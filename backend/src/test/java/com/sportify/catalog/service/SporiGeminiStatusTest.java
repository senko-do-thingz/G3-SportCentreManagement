package com.sportify.catalog.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SporiGeminiStatusTest {
    private CenterConsultationService service() {
        return new CenterConsultationService(
                mock(JdbcTemplate.class), Clock.systemUTC(), true, mock(VisibleTrainingSessions.class));
    }

    @Test
    void reportsDisabledWithoutContactingProviderWhenWriterIsAbsent() {
        var service = service();
        assertThat(service.geminiStatus().answer()).contains("DISABLED");
        assertThat(service.geminiStatus().sources()).isEmpty();
    }

    @Test
    void confirmsLiveProviderResponseAndCachesRepeatedChecks() {
        var service = service();
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenReturn(new AssistantReplyWriter.Reply("Hello from Gemini", true, "GREETING"));
        service.setReplyWriter(writer);

        var first = service.geminiStatus();
        var second = service.geminiStatus();

        assertThat(first.answer()).contains("CONNECTED").doesNotContain("NOT CONNECTED");
        assertThat(second).isEqualTo(first);
        assertThat(first.historyStored()).isFalse();
        verify(writer, times(1)).write(argThat(probe ->
                probe.history().equals(List.of()) && probe.facts().isEmpty()
                        && probe.question().equals("Hello")));
    }

    @Test
    void reportsFailureWithoutLeakingProviderExceptionDetails() {
        var service = service();
        var writer = mock(AssistantReplyWriter.class);
        when(writer.write(any())).thenThrow(new IllegalStateException("secret-provider-response"));
        service.setReplyWriter(writer);

        var reply = service.geminiStatus();
        assertThat(reply.answer()).contains("NOT CONNECTED").doesNotContain("secret-provider-response");
        verify(writer, times(1)).write(any());
    }
}
