package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** No network: the HTTP transport is replaced by a recorder. */
class GeminiDrillGeneratorTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final DrillGenerator.Input INPUT = new DrillGenerator.Input(DrillGenerator.SCOPE_INDIVIDUAL, "Badminton", "BEGINNER", "ADVANCED",
            "COMPETITION", 6, List.of(new DrillGenerator.RecentScore("Footwork", new BigDecimal("4.50"), 10, LocalDate.of(2027, 3, 9))),
            "Strong serve", "Footwork recovery", "Net kills", 45, null);

    private record Sent(URI uri, String key, String body) {}

    @SuppressWarnings("unchecked")
    private static GeminiDrillGenerator generator(List<Sent> sent, int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return new GeminiDrillGenerator((uri, key, request, timeout) -> { sent.add(new Sent(uri, key, request)); return response; },
                "https://example.test/v1beta/models/", "gemini-flash-latest", "test-key", Duration.ofSeconds(5), JSON);
    }

    private static String reply(String finishReason, String drillsJson) throws Exception {
        return JSON.writeValueAsString(java.util.Map.of("candidates", List.of(java.util.Map.of(
                "finishReason", finishReason,
                "content", java.util.Map.of("parts", List.of(java.util.Map.of("text", drillsJson))))))) ;
    }

    @Test
    void sendsTheSharedPromptSchemaAndKeyHeaderAndValidatesTheReply() throws Exception {
        var sent = new ArrayList<Sent>();
        String drills = "{\"drills\":[{\"title\":\"Six-corner shadow footwork\",\"goal\":\"Footwork\",\"durationMinutes\":20,\"instructions\":\"Shadow the six corners.\",\"reason\":\"Footwork scored 4.5/10.\"},"
                + "{\"title\":\"Net kill feed\",\"goal\":\"Net play\",\"durationMinutes\":20,\"instructions\":\"Feed and kill at the net.\",\"reason\":\"Coach focus: net kills.\"},"
                + "{\"title\":\"Too long\",\"goal\":\"x\",\"durationMinutes\":31,\"instructions\":\"x\",\"reason\":\"x\"},"
                + "{\"title\":\"Over budget\",\"goal\":\"x\",\"durationMinutes\":10,\"instructions\":\"x\",\"reason\":\"x\"}]}";
        var drafts = generator(sent, 200, reply("STOP", drills)).generate(INPUT);

        assertThat(drafts).extracting(d -> d.title()).containsExactly("Six-corner shadow footwork", "Net kill feed");
        assertThat(drafts).allSatisfy(d -> assertThat(d.level()).isEqualTo("ADVANCED"));
        Sent request = sent.getFirst();
        assertThat(request.uri()).hasToString("https://example.test/v1beta/models/gemini-flash-latest:generateContent");
        assertThat(request.key()).isEqualTo("test-key");
        JsonNode body = JSON.readTree(request.body());
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText()).isEqualTo(AiDrillPolicy.SYSTEM_PROMPT);
        assertThat(body.path("generationConfig").path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(body.path("generationConfig").path("responseSchema").path("properties").has("drills")).isTrue();
        String context = body.path("contents").path(0).path("parts").path(0).path("text").asText();
        assertThat(context).contains("COMPETITION", "Footwork recovery", "Net kills", "INDIVIDUAL");
        assertThat(request.body()).doesNotContain("test-key");
    }

    @Test
    void blockedTruncatedOrUnparsableRepliesGiveNoDrafts() throws Exception {
        assertThat(generator(new ArrayList<>(), 200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}").generate(INPUT)).isEmpty();
        assertThat(generator(new ArrayList<>(), 200, reply("MAX_TOKENS", "{\"drills\":[")).generate(INPUT)).isEmpty();
        assertThat(generator(new ArrayList<>(), 200, reply("SAFETY", "{}")).generate(INPUT)).isEmpty();
        assertThat(generator(new ArrayList<>(), 200, reply("STOP", "not json")).generate(INPUT)).isEmpty();
        assertThat(generator(new ArrayList<>(), 200, "{}").generate(INPUT)).isEmpty();
    }

    @Test
    void thoughtPartsAreIgnored() throws Exception {
        String body = JSON.writeValueAsString(java.util.Map.of("candidates", List.of(java.util.Map.of("finishReason", "STOP",
                "content", java.util.Map.of("parts", List.of(
                        java.util.Map.of("text", "thinking...", "thought", true),
                        java.util.Map.of("text", "{\"drills\":[{\"title\":\"Shadow\",\"goal\":\"g\",\"durationMinutes\":5,\"instructions\":\"i\",\"reason\":\"r\"}]}")))))));
        assertThat(generator(new ArrayList<>(), 200, body).generate(INPUT)).extracting(d -> d.title()).containsExactly("Shadow");
    }

    @Test
    void httpErrorsMissingKeysAndUnsafeModelNamesFailWithoutLeakingDetails() {
        assertThatThrownBy(() -> generator(new ArrayList<>(), 429, "{\"error\":{\"message\":\"quota for key test-key\"}}").generate(INPUT))
                .isInstanceOf(IllegalStateException.class).hasMessage("Gemini returned HTTP 429");
        var noKey = new GeminiDrillGenerator((uri, key, body, timeout) -> { throw new AssertionError("must not call"); },
                "https://example.test/", "gemini-flash-latest", " ", Duration.ofSeconds(5), JSON);
        assertThatThrownBy(() -> noKey.generate(INPUT)).isInstanceOf(IllegalStateException.class).hasMessage("GEMINI_API_KEY is not configured");
        assertThatThrownBy(() -> new GeminiDrillGenerator((uri, key, body, timeout) -> null, "https://example.test/", "../models/x?alt=1", "k", Duration.ofSeconds(5), JSON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new GeminiDrillGenerator((uri, key, body, timeout) -> null, "https://example.test/", "gemini-flash-latest", "k", Duration.ofSeconds(5), JSON).version())
                .isEqualTo("gemini-flash-latest");
    }

    @Test
    void classScopeDraftsUseTheClassLevel() throws Exception {
        var group = new DrillGenerator.GroupSummary(6, java.util.Map.of("BEGINNER", 4, "ADVANCED", 2), java.util.Map.of("BUILD_SKILLS", 6), List.of());
        var classInput = new DrillGenerator.Input(DrillGenerator.SCOPE_CLASS, "Badminton", "BEGINNER", null, null, 0, List.of(), null, null, null, null, group);
        var drafts = generator(new ArrayList<>(), 200, reply("STOP", "{\"drills\":[{\"title\":\"Relay\",\"goal\":\"g\",\"durationMinutes\":10,\"instructions\":\"i\",\"reason\":\"r\"}]}")).generate(classInput);
        assertThat(drafts).singleElement().satisfies(d -> assertThat(d.level()).isEqualTo("BEGINNER"));
    }
}
