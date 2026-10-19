package com.sportify.payment.service.impl;

import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.payment.dto.finance.CashFlowDtos.*;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.repository.CashFlowRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CashFlowReportServiceTest {
    final CashFlowRepository repository = mock(CashFlowRepository.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-04-30T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    final CashFlowReportService service = new CashFlowReportService(repository, clock);
    void covered() {
        when(repository.cutover()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        when(repository.guardsActive()).thenReturn(true);
    }
    @Test void marchCollectionAndAprilPartialPayoutReconcileWithoutErasingGross() {
        covered();
        when(repository.totals(Instant.parse("2026-02-28T17:00:00Z"), Instant.parse("2026-03-31T17:00:00Z")))
                .thenReturn(new Totals(new BigDecimal("500000.00"), new BigDecimal("0.00"), 1, 0));
        when(repository.totals(Instant.parse("2026-03-31T17:00:00Z"), Instant.parse("2026-04-30T17:00:00Z")))
                .thenReturn(new Totals(new BigDecimal("0.00"), new BigDecimal("100000.00"), 0, 1));
        var march = service.overview("2026-03");
        var april = service.overview("2026-04");
        assertThat(march.grossCashReceived()).isEqualByComparingTo("500000");
        assertThat(march.netCashFlow()).isEqualByComparingTo("500000");
        assertThat(april.netCashFlow()).isEqualByComparingTo("-100000");
        assertThat(march.netCashFlow().add(april.netCashFlow())).isEqualByComparingTo("400000");
        assertThat(april.completedRefundCount()).isEqualTo(1);
    }
    @Test void inactiveAndCrossCutoverPeriodsAreUnavailableNotZero() {
        when(repository.cutover()).thenReturn(null, Instant.parse("2026-03-15T00:00:00Z"));
        assertThatThrownBy(() -> service.overview("2026-03")).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.overview("2026-03")).isInstanceOf(ConflictException.class);
        verify(repository, never()).totals(any(), any());
    }
    @Test void completeEmptyPeriodHasExplicitCoverageAndZeros() {
        covered();
        when(repository.totals(any(), any())).thenReturn(new Totals(new BigDecimal("0.00"), new BigDecimal("0.00"), 0, 0));
        var result = service.overview("2026-04");
        assertThat(result.coverage().status()).isEqualTo("VERIFIED_COMPLETE");
        assertThat(result.coverage().evidenceBasis()).isEqualTo("SYSTEM_RECORDED");
        assertThat(result.netCashFlow()).isEqualByComparingTo("0");
    }
    @Test void disabledDatabaseGuardRefusesCoverage() {
        when(repository.cutover()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        assertThatThrownBy(() -> service.overview("2026-04")).isInstanceOf(ConflictException.class);
        verify(repository, never()).totals(any(), any());
    }
    @Test void negativeUnallocatedGroupReconciles() {
        covered();
        when(repository.totals(any(), any())).thenReturn(new Totals(new BigDecimal("0.00"), new BigDecimal("100000.00"), 0, 1));
        when(repository.groups(any(), any(), eq(RevenueGroupBy.SPORT))).thenReturn(List.of(new Group("UNALLOCATED", "UNALLOCATED",
                new BigDecimal("0.00"), new BigDecimal("100000.00"), new BigDecimal("-100000.00"), 0, 1)));
        var result = service.breakdown(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), RevenueGroupBy.SPORT);
        assertThat(result.rows().getFirst().netCashFlow()).isEqualByComparingTo(result.netCashFlow());
    }
    @Test void csvUsesSignedImpactAndNeutralizesOriginalAttribution() {
        covered();
        when(repository.totals(any(), any())).thenReturn(new Totals(new BigDecimal("0.00"), new BigDecimal("100.25"), 0, 1));
        doAnswer(invocation -> {
            Consumer<Event> out = invocation.getArgument(3);
            out.accept(new Event(2, "REFUND_PAYOUT", Instant.parse("2026-04-01T17:00:00Z"), 1L, 2L,
                    new BigDecimal("100.25"), "CASH", null, "1", "  =SUM(1,2)")); return null;
        }).when(repository).events(any(), any(), anyInt(), any());
        String csv = new String(service.export(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)).content(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\ufeffevent_id").contains(",100.25,-100.25,").contains("'  =SUM(1,2)").contains("UNALLOCATED");
    }
    @Test void oversizeAndMalformedQueriesAreRefused() {
        covered();
        when(repository.totals(any(), any())).thenReturn(new Totals(BigDecimal.ONE, BigDecimal.ZERO, 50001, 0));
        assertThatThrownBy(() -> service.export(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.overview("2026-13")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.trend(25)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.breakdown(LocalDate.MAX, LocalDate.MAX, RevenueGroupBy.METHOD)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void partlyCoveredTrendIsRefusedBeforeAnyMonthlyTotals() {
        when(repository.cutover()).thenReturn(Instant.parse("2026-03-15T00:00:00Z"));
        assertThatThrownBy(() -> service.trend(2)).isInstanceOf(ConflictException.class);
        verify(repository,never()).totals(any(),any());
    }
    @Test void groupingMismatchCannotBeReturnedAsAValidFinancialReport() {
        covered();
        when(repository.totals(any(),any())).thenReturn(new Totals(BigDecimal.ONE,BigDecimal.ZERO,1,0));
        when(repository.groups(any(),any(),any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.breakdown(LocalDate.of(2026,4,1),LocalDate.of(2026,4,30),RevenueGroupBy.METHOD))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reconciliation");
    }
    @Test void countStreamRaceCannotProduceOversizedSuccessfulCsv() {
        covered();
        when(repository.totals(any(),any())).thenReturn(new Totals(new BigDecimal("50000"),BigDecimal.ZERO,50000,0));
        doAnswer(invocation -> {
            Consumer<Event> out=invocation.getArgument(3);
            for(int i=1;i<=50001;i++) out.accept(new Event(i,"COLLECTION",clock.instant(),(long)i,null,BigDecimal.ONE,null,null,null,null));
            return null;
        }).when(repository).events(any(),any(),eq(50001),any());
        assertThatThrownBy(() -> service.export(LocalDate.of(2026,4,1),LocalDate.of(2026,4,30)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("50000");
    }
}
