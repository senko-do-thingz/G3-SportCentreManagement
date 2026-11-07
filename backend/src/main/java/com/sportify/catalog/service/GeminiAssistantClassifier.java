package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.service.AssistantAiPolicy.ProposedIntent;
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
 * Classifies Flow 6 assistant questions with the Google Gemini API. Active when app.assistant.ai.enabled=true and
 * app.assistant.ai.provider=gemini; the key comes from GEMINI_API_KEY and is sent in the x-goog-api-key header.
 */
@Service
@ConditionalOnExpression("${app.assistant.ai.enabled:false} and '${app.assistant.ai.provider:claude}'.equalsIgnoreCase('gemini')")
class GeminiAssistantClassifier implements AssistantIntentClassifier {
    static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Pattern MODEL = Pattern.compile("[a-z0-9][a-z0-9.\\-]{0,63}");

    /** Gemini response schema matching {@link ProposedIntent}; enums keep the model inside the allowed values. */
    static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
            "type", "OBJECT",
            "required", List.of("intent", "sport", "day", "faqTopic"),
            "properties", Map.of(
                    "intent", Map.of("type", "STRING", "enum", AssistantAiPolicy.INTENTS),
                    "sport", Map.of("type", "STRING", "description", "Exactly one sport name from the provided list, or an empty string"),
                    "day", Map.of("type", "STRING", "enum", AssistantAiPolicy.DAYS),
                    "faqTopic", Map.of("type", "INTEGER", "description", "FAQ topic 1 to 7 when intent is FAQ, otherwise 0")));

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
    GeminiAssistantClassifier(@Value("${app.assistant.ai.gemini-model:gemini-flash-lite-latest}") String model,
                              @Value("${app.assistant.ai.timeout-seconds:15}") long timeoutSeconds,
                              @Value("${GEMINI_API_KEY:}") String apiKey,
                              ObjectMapper json) {
        this(httpTransport(timeoutSeconds), DEFAULT_ENDPOINT, model, apiKey, Duration.ofSeconds(timeoutSeconds), json);
    }

    GeminiAssistantClassifier(Transport transport, String endpoint, String model, String apiKey, Duration timeout, ObjectMapper json) {
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

    private static Transport httpTransport(long timeoutSeconds) {
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
    public Classification classify(List<String> keywords, List<String> sports) {
        if (apiKey.isEmpty()) throw new IllegalStateException("GEMINI_API_KEY is not configured");
        Map<String, Object> request = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", AssistantAiPolicy.SYSTEM_PROMPT))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", AssistantAiPolicy.userMessage(json, keywords, sports))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", RESPONSE_SCHEMA,
                        "temperature", 0,
                        "maxOutputTokens", 512));
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
        return toClassification(response.body(), sports);
    }

    /** Reads the first candidate; blocked, truncated or unparsable replies give UNKNOWN (the caller then uses rules). */
    Classification toClassification(String body, List<String> sports) {
        try {
            JsonNode root = json.readTree(body);
            if (root.path("promptFeedback").hasNonNull("blockReason")) return AssistantAiPolicy.unknown();
            JsonNode candidate = root.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText())) return AssistantAiPolicy.unknown();
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (!part.path("thought").asBoolean(false)) text.append(part.path("text").asText(""));
            }
            if (text.isEmpty()) return AssistantAiPolicy.unknown();
            return AssistantAiPolicy.validate(json.readValue(text.toString(), ProposedIntent.class), sports);
        } catch (IOException unparsable) {
            return AssistantAiPolicy.unknown();
        }
    }
}
