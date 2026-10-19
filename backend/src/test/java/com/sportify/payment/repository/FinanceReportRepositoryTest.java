package com.sportify.payment.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FinanceReportRepositoryTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final FinanceReportRepository repository = new FinanceReportRepository(jdbc);

    @Test
    void exportQueryIsBoundedEvenWhenTheEarlierCountBecomesStale() {
        var from = LocalDateTime.of(2001, 3, 1, 0, 0);
        var to = from.plusMonths(1);
        repository.forEachPaidRow(from, to, 50001, row -> { });
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowCallbackHandler.class));
        assertThat(sql.getValue()).contains("TOP (:limit)", "FROM dbo.vw_paid_payment", "ORDER BY payment_time, payment_id");
        assertThat(parameters.getValue().getValue("limit")).isEqualTo(50001);
        assertThat(parameters.getValue().getValue("from")).isEqualTo(java.sql.Timestamp.valueOf(from));
        assertThat(parameters.getValue().getValue("to")).isEqualTo(java.sql.Timestamp.valueOf(to));
    }

    @Test
    void ledgerSearchAndFiltersAreBoundParametersWithLiteralWildcards() {
        when(jdbc.queryForObject(anyString(), any(SqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        repository.countLedger(new FinanceReportRepository.LedgerFilter("SUCCESS", "CASH", "SPORT_PACKAGE", null, null, "%_[\\"));
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("COUNT_BIG(*)", "status = :status", "method = :method", "item_type = :itemType")
                .doesNotContain("SPORT_PACKAGE");
        assertThat(parameters.getValue().getValue("search")).isEqualTo("%\\%\\_\\[\\\\%");
        assertThat(parameters.getValue().getValue("method")).isEqualTo("CASH");
    }

    @SuppressWarnings("unchecked")
    @Test
    void offsetUsesLongArithmeticAndStablePaymentIdTieBreak() {
        repository.ledgerPage(new FinanceReportRepository.LedgerFilter(null, null, null, null, null, null), Integer.MAX_VALUE, 100);
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains("ORDER BY ledger_time DESC, payment_id DESC", "OFFSET :offset");
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(214748364700L);
        assertThat(parameters.getValue().getValue("size")).isEqualTo(100);
    }
}
