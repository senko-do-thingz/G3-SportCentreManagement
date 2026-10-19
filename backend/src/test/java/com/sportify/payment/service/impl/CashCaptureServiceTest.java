package com.sportify.payment.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CashCaptureServiceTest {
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-03-01T00:00:00.123456Z"), ZoneOffset.UTC);
    @SuppressWarnings("unchecked") void marker(LocalDateTime value) {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(value);
    }
    @Test void inactiveDeploymentCannotClaimCoverage() {
        assertThat(new CashCaptureService(jdbc, clock, false).prepareCollection(3)).isNull();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
        verify(jdbc).queryForObject(contains("HOLDLOCK"), any(RowMapper.class));
    }
    @Test void activeStreamIsCapturedEvenWhenLocalOptInIsFalse() {
        marker(LocalDateTime.of(2026,1,1,0,0));
        assertThat(new CashCaptureService(jdbc, clock, false).prepareCollection(3)).isEqualTo(clock.instant());
        verify(jdbc).update(contains("CURRENT_TRANSACTION_ID()"), eq("COLLECTION"), eq(3L), eq(CashCaptureService.utc(clock.instant())));
        verify(jdbc, never()).update(startsWith("UPDATE"), any(Object[].class));
    }
    @Test void payoutUsesSameAtomicTicketMechanism() {
        marker(LocalDateTime.of(2026,1,1,0,0));
        new CashCaptureService(jdbc, clock, false).preparePayout(9);
        verify(jdbc).update(contains("CURRENT_TRANSACTION_ID()"), eq("REFUND_PAYOUT"), eq(9L), eq(CashCaptureService.utc(clock.instant())));
    }
    @Test @SuppressWarnings("unchecked") void activationPrecedesTicketAndUsesApplicationClock() {
        when(jdbc.queryForObject(anyString(), any(RowMapper.class))).thenReturn(null, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        new CashCaptureService(jdbc, clock, true).prepareCollection(0);
        var order = inOrder(jdbc);
        order.verify(jdbc).queryForObject(anyString(), any(RowMapper.class));
        order.verify(jdbc).update(startsWith("UPDATE dbo.cash_coverage"), eq(CashCaptureService.utc(clock.instant())));
        order.verify(jdbc).queryForObject(anyString(), any(RowMapper.class));
        order.verify(jdbc).update(contains("INSERT INTO dbo.cash_capture_ticket"), eq("COLLECTION"), eq(0L), eq(CashCaptureService.utc(clock.instant())));
    }
    @Test void clockBeforeCutoverAndLedgerPreparationFailureAreNotIgnored() {
        marker(LocalDateTime.of(2026,4,1,0,0));
        assertThatThrownBy(() -> new CashCaptureService(jdbc, clock, false).preparePayout(9)).isInstanceOf(IllegalStateException.class);
        marker(LocalDateTime.of(2026,1,1,0,0));
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new IllegalStateException("Synthetic ticket failure"));
        assertThatThrownBy(() -> new CashCaptureService(jdbc, clock, false).preparePayout(9)).hasMessageContaining("Synthetic ticket failure");
    }
    @Test void utcWallTimestampDoesNotUseClockZone() {
        assertThat(CashCaptureService.utc(clock.instant()).toLocalDateTime()).isEqualTo(LocalDateTime.of(2026,3,1,0,0,0,123456000));
    }
}
