package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Manual, synthetic-only LIVE smoke check of the approved Gemini provider. Never runs in CI: it is skipped unless
 * GEMINI_LIVE_SMOKE=true and GEMINI_API_KEY are set in the local environment. It sends one made-up training context
 * (no real Member data) and prints only non-sensitive evidence: model, HTTP status, timings, the number of accepted
 * drafts and the fallback outcome. It never prints the key, the request or the model's text.
 */
@EnabledIfEnvironmentVariable(named = "GEMINI_LIVE_SMOKE", matches = "true")
class GeminiDrillLiveSmokeTest {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final String MODEL = System.getenv().getOrDefault("AI_DRILLS_GEMINI_MODEL", "gemini-3.5-flash-lite");
    private static final String KEY = System.getenv().getOrDefault("GEMINI_API_KEY", "");

    /** Entirely synthetic: an intermediate badminton member with two made-up scores. */
    private static final DrillGenerator.Input SYNTHETIC = new DrillGenerator.Input(DrillGenerator.SCOPE_INDIVIDUAL,
            "Badminton", "BEGINNER", "INTERMEDIATE", "BUILD_SKILLS", 3,
            List.of(new DrillGenerator.RecentScore("Footwork", new BigDecimal("4.00"), 10, LocalDate.of(2030, 1, 1)),
                    new DrillGenerator.RecentScore("Serving", new BigDecimal("6.00"), 10, LocalDate.of(2030, 1, 1))),
            null, "footwork", "serving", 45, null);

    @Test
    void modelIsAvailableForThisKey() throws Exception {
        assertThat(KEY).as("GEMINI_API_KEY must be set locally").isNotBlank();
        long start = System.nanoTime();
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                        URI.create(GeminiDrillGenerator.DEFAULT_ENDPOINT + MODEL)).header("x-goog-api-key", KEY)
                        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        String resolved = response.statusCode() == 200 ? JSON.readTree(response.body()).path("name").asText() : "-";
        System.out.printf("LIVE-SMOKE model-check model=%s status=%d resolved=%s ms=%d%n", MODEL, response.statusCode(), resolved, ms);
        assertThat(response.statusCode()).as("model " + MODEL + " must be available to this key").isEqualTo(200);
    }

    @Test
    void structuredReplyPassesTheReleaseGate() {
        var generator = new GeminiDrillGenerator(MODEL, 30, KEY, JSON);
        long start = System.nanoTime();
        var drafts = generator.generate(SYNTHETIC);
        long ms = (System.nanoTime() - start) / 1_000_000;
        int minutes = drafts.stream().mapToInt(d -> d.durationMinutes()).sum();
        System.out.printf("LIVE-SMOKE generate model=%s outcome=%s drafts=%d totalMinutes=%d levels=%s ms=%d%n", generator.version(),
                drafts.isEmpty() ? "EMPTY->RULE_BASED" : "AI_GENERATED", drafts.size(), minutes,
                drafts.stream().map(d -> d.level()).distinct().toList(), ms);
        assertThat(drafts).hasSizeBetween(2, AiDrillPolicy.MAX_DRILLS);
        assertThat(minutes).isLessThanOrEqualTo(45);
        assertThat(drafts).allSatisfy(d -> assertThat(d.level()).isEqualTo("INTERMEDIATE"));
    }

    @Test
    void timeoutAndUnknownModelFailSoTheServiceUsesTheRuleCatalog() {
        var tooShort = new GeminiDrillGenerator(GeminiDrillGenerator.httpTransport(1), GeminiDrillGenerator.DEFAULT_ENDPOINT,
                MODEL, KEY, Duration.ofMillis(1), JSON);
        long start = System.nanoTime();
        assertThatThrownBy(() -> tooShort.generate(SYNTHETIC)).isInstanceOf(RuntimeException.class)
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContain(KEY));
        System.out.printf("LIVE-SMOKE timeout outcome=EXCEPTION->RULE_BASED ms=%d%n", (System.nanoTime() - start) / 1_000_000);

        var unknown = new GeminiDrillGenerator("gemini-no-such-model-sportify", 20, KEY, JSON);
        assertThatThrownBy(() -> unknown.generate(SYNTHETIC)).isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Gemini returned HTTP 4");
        System.out.println("LIVE-SMOKE unknown-model outcome=HTTP_4xx->RULE_BASED");
    }
}
