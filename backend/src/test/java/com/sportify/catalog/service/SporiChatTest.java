package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.service.AssistantReplyWriter.Reply;
import com.sportify.catalog.service.AssistantReplyWriter.ReplyRequest;
import com.sportify.core.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Spori for all four roles: role-scoped data, server-side human handoff, and polite off-topic replies. */
class SporiChatTest {
    private final List<String> sql = new ArrayList<>();

    private CenterConsultationService service(AssistantReplyWriter writer) {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenAnswer(call -> {
            sql.add(call.getArgument(0));
            return 3;
        });
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            sql.add(call.getArgument(0));
            return List.of();
        });
        var service = new CenterConsultationService(jdbc, Clock.systemUTC(), true, mock(VisibleTrainingSessions.class));
        if (writer != null) service.setReplyWriter(writer);
        return service;
    }

    @Test
    void paymentVietQrRefundAndAccountTopicsGoToReception() {
        for (String question : List.of("My VietQR payment failed", "I was charged twice", "how do I get a refund",
                "I cannot log in to my account", "chuy\u1EC3n kho\u1EA3n b\u1ECB l\u1ED7i", "thanh toan khong duoc")) {
            String raw = question.toLowerCase(Locale.ROOT);
            assertThat(CenterConsultationService.handoff(raw, CenterConsultationService.plain(raw))).as(question).isEqualTo("RECEPTIONIST");
        }
    }

    @Test
    void attendanceAndTrainingTopicsGoToTheCoach() {
        for (String question : List.of("my attendance is missing", "I was marked absent", "how is my performance",
                "\u0111i\u1EC3m danh c\u1EE7a t\u00F4i b\u1ECB sai", "bai tap hom nay")) {
            String raw = question.toLowerCase(Locale.ROOT);
            assertThat(CenterConsultationService.handoff(raw, CenterConsultationService.plain(raw))).as(question).isEqualTo("COACH");
        }
        assertThat(CenterConsultationService.handoff("what classes are on tomorrow", "what classes are on tomorrow")).isNull();
    }

    @Test
    void aGuardedRefundRequestExplainsReceptionWithoutCallingTheAi() {
        var writer = mock(AssistantReplyWriter.class);

        var answer = service(writer).chat(7L, "MEMBER", "refund me now", List.of());

        assertThat(answer.handoff()).isEqualTo("RECEPTIONIST");
        assertThat(answer.answer()).contains("Receptionist").contains("cannot confirm payments");
        verifyNoInteractions(writer);
    }

    @Test
    void aBookAndRefundForMeRequestIsStillRefusedButNamesReception() {
        var jdbc = mock(JdbcTemplate.class);
        var answer = new CenterConsultationService(jdbc, Clock.systemUTC(), true, mock(VisibleTrainingSessions.class))
                .answer("Book for me the next class and refund me later", true);

        assertThat(answer.status()).isEqualTo("NO_ANSWER");
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.handoff()).isEqualTo("RECEPTIONIST");
        verifyNoInteractions(jdbc);
    }

    @Test
    void theWriterGetsTheRoleAndTheServerHandoffAndTheServerDecisionWins() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new Reply("Reception handles VietQR problems.", false, "NOT_IN_DATA"));

        var answer = service(writer).chat(7L, "MEMBER", "my VietQR payment is stuck", List.of());

        var request = ArgumentCaptor.forClass(ReplyRequest.class);
        verify(writer).write(request.capture());
        assertThat(request.getValue().role()).isEqualTo("MEMBER");
        assertThat(request.getValue().handoff()).isEqualTo("RECEPTIONIST");
        assertThat(answer.mode()).isEqualTo("AI_GENERATED");
        assertThat(answer.handoff()).isEqualTo("RECEPTIONIST");
    }

    @Test
    void anOffTopicReplyIsAnsweredPolitelyWithoutSourcesOrHandoff() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new Reply("I'm Spori, Sportify's AI assistant. I can help with classes.", true, "OFF_TOPIC"));

        var answer = service(writer).chat(7L, "MEMBER", "who is the president of the USA", List.of());

        assertThat(answer.status()).isEqualTo("ANSWERED");
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.handoff()).isNull();
    }

    @Test
    void staffGetTheirRoleOverviewAndNeverMemberPrivateColumns() {
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new Reply("Here is today's overview.", true, "SPORTIFY"));

        for (String role : List.of("MANAGER", "RECEPTIONIST", "COACH")) {
            var answer = service(writer).chat(11L, role, "show my schedule", List.of());
            assertThat(answer.mode()).as(role).isEqualTo("AI_GENERATED");
            assertThat(answer.sources()).as(role).allSatisfy(source -> assertThat(source.path()).isEmpty());
        }
        var request = ArgumentCaptor.forClass(ReplyRequest.class);
        verify(writer, times(3)).write(request.capture());
        assertThat(request.getAllValues()).extracting(ReplyRequest::intent).containsOnly("STAFF");
        assertThat(request.getAllValues()).extracting(ReplyRequest::role).containsExactly("MANAGER", "RECEPTIONIST", "COACH");
        assertThat(sql).isNotEmpty().allSatisfy(statement -> assertThat(statement.toLowerCase(Locale.ROOT))
                .doesNotContain("full_name").doesNotContain("email").doesNotContain("phone").doesNotContain("member_package")
                .doesNotContain("payment").doesNotContain("attendance_record"));
    }

    @Test
    void aManagerOverviewWithoutAiListsOnlyAggregateFigures() {
        var answer = service(null).chat(11L, "MANAGER", "my overview", List.of());

        assertThat(answer.status()).isEqualTo("ANSWERED");
        assertThat(answer.answer()).contains("Active members: 3").contains("Open support requests: 3");
    }

    @Test
    void unknownRolesAreRejected() {
        assertThatThrownBy(() -> service(null).chat(1L, "GUEST", "hello", List.of())).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void geminiCategoriesAreReadAndUnknownOnesFallBackToSportify() {
        var writer = new GeminiReplyWriter((uri, key, body, timeout) -> null, "http://localhost/", "gemini-test", "k",
                Duration.ofSeconds(1), new ObjectMapper());
        String body = "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":"
                + "\"{\\\"reply\\\":\\\"Hi!\\\",\\\"answered\\\":true,\\\"category\\\":\\\"%s\\\"}\"}]}}]}";

        assertThat(writer.toReply(body.formatted("OFF_TOPIC")).category()).isEqualTo("OFF_TOPIC");
        assertThat(writer.toReply(body.formatted("HANDOFF_COACH")).category()).isEqualTo("HANDOFF_COACH");
        assertThat(writer.toReply(body.formatted("SOMETHING_ELSE")).category()).isEqualTo("SPORTIFY");
        assertThat(GeminiReplyWriter.SYSTEM_PROMPT).contains("OFF_TOPIC").contains("RECEPTIONIST").contains("COACH")
                .contains("never approve refunds");
    }
}
