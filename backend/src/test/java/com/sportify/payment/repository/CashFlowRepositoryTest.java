package com.sportify.payment.repository;

import com.sportify.payment.dto.finance.RevenueGroupBy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.core.namedparam.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CashFlowRepositoryTest {
    final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    final CashFlowRepository repository = new CashFlowRepository(jdbc);
    final Instant start = Instant.parse("2026-03-31T17:00:00Z"), end = Instant.parse("2026-04-30T17:00:00Z");
    @Test void eventExportIsBoundedAndOrderedByImmutableEventIdentity() {
        repository.events(start, end, 50001, event -> { });
        var sql = ArgumentCaptor.forClass(String.class); var params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), params.capture(), any(RowCallbackHandler.class));
        assertThat(sql.getValue()).contains("TOP (:limit)", "ORDER BY event_time_utc, id", "event_time_utc >= :start", "event_time_utc < :end")
                .doesNotContain("vw_paid_payment", "payment_status", "activity_log");
        assertThat(params.getValue().getValue("limit")).isEqualTo(50001);
        assertThat(params.getValue().getValue("start")).isEqualTo(java.sql.Timestamp.valueOf("2026-03-31 17:00:00"));
    }
    @Test @SuppressWarnings("unchecked") void everyGroupingReadsOneImmutableEventTableAndKeepsUnallocated() {
        for (RevenueGroupBy by : RevenueGroupBy.values()) repository.groups(start, end, by);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(3)).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getAllValues()).allSatisfy(query -> assertThat(query).contains("UNALLOCATED", "FROM dbo.cash_event", "REFUND_PAYOUT")
                .doesNotContain("JOIN", "DISTINCT", "updated_at"));
    }
    @Test @SuppressWarnings("unchecked") void aggregateUsesBothEventTypesWithExactSumsAndUniqueRowCounts() {
        repository.totals(start, end);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("SUM(CASE", "CAST(1 AS BIGINT)", "COLLECTION", "REFUND_PAYOUT").doesNotContain("JOIN", "FLOAT");
    }
}
