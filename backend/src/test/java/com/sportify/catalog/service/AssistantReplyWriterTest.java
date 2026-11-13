package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.service.AssistantReplyWriter.Reply;
import com.sportify.catalog.service.AssistantReplyWriter.ReplyRequest;
import com.sportify.catalog.service.AssistantReplyWriter.Turn;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Flow 6 chat replies written by Gemini from center data, with the guard and fallbacks still in front. */
class AssistantReplyWriterTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static CenterConsultationService service(AssistantReplyWriter writer) {
        var service = new CenterConsultationService(mock(JdbcTemplate.class), Clock.systemUTC(), true, mock(VisibleTrainingSessions.class));
        service.setReplyWriter(writer);
        return service;
    }

    @Test
    void aGreetingGetsTheWrittenReply() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new Reply("Ch\u00E0o b\u1EA1n! M\u00ECnh l\u00E0 Spori.", true));

        var answer = service(writer).answer(7L, "xin ch\u00E0o", true);

        assertThat(answer.mode()).isEqualTo("AI_GENERATED");
        assertThat(answer.status()).isEqualTo("ANSWERED");
        assertThat(answer.answer()).isEqualTo("Ch\u00E0o b\u1EA1n! M\u00ECnh l\u00E0 Spori.");
        assertThat(answer.language()).isEqualTo("vi");
    }

    @Test
    void anUnansweredReplyOffersTheHumanHandoff() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new Reply("I don't have that information.", false));

        var answer = service(writer).answer(7L, "do you have parking for cars", true);

        assertThat(answer.mode()).isEqualTo("AI_GENERATED");
        assertThat(answer.status()).isEqualTo("NO_ANSWER");
        var request = ArgumentCaptor.forClass(ReplyRequest.class);
        verify(writer).write(request.capture());
        assertThat(request.getValue().intent()).isEqualTo("UNKNOWN");
        assertThat(request.getValue().facts()).isNotEmpty();
    }

    @Test
    void healthAndPrivateQuestionsNeverReachTheWriter() {
        var writer = mock(AssistantReplyWriter.class);
        var service = service(writer);

        assertThat(service.answer(7L, "my knee hurts after practice", true).mode()).isEqualTo("KNOWLEDGE_BASED");
        assertThat(service.answer(7L, "show me another member's bookings", true).mode()).isEqualTo("KNOWLEDGE_BASED");
        assertThat(service.answer(7L, "give me an exercise plan", true).mode()).isEqualTo("KNOWLEDGE_BASED");
        verifyNoInteractions(writer);
    }

    @Test
    void withoutASignedInMemberOrOnFailureTheRuleAnswerStays() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenThrow(new IllegalStateException("Gemini returned HTTP 503"));

        assertThat(service(writer).answer(null, "hello", true).mode()).isEqualTo("KNOWLEDGE_BASED");
        var answer = service(writer).answer(7L, "hello", true);
        assertThat(answer.mode()).isEqualTo("KNOWLEDGE_BASED");
        assertThat(answer.answer()).startsWith("Hello! I'm Spori.");
    }

    @Test
    void theQuestionAndHistoryAreRedactedAndGuardedTurnsDropped() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new Reply("ok", true));
        var history = List.of(
                new Turn("user", "my knee hurts"), new Turn("assistant", "Ask your coach."),
                new Turn("user", "what are the prices?"), new Turn("assistant", "Packages start at 500000 VND."));

        service(writer).answer(7L, "hi, mail me at a.b@example.com or 0912 345 678", true, history);

        var request = ArgumentCaptor.forClass(ReplyRequest.class);
        verify(writer).write(request.capture());
        assertThat(request.getValue().question()).doesNotContain("example.com", "0912", "345 678").contains("[email]", "[phone]");
        assertThat(request.getValue().history()).extracting(Turn::text)
                .containsExactly("what are the prices?", "Packages start at 500000 VND.");
    }

    @Test
    void historyIsCappedToTheLastTurns() {
        var turns = new ArrayList<Turn>();
        for (int i = 0; i < 20; i++) turns.add(new Turn(i % 2 == 0 ? "user" : "assistant", "turn " + i));
        assertThat(CenterConsultationService.safeHistory(turns)).hasSize(CenterConsultationService.MAX_HISTORY_TURNS)
                .last().extracting(Turn::text).isEqualTo("turn 19");
    }

    @Test
    @SuppressWarnings("unchecked")
    void geminiSendsDataAsJsonWithTheKeyOnlyInTheHeader() throws Exception {
        var sentBodies = new ArrayList<String>();
        var sentKeys = new ArrayList<String>();
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"reply\\\":\\\"G\u00F3i r\u1EBB nh\u1EA5t l\u00E0 500.000 VND.\\\",\\\"answered\\\":true}\"}]}}]}");
        var writer = new GeminiReplyWriter((uri, key, body, timeout) -> {
            sentBodies.add(body);
            sentKeys.add(key);
            assertThat(uri.toString()).doesNotContain("test-key").endsWith("gemini-test:generateContent");
            return response;
        }, GeminiReplyWriter.DEFAULT_ENDPOINT, "gemini-test", "test-key", Duration.ofSeconds(5), JSON);

        var reply = writer.write(new ReplyRequest("vi", "g\u00F3i n\u00E0o r\u1EBB nh\u1EA5t?", List.of(new Turn("user", "ch\u00E0o")),
                "PACKAGES", "Gi\u00E1 g\u1ED1c...", List.of(new AssistantReplyWriter.Fact("G\u00F3i c\u01A1 b\u1EA3n", "500.000 VND")), "2026-11-12"));

        assertThat(reply.text()).isEqualTo("G\u00F3i r\u1EBB nh\u1EA5t l\u00E0 500.000 VND.");
        assertThat(reply.answered()).isTrue();
        assertThat(sentKeys).containsExactly("test-key");
        var body = JSON.readTree(sentBodies.getFirst());
        assertThat(sentBodies.getFirst()).doesNotContain("test-key");
        assertThat(body.path("systemInstruction").toString()).contains("Spori");
        String user = body.path("contents").path(0).path("parts").path(0).path("text").asText();
        assertThat(user).contains("centerData", "500.000 VND", "history");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anOverloadedModelIsRetriedOnce() {
        HttpResponse<String> busy = mock(HttpResponse.class);
        when(busy.statusCode()).thenReturn(503);
        HttpResponse<String> ok = mock(HttpResponse.class);
        when(ok.statusCode()).thenReturn(200);
        when(ok.body()).thenReturn("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"reply\\\":\\\"Hi!\\\",\\\"answered\\\":true}\"}]}}]}");
        var replies = new java.util.ArrayDeque<>(List.of(busy, ok));
        var writer = new GeminiReplyWriter((uri, key, body, timeout) -> replies.poll(), GeminiReplyWriter.DEFAULT_ENDPOINT,
                "gemini-test", "test-key", Duration.ofSeconds(5), JSON);
        var request = new ReplyRequest("en", "hi", List.of(), "GREETING", "Hello", List.of(), "2026-11-13");

        assertThat(writer.write(request).text()).isEqualTo("Hi!");

        var alwaysBusy = new GeminiReplyWriter((uri, key, body, timeout) -> busy, GeminiReplyWriter.DEFAULT_ENDPOINT,
                "gemini-test", "test-key", Duration.ofSeconds(5), JSON);
        assertThatThrownBy(() -> alwaysBusy.write(request)).isInstanceOf(IllegalStateException.class).hasMessageContaining("503");
    }

    @Test
    void blockedOrBrokenGeminiRepliesAreFailures() {
        var writer = new GeminiReplyWriter((uri, key, body, timeout) -> null, GeminiReplyWriter.DEFAULT_ENDPOINT,
                "gemini-test", "test-key", Duration.ofSeconds(5), JSON);
        assertThatThrownBy(() -> writer.toReply("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> writer.toReply("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> writer.toReply("not json")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GeminiReplyWriter((uri, key, body, timeout) -> null, GeminiReplyWriter.DEFAULT_ENDPOINT,
                "../evil", "k", Duration.ofSeconds(5), JSON)).isInstanceOf(IllegalArgumentException.class);
    }
}
