package com.sportify.catalog.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Drafts Coach drill suggestions with Claude from a de-identified training context (Flow 5).
 * Active only when app.training.ai-drills.enabled=true; the API key comes from ANTHROPIC_API_KEY.
 * The response is constrained to a JSON schema and every drill is validated before it reaches the Coach.
 */
@Service
@ConditionalOnProperty(name = "app.training.ai-drills.enabled", havingValue = "true")
class ClaudeDrillGenerator implements DrillGenerator {
    static final int MAX_DRILLS = 4;
    static final int MIN_MINUTES = 3;
    static final int MAX_MINUTES = 30;
    static final int MAX_TITLE = 150;
    static final int MAX_GOAL = 150;
    static final int MAX_INSTRUCTIONS = 1000;
    static final int MAX_REASON = 500;

    static final String SYSTEM_PROMPT = """
            You draft practice drills for a coach at a multi-sport center. The coach reviews, edits and \
            assigns every draft; nothing you write reaches the member without that review.

            Use the training context in the user message: the sport, the member's level, the member's \
            training goal, how many sessions the member attended, recent skill scores and the coach's \
            latest feedback. Choose drills that address the weakest recent scores and the areas for \
            improvement, at a difficulty that matches the member's level, and that serve the stated goal. \
            When there are no recent results, rely on the level and the goal.

            Return 2 to 4 drills for the given sport only. Each drill lasts 3 to 30 minutes. Write the \
            instructions as short, safe, step-by-step guidance that a coach can supervise in a normal \
            session. In "reason", say which goal, level, score or feedback the drill responds to.

            Do not give medical, injury, rehabilitation, diet or weight-loss advice. Do not invent scores \
            or facts that are not in the context. Write in English.""";

    /** One drill as Claude must return it. */
    record ProposedDrill(
            @JsonPropertyDescription("Short drill title, at most 150 characters") String title,
            @JsonPropertyDescription("The skill this drill trains, for example passing accuracy") String goal,
            @JsonPropertyDescription("Duration in minutes, between 3 and 30") int durationMinutes,
            @JsonPropertyDescription("Step-by-step instructions a coach can supervise, at most 1000 characters") String instructions,
            @JsonPropertyDescription("Which goal, level, recent score or feedback this drill addresses, at most 500 characters") String reason) {}

    /** The full structured response. */
    record ProposedDrills(List<ProposedDrill> drills) {}

    private final Supplier<AnthropicClient> clientFactory;
    private volatile AnthropicClient client;
    private final String model;
    private final ObjectMapper json;

    @Autowired
    ClaudeDrillGenerator(@Value("${app.training.ai-drills.model:claude-opus-5-5}") String model,
                         @Value("${app.training.ai-drills.timeout-seconds:30}") long timeoutSeconds,
                         @Value("${ANTHROPIC_API_KEY:}") String apiKey,
                         ObjectMapper json) {
        this(() -> newClient(apiKey, timeoutSeconds), model, json);
    }

    ClaudeDrillGenerator(AnthropicClient client, String model, ObjectMapper json) {
        this(() -> client, model, json);
    }

    private ClaudeDrillGenerator(Supplier<AnthropicClient> clientFactory, String model, ObjectMapper json) {
        this.clientFactory = clientFactory;
        this.model = model;
        this.json = json;
    }

    /**
     * backend/.env is imported as Spring properties, not process environment variables, so the key is resolved
     * through Spring first; the SDK's own environment lookup is the fallback.
     */
    private static AnthropicClient newClient(String apiKey, long timeoutSeconds) {
        var builder = AnthropicOkHttpClient.builder().fromEnv();
        if (apiKey != null && !apiKey.isBlank()) builder.apiKey(apiKey.trim());
        return builder.timeout(Duration.ofSeconds(timeoutSeconds)).maxRetries(1).build();
    }

    /**
     * Created on first use, so a missing or invalid key never stops the application from starting: the failing call
     * is caught by the caller, which falls back to the rule catalog.
     */
    private AnthropicClient client() {
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) client = clientFactory.get();
                current = client;
            }
        }
        return current;
    }

    @Override
    public String version() {
        return model;
    }

    @Override
    public List<DrillDraft> generate(Input input) {
        StructuredMessageCreateParams<ProposedDrills> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(ProposedDrills.class)
                .addUserMessage(context(input))
                .build();
        StructuredMessage<ProposedDrills> message = client().messages().create(params);
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            return List.of();
        }
        var proposed = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .filter(Objects::nonNull)
                .findFirst();
        return toDrafts(proposed.orElse(null), DrillGenerator.effectiveLevel(input.memberLevel(), input.classLevel()));
    }

    /** Validates Claude's proposal; anything outside the limits Coach plans accept is dropped. */
    static List<DrillDraft> toDrafts(ProposedDrills proposed, String level) {
        if (proposed == null || proposed.drills() == null) return List.of();
        return proposed.drills().stream()
                .filter(Objects::nonNull)
                .filter(drill -> drill.durationMinutes() >= MIN_MINUTES && drill.durationMinutes() <= MAX_MINUTES)
                .map(drill -> new DrillDraft(
                        clean(drill.title(), MAX_TITLE),
                        truncate(drill.goal(), MAX_GOAL),
                        drill.durationMinutes(),
                        clean(drill.instructions(), MAX_INSTRUCTIONS),
                        truncate(drill.reason(), MAX_REASON),
                        level))
                .filter(draft -> draft.title() != null && draft.instructions() != null)
                .limit(MAX_DRILLS)
                .toList();
    }

    private String context(Input input) {
        try {
            return "Training context (JSON):\n" + json.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise the drill context", e);
        }
    }

    /** Trims, removes control characters and rejects blank or over-long text (fields a Coach plan stores as-is). */
    static String clean(String value, int max) {
        String text = normalise(value);
        return text == null || text.length() > max ? null : text;
    }

    /** Like {@link #clean} but shortens over-long descriptive text instead of rejecting the drill. */
    static String truncate(String value, int max) {
        String text = normalise(value);
        return text == null ? null : text.length() <= max ? text : text.substring(0, max).trim();
    }

    private static String normalise(String value) {
        if (value == null) return null;
        String text = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
        return text.isEmpty() ? null : text;
    }
}
