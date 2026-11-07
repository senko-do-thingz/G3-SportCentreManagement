package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import com.sportify.catalog.service.AiDrillPolicy.ProposedDrills;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Drafts Coach drill suggestions with the Google Gemini API (Flow 5), the CODEOWNER-approved and only AI provider.
 * Active only when app.training.ai-drills.enabled=true and app.training.ai-drills.provider=gemini; any other provider
 * value activates no generator, so the rule catalog is used. The key comes from GEMINI_API_KEY and is sent in the
 * x-goog-api-key header, never in the URL. Prompt, output shape and validation live in {@link AiDrillPolicy}; the
 * response is constrained with a JSON response schema. Every failure falls back to the rule catalog.
 */
@Service
@ConditionalOnExpression("${app.training.ai-drills.enabled:false} and '${app.training.ai-drills.provider:gemini}'.equalsIgnoreCase('gemini')")
class GeminiDrillGenerator implements DrillGenerator {
    static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Pattern MODEL = Pattern.compile("[a-z0-9][a-z0-9.\\-]{0,63}");

    /** Gemini response schema (OpenAPI subset) matching {@link AiDrillPolicy.ProposedDrills}. */
    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "required", List.of("drills"),
            "properties", Map.of("drills", Map.of(
                    "type", "ARRAY",
                    "items", Map.of(
                            "type", "OBJECT",
                            "required", List.of("title", "goal", "durationMinutes", "instructions", "reason"),
                            "properties", Map.of(
                                    "title", Map.of("type", "STRING", "description", "Short drill title, at most 150 characters"),
                                    "goal", Map.of("type", "STRING", "description", "The skill this drill trains"),
                                    "durationMinutes", Map.of("type", "INTEGER", "description", "Duration in minutes, between 3 and 30"),
                                    "instructions", Map.of("type", "STRING", "description", "Step-by-step instructions a coach can supervise, at most 1000 characters"),
                                    "reason", Map.of("type", "STRING", "description", "Which goal, level, focus, score or feedback this drill addresses"))))));

    /** Sends one request and returns the HTTP status and body; replaced in tests. */
    interface Transport {
        HttpResponse<String> post(URI uri, String apiKey, String body, Duration timeout) throws IOException, InterruptedException;
    }

    private final Transport transport;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final Duration timeout;
    private final ObjectMapper json;

    @Autowired
    GeminiDrillGenerator(@Value("${app.training.ai-drills.gemini-model:gemini-3.5-flash-lite}") String model,
                         @Value("${app.training.ai-drills.timeout-seconds:30}") long timeoutSeconds,
                         @Value("${GEMINI_API_KEY:}") String apiKey,
                         ObjectMapper json) {
        this(httpTransport(timeoutSeconds), DEFAULT_ENDPOINT, model, apiKey, Duration.ofSeconds(timeoutSeconds), json);
    }

    GeminiDrillGenerator(Transport transport, String endpoint, String model, String apiKey, Duration timeout, ObjectMapper json) {
        String name = model == null ? "" : model.trim();
        // The model is part of the request path, so only plain model names are accepted.
        if (!MODEL.matcher(name).matches()) throw new IllegalArgumentException("Invalid Gemini model name");
        this.transport = transport;
        this.endpoint = endpoint;
        this.model = name;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.timeout = timeout;
        this.json = json;
    }

    static Transport httpTransport(long timeoutSeconds) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.min(10, timeoutSeconds))).build();
        return (uri, key, body, timeout) -> client.send(HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Override
    public String version() {
        return model;
    }

    @Override
    public List<DrillDraft> generate(Input input) {
        if (apiKey.isEmpty()) throw new IllegalStateException("GEMINI_API_KEY is not configured");
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", AiDrillPolicy.SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", AiDrillPolicy.context(json, input))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA,
                        "maxOutputTokens", 8192));
        HttpResponse<String> response;
        try {
            response = transport.post(URI.create(endpoint + model + ":generateContent"), apiKey, json.writeValueAsString(request), timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request interrupted", interrupted);
        } catch (IOException failure) {
            throw new IllegalStateException("Gemini request failed", failure);
        }
        // Only the status reaches the exception: provider error bodies can echo request content.
        if (response.statusCode() != 200) throw new IllegalStateException("Gemini returned HTTP " + response.statusCode());
        return toDrafts(response.body(), input);
    }

    /** Reads the first candidate; blocked, truncated or unparsable replies give no drafts (the caller then uses rules). */
    List<DrillDraft> toDrafts(String body, Input input) {
        try {
            JsonNode root = json.readTree(body);
            if (root.path("promptFeedback").hasNonNull("blockReason")) return List.of();
            JsonNode candidate = root.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) return List.of();
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
            }
            if (text.isEmpty()) return List.of();
            ProposedDrills proposed = json.readValue(text.toString(), ProposedDrills.class);
            return AiDrillPolicy.toDrafts(proposed, AiDrillPolicy.level(input), input.sessionMinutes(), input.sport());
        } catch (IOException unparsable) {
            return List.of();
        }
    }
}
