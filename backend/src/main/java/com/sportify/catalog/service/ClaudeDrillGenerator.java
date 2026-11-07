package com.sportify.catalog.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import com.sportify.catalog.service.AiDrillPolicy.ProposedDrills;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Drafts Coach drill suggestions with Claude from a de-identified training context (Flow 5).
 * Active when app.training.ai-drills.enabled=true and app.training.ai-drills.provider is claude (the default);
 * the API key comes from ANTHROPIC_API_KEY. Prompt, output shape and validation live in {@link AiDrillPolicy}.
 */
@Service
@ConditionalOnExpression("${app.training.ai-drills.enabled:false} and '${app.training.ai-drills.provider:claude}'.equalsIgnoreCase('claude')")
class ClaudeDrillGenerator implements DrillGenerator {
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
                .system(AiDrillPolicy.SYSTEM_PROMPT)
                .outputConfig(ProposedDrills.class)
                .addUserMessage(AiDrillPolicy.context(json, input))
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
        return AiDrillPolicy.toDrafts(proposed.orElse(null), AiDrillPolicy.level(input), input.sessionMinutes(), input.sport());
    }
}
