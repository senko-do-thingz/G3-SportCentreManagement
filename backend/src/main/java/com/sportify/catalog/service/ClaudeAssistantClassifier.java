package com.sportify.catalog.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.service.AssistantAiPolicy.ProposedIntent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Classifies Flow 6 assistant questions with Claude. Active when app.assistant.ai.enabled=true and
 * app.assistant.ai.provider is claude; the key comes from ANTHROPIC_API_KEY. Only the allowlisted keywords found in the
 * question and the active sport names are sent; the question text is never sent.
 */
@Service
@ConditionalOnExpression("${app.assistant.ai.enabled:false} and '${app.assistant.ai.provider:gemini}'.equalsIgnoreCase('claude')")
class ClaudeAssistantClassifier implements AssistantIntentClassifier {
    private final Supplier<AnthropicClient> clientFactory;
    private volatile AnthropicClient client;
    private final String model;
    private final ObjectMapper json;

    @Autowired
    ClaudeAssistantClassifier(@Value("${app.assistant.ai.model:claude-opus-5-5}") String model,
                              @Value("${app.assistant.ai.timeout-seconds:15}") long timeoutSeconds,
                              @Value("${ANTHROPIC_API_KEY:}") String apiKey,
                              ObjectMapper json) {
        this(() -> newClient(apiKey, timeoutSeconds), model, json);
    }

    ClaudeAssistantClassifier(AnthropicClient client, String model, ObjectMapper json) {
        this(() -> client, model, json);
    }

    private ClaudeAssistantClassifier(Supplier<AnthropicClient> clientFactory, String model, ObjectMapper json) {
        this.clientFactory = clientFactory;
        this.model = model;
        this.json = json;
    }

    private static AnthropicClient newClient(String apiKey, long timeoutSeconds) {
        var builder = AnthropicOkHttpClient.builder().fromEnv();
        if (apiKey != null && !apiKey.isBlank()) builder.apiKey(apiKey.trim());
        return builder.timeout(Duration.ofSeconds(timeoutSeconds)).maxRetries(1).build();
    }

    /** Created on first use so a missing key never stops the application; the failing call falls back to the rules. */
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
    public Classification classify(List<String> keywords, List<String> sports) {
        StructuredMessageCreateParams<ProposedIntent> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(1024L)
                .system(AssistantAiPolicy.SYSTEM_PROMPT)
                .outputConfig(ProposedIntent.class)
                .addUserMessage(AssistantAiPolicy.userMessage(json, keywords, sports))
                .build();
        StructuredMessage<ProposedIntent> message = client().messages().create(params);
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) return AssistantAiPolicy.unknown();
        var proposed = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .filter(Objects::nonNull)
                .findFirst();
        return AssistantAiPolicy.validate(proposed.orElse(null), sports);
    }
}
