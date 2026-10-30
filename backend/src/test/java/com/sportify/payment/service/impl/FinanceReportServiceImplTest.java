package com.sportify.payment.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.payment.dto.finance.FinanceOverviewResponse;
import com.sportify.payment.dto.finance.FinanceTransactionResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownResponse;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.dto.finance.RevenueTrendPoint;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.FinanceReportRepository;
import com.sportify.payment.repository.FinanceReportRepository.ExportRow;
import com.sportify.payment.repository.FinanceReportRepository.GroupTotal;
import com.sportify.payment.repository.FinanceReportRepository.LedgerFilter;
import com.sportify.payment.repository.FinanceReportRepository.MonthTotal;
import com.sportify.payment.repository.FinanceReportRepository.Totals;
import com.sportify.payment.service.FinanceReportService;
import com.sportify.payment.service.FinanceReportService.CsvExport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinanceReportServiceImplTest {
    @Test
    void exportRejectsRowsThatAppearAfterThePreCount() {
        when(repository.paidTotals(any(), any())).thenReturn(new Totals(BigDecimal.ZERO, 0));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ExportRow> consumer = invocation.getArgument(3);
            for (int i = 0; i <= FinanceReportService.MAX_EXPORT_ROWS; i++) {
                consumer.accept(new ExportRow("PAY-RACE", OCT, null, null, null, null, null, null, null, "CASH", BigDecimal.ONE));
            }
            return null;
        }).when(repository).forEachPaidRow(any(), any(), eq(FinanceReportService.MAX_EXPORT_ROWS + 1), any());
        assertThatThrownBy(() -> service.exportRevenue(null, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("50000");
    }

    @ParameterizedTest
    @ValueSource(strings = {" =1+1", "\n=1+1", "\ufeff@SUM(1)", "  +1", "\tvalue", "\rvalue"})
    void csvNeutralizesFormulaPrefixesAfterWhitespace(String value) {
        assertThat(FinanceReportServiceImpl.text(value)).contains("'" + value);
    }


    /** 2026-10-15 10:00 in Ho Chi Minh City. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime OCT = LocalDateTime.of(2026, 10, 1, 0, 0);
    private static final LocalDateTime NOV = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime SEP = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Mock private FinanceReportRepository repository;

    private FinanceReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FinanceReportServiceImpl(repository, CLOCK);
    }

    private static Totals totals(String amount, long count) {
        return new Totals(new BigDecimal(amount), count);
    }

    // ---------------------------------------------------------------- overview

    @Test
    @DisplayName("Overview: revenue, count, average (half up), pending, refunded, previous month and change")
    void overview_Figures() {
        when(repository.paidTotals(OCT, NOV)).thenReturn(totals("2000000.00", 3));
        when(repository.pendingTotals(OCT, NOV)).thenReturn(totals("700000.00", 1));
        when(repository.refundedTotals(OCT, NOV)).thenReturn(totals("200000.00", 1));
        when(repository.paidTotals(SEP, OCT)).thenReturn(totals("1600000.00", 2));

        FinanceOverviewResponse o = service.overview("2026-10");

        assertThat(o.month()).isEqualTo("2026-10");
        assertThat(o.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(o.to()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(o.paidRevenue()).isEqualByComparingTo("2000000");
        assertThat(o.paidTransactions()).isEqualTo(3);
        assertThat(o.averagePayment()).isEqualByComparingTo("666666.67");
        assertThat(o.pendingAmount()).isEqualByComparingTo("700000");
        assertThat(o.refundedAmount()).isEqualByComparingTo("200000");
        assertThat(o.previousMonth()).isEqualTo("2026-09");
        assertThat(o.previousPaidRevenue()).isEqualByComparingTo("1600000");
        assertThat(o.revenueChange()).isEqualByComparingTo("400000");
        assertThat(o.revenueChangePercent()).isEqualByComparingTo("25.00");
        assertThat(o.simulatedRevenue()).isEqualByComparingTo("0.00");
        assertThat(o.simulatedTransactions()).isZero();
    }

    @Test
    @DisplayName("Overview: simulated QR settlements are reported separately and never added to paidRevenue")
    void overview_SeparatesSimulatedTotalsFromActualPaidRevenue() {
        when(repository.paidTotals(OCT, NOV)).thenReturn(totals("2000000.00", 3));
        when(repository.pendingTotals(OCT, NOV)).thenReturn(totals("700000.00", 1));
        when(repository.refundedTotals(OCT, NOV)).thenReturn(totals("200000.00", 1));
        when(repository.paidTotals(SEP, OCT)).thenReturn(totals("1600000.00", 2));
        when(repository.simulatedTotals(OCT, NOV)).thenReturn(totals("810000.00", 2));

        FinanceOverviewResponse o = service.overview("2026-10");

        assertThat(o.paidRevenue()).isEqualByComparingTo("2000000.00");
        assertThat(o.paidTransactions()).isEqualTo(3);
        assertThat(o.simulatedRevenue()).isEqualByComparingTo("810000.00");
        assertThat(o.simulatedTransactions()).isEqualTo(2);
    }

    @Test
    @DisplayName("Overview without month uses the current month; no revenue gives average 0 and no change percent")
    void overview_DefaultMonthAndEmpty() {
        when(repository.paidTotals(any(), any())).thenReturn(totals("0", 0));
        when(repository.pendingTotals(OCT, NOV)).thenReturn(totals("0", 0));
        when(repository.refundedTotals(OCT, NOV)).thenReturn(totals("0", 0));

        FinanceOverviewResponse o = service.overview(null);

        assertThat(o.month()).isEqualTo("2026-10");
        assertThat(o.averagePayment()).isEqualByComparingTo("0");
        assertThat(o.revenueChangePercent()).isNull();
    }

    @Test
    @DisplayName("Overview: revenue fell to zero gives -100.00 percent")
    void overview_DropToZero() {
        when(repository.paidTotals(OCT, NOV)).thenReturn(totals("0", 0));
        when(repository.pendingTotals(OCT, NOV)).thenReturn(totals("0", 0));
        when(repository.refundedTotals(OCT, NOV)).thenReturn(totals("0", 0));
        when(repository.paidTotals(SEP, OCT)).thenReturn(totals("500000", 1));

        assertThat(service.overview("2026-10").revenueChangePercent()).isEqualByComparingTo("-100.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "10-2026", "2026/10", "abc"})
    @DisplayName("Overview with a bad month is 400")
    void overview_BadMonth(String month) {
        assertThatThrownBy(() -> service.overview(month)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(repository);
    }

    // ---------------------------------------------------------------- trend

    @Test
    @DisplayName("Trend: 6 months ending with the current month, months without payments are zero")
    void trend_FillsMissingMonths() {
        when(repository.monthlyRevenue(LocalDateTime.of(2026, 5, 1, 0, 0), NOV)).thenReturn(List.of(
                new MonthTotal(2026, 6, new BigDecimal("300000"), 2),
                new MonthTotal(2026, 10, new BigDecimal("900000"), 3)));

        List<RevenueTrendPoint> trend = service.trend(null);

        assertThat(trend).extracting(RevenueTrendPoint::month)
                .containsExactly("2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10");
        assertThat(trend.get(0).revenue()).isEqualByComparingTo("0");
        assertThat(trend.get(1).revenue()).isEqualByComparingTo("300000");
        assertThat(trend.get(1).transactions()).isEqualTo(2);
        assertThat(trend.get(5).revenue()).isEqualByComparingTo("900000");
    }

    @Test
    @DisplayName("Trend crosses a year boundary")
    void trend_CrossesYear() {
        when(repository.monthlyRevenue(LocalDateTime.of(2025, 11, 1, 0, 0), NOV)).thenReturn(List.of());

        assertThat(service.trend(12)).extracting(RevenueTrendPoint::month).startsWith("2025-11", "2025-12", "2026-01");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 25, -1})
    @DisplayName("Trend months outside 1 to 24 is 400")
    void trend_BadMonths(int months) {
        assertThatThrownBy(() -> service.trend(months)).isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- ledger

    @Test
    @DisplayName("Ledger: filters passed as names, to date made exclusive, search trimmed")
    void transactions_Filters() {
        when(repository.countLedger(any())).thenReturn(45L);
        when(repository.ledgerPage(any(), eq(2), eq(20))).thenReturn(List.of());

        PageResponse<FinanceTransactionResponse> page = service.transactions(PaymentStatus.FAILED, PaymentMethod.CASH,
                InvoiceItemType.SPORT_PACKAGE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), "  Anh ", 2, 20);

        ArgumentCaptor<LedgerFilter> filter = ArgumentCaptor.forClass(LedgerFilter.class);
        verify(repository).countLedger(filter.capture());
        assertThat(filter.getValue()).isEqualTo(new LedgerFilter("FAILED", "CASH", "SPORT_PACKAGE",
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 10, 16, 0, 0), "Anh"));
        assertThat(page.totalElements()).isEqualTo(45);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.page()).isEqualTo(2);
    }

    @Test
    @DisplayName("Ledger: nothing found skips the page query")
    void transactions_Empty() {
        when(repository.countLedger(any())).thenReturn(0L);

        PageResponse<FinanceTransactionResponse> page = service.transactions(null, null, null, null, null, " ", 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalPages()).isZero();
        verify(repository, never()).ledgerPage(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Ledger: bad page, size or range is 400")
    void transactions_BadParameters() {
        assertThatThrownBy(() -> service.transactions(null, null, null, null, null, null, -1, 20))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.transactions(null, null, null, null, null, null, 0, 101))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.transactions(null, null, null, LocalDate.of(2026, 10, 2),
                LocalDate.of(2026, 10, 1), null, 0, 20)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(repository);
    }

    // ---------------------------------------------------------------- revenue

    @Test
    @DisplayName("Revenue by method: totals, labels and shares that add up to 100")
    void revenue_ByMethod() {
        when(repository.revenueBy(RevenueGroupBy.METHOD, OCT, LocalDateTime.of(2026, 10, 16, 0, 0))).thenReturn(List.of(
                new GroupTotal("MOMO", "MOMO", new BigDecimal("1200000"), 1),
                new GroupTotal("CASH", "CASH", new BigDecimal("500000"), 1),
                new GroupTotal("BANK_TRANSFER", "BANK_TRANSFER", new BigDecimal("300000"), 1)));

        RevenueBreakdownResponse r = service.revenue(null, null, RevenueGroupBy.METHOD);

        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.to()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(r.totalRevenue()).isEqualByComparingTo("2000000");
        assertThat(r.totalTransactions()).isEqualTo(3);
        assertThat(r.rows()).extracting(com.sportify.payment.dto.finance.RevenueBreakdownRow::label).containsExactly("MoMo", "Cash", "Bank transfer");
        assertThat(r.rows()).extracting(row -> row.sharePercent().toPlainString()).containsExactly("60.00", "25.00", "15.00");
    }

    @Test
    @DisplayName("Revenue by sport: the group without a sport gets its own label")
    void revenue_BySport_NullGroup() {
        when(repository.revenueBy(eq(RevenueGroupBy.SPORT), any(), any())).thenReturn(List.of(
                new GroupTotal(null, null, new BigDecimal("1200000"), 1),
                new GroupTotal("7", "B\u01a1i l\u1ed9i", new BigDecimal("800000"), 2)));

        RevenueBreakdownResponse r = service.revenue(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), RevenueGroupBy.SPORT);

        assertThat(r.rows().get(0).label()).isEqualTo("No sport (membership card or other)");
        assertThat(r.rows().get(1).label()).isEqualTo("B\u01a1i l\u1ed9i");
    }

    @Test
    @DisplayName("Revenue: no payments gives an empty breakdown with total 0")
    void revenue_Empty() {
        when(repository.revenueBy(eq(RevenueGroupBy.ITEM_TYPE), any(), any())).thenReturn(List.of());

        RevenueBreakdownResponse r = service.revenue(null, null, RevenueGroupBy.ITEM_TYPE);

        assertThat(r.rows()).isEmpty();
        assertThat(r.totalRevenue()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Revenue: from after to, a range longer than 366 days, or no groupBy is 400")
    void revenue_BadRange() {
        assertThatThrownBy(() -> service.revenue(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1), RevenueGroupBy.METHOD))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.revenue(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2), RevenueGroupBy.METHOD))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.revenue(null, null, null)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Shares: three equal parts are 33.34, 33.33, 33.33 and add up to 100.00")
    void sharePercents_ThreeEqual() {
        List<BigDecimal> shares = FinanceReportServiceImpl.sharePercents(
                List.of(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("100")), new BigDecimal("300"));

        assertThat(shares).extracting(BigDecimal::toPlainString).containsExactly("33.34", "33.33", "33.33");
        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Shares: uneven parts still add up to exactly 100.00, the largest remainder gets the extra cent")
    void sharePercents_Uneven() {
        List<BigDecimal> amounts = List.of(new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("3"));
        List<BigDecimal> shares = FinanceReportServiceImpl.sharePercents(amounts, new BigDecimal("9"));

        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100.00");
        // 6 x 11.11 + 33.33 = 99.99; the missing cent goes to 3/9 (remainder 0.0033) rather than 1/9 (0.0011).
        assertThat(shares.get(6)).isEqualByComparingTo("33.34");
        assertThat(shares.get(0)).isEqualByComparingTo("11.11");
        shares.forEach(s -> assertThat(s.scale()).isEqualTo(2));
    }

    // ---------------------------------------------------------------- CSV

    @Test
    @DisplayName("CSV: BOM, header, CRLF, Vietnamese text in UTF-8, comma and quotes escaped, formula neutralised, plain amounts")
    void export_Format() {
        stubExportRows(OCT, LocalDateTime.of(2026, 10, 16, 0, 0), List.of(
                new ExportRow("PAY-0001", LocalDateTime.of(2026, 10, 5, 10, 0), "INV-0001", "MEM-0001",
                        "Nguy\u1ec5n V\u0103n \u00c1nh", "SPORT_PACKAGE", "REG-PKG-0001", "B\u01a1i l\u1ed9i 30 ng\u00e0y", "B\u01a1i l\u1ed9i",
                        "BANK_TRANSFER", new BigDecimal("300000.00")),
                new ExportRow("PAY-0002", LocalDateTime.of(2026, 10, 6, 9, 0), null, "MEM-0002",
                        "Tr\u1ea7n, \"B\u00edch\"", "MEMBERSHIP_CARD", "CARD-0002", "=HYPERLINK(\"x\")", null,
                        "MOMO", new BigDecimal("1200000.50"))));

        CsvExport export = service.exportRevenue(null, null);

        assertThat(export.fileName()).isEqualTo("revenue_2026-10-01_2026-10-15.csv");
        byte[] bytes = export.content();
        assertThat(Arrays.copyOf(bytes, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(text).isEqualTo(FinanceReportServiceImpl.CSV_HEADER + "\r\n"
                + "PAY-0001,2026-10-05 10:00:00,INV-0001,MEM-0001,Nguy\u1ec5n V\u0103n \u00c1nh,SPORT_PACKAGE,REG-PKG-0001,"
                + "B\u01a1i l\u1ed9i 30 ng\u00e0y,B\u01a1i l\u1ed9i,BANK_TRANSFER,300000\r\n"
                + "PAY-0002,2026-10-06 09:00:00,,MEM-0002,\"Tr\u1ea7n, \"\"B\u00edch\"\"\",MEMBERSHIP_CARD,CARD-0002,"
                + "\"'=HYPERLINK(\"\"x\"\")\",,MOMO,1200000.5\r\n");
    }

    @Test
    @DisplayName("CSV with no rows still has the BOM and the header")
    void export_Empty() {
        stubExportRows(SEP, OCT, List.of());

        byte[] bytes = service.exportRevenue(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).content();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("\ufeff" + FinanceReportServiceImpl.CSV_HEADER + "\r\n");
    }

    /** The repository count says rows.size() payments, then streams the rows to the consumer. */
    @SuppressWarnings("unchecked")
    private void stubExportRows(LocalDateTime from, LocalDateTime to, List<ExportRow> rows) {
        when(repository.paidTotals(from, to)).thenReturn(new Totals(BigDecimal.ZERO, rows.size()));
        doAnswer(invocation -> {
            Consumer<ExportRow> consumer = invocation.getArgument(3);
            rows.forEach(consumer);
            return null;
        }).when(repository).forEachPaidRow(eq(from), eq(to), eq(FinanceReportService.MAX_EXPORT_ROWS + 1), any(Consumer.class));
    }

    @Test
    @DisplayName("CSV: more payments than MAX_EXPORT_ROWS is 400 and no row is read")
    void export_OverLimit() {
        when(repository.paidTotals(OCT, LocalDateTime.of(2026, 10, 16, 0, 0)))
                .thenReturn(new Totals(BigDecimal.ZERO, FinanceReportService.MAX_EXPORT_ROWS + 1L));

        assertThatThrownBy(() -> service.exportRevenue(null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining(String.valueOf(FinanceReportService.MAX_EXPORT_ROWS));
        verify(repository, never()).forEachPaidRow(any(), any(), eq(FinanceReportService.MAX_EXPORT_ROWS + 1), any());
    }

    @Test
    @DisplayName("CSV: exactly MAX_EXPORT_ROWS payments is still exported")
    void export_AtLimit() {
        ExportRow row = new ExportRow("PAY-LIMIT", SEP, null, "MEM-LIMIT", "Synthetic member",
                null, null, null, null, "CASH", new BigDecimal("1.25"));
        stubExportRows(SEP, OCT, java.util.Collections.nCopies(FinanceReportService.MAX_EXPORT_ROWS, row));

        CsvExport export = service.exportRevenue(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(export.fileName()).isEqualTo("revenue_2026-09-01_2026-09-30.csv");
        assertThat(new String(export.content(), StandardCharsets.UTF_8).lines().count())
                .isEqualTo(FinanceReportService.MAX_EXPORT_ROWS + 1L);
        verify(repository).forEachPaidRow(eq(SEP), eq(OCT), eq(FinanceReportService.MAX_EXPORT_ROWS + 1), any());
    }

    // ---------------------------------------------------------------- supported dates (review P1-01)

    @Test
    @DisplayName("Ledger: to = 9999-12-30 is accepted and becomes the exclusive bound 9999-12-31 00:00")
    void transactions_LastSupportedDate() {
        when(repository.countLedger(any())).thenReturn(0L);

        service.transactions(null, null, null, LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 30), null, 0, 20);

        ArgumentCaptor<LedgerFilter> filter = ArgumentCaptor.forClass(LedgerFilter.class);
        verify(repository).countLedger(filter.capture());
        assertThat(filter.getValue().from()).isEqualTo(LocalDateTime.of(1900, 1, 1, 0, 0));
        assertThat(filter.getValue().toExclusive()).isEqualTo(LocalDateTime.of(9999, 12, 31, 0, 0));
    }

    @ParameterizedTest(name = "from={0} to={1}")
    @CsvSource({
            ", 9999-12-31",
            ", +10000-01-01",
            ", +999999999-12-31",
            "1899-12-31, ",
            "0001-01-01, 2026-10-01",
            "-999999999-01-01, 2026-10-01",
            "+999999999-12-31, +999999999-12-31"
    })
    @DisplayName("Ledger: a date outside 1900-01-01 to 9999-12-30 is 400 and the repository is not called")
    void transactions_UnsupportedDate(String from, String to) {
        assertThatThrownBy(() -> service.transactions(null, null, null, date(from), date(to), null, 0, 20))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 1900-01-01 and 9999-12-30");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Revenue and export: 9999-01-01 to 9999-12-30 is accepted with the bound 9999-12-31 00:00")
    void revenueAndExport_LastSupportedDate() {
        LocalDateTime start = LocalDateTime.of(9999, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(9999, 12, 31, 0, 0);
        when(repository.revenueBy(RevenueGroupBy.METHOD, start, end)).thenReturn(List.of());
        when(repository.paidTotals(start, end)).thenReturn(new Totals(BigDecimal.ZERO, 0));

        assertThat(service.revenue(LocalDate.of(9999, 1, 1), LocalDate.of(9999, 12, 30), RevenueGroupBy.METHOD).rows())
                .isEmpty();
        assertThat(service.exportRevenue(LocalDate.of(9999, 1, 1), LocalDate.of(9999, 12, 30)).fileName())
                .isEqualTo("revenue_9999-01-01_9999-12-30.csv");
        verify(repository).forEachPaidRow(eq(start), eq(end), eq(FinanceReportService.MAX_EXPORT_ROWS + 1), any());
    }

    @ParameterizedTest(name = "from={0} to={1}")
    @CsvSource({
            "9999-12-31, 9999-12-31",
            "9999-12-30, 9999-12-31",
            "+999999999-12-31, +999999999-12-31",
            ", +999999999-12-31",
            "1899-12-31, 1900-01-01",
            "-999999999-01-01, "
    })
    @DisplayName("Revenue and export: a date outside 1900-01-01 to 9999-12-30 is 400 before any query")
    void revenueAndExport_UnsupportedDate(String from, String to) {
        assertThatThrownBy(() -> service.revenue(date(from), date(to), RevenueGroupBy.ITEM_TYPE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 1900-01-01 and 9999-12-30");
        assertThatThrownBy(() -> service.exportRevenue(date(from), date(to)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 1900-01-01 and 9999-12-30");
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("Overview: 1900-01 and 9999-11 are accepted, 1899-12 and 9999-12 are 400")
    void overview_SupportedMonths() {
        when(repository.paidTotals(any(), any())).thenReturn(totals("0", 0));
        when(repository.pendingTotals(any(), any())).thenReturn(totals("0", 0));
        when(repository.refundedTotals(any(), any())).thenReturn(totals("0", 0));

        assertThat(service.overview("1900-01").month()).isEqualTo("1900-01");
        assertThat(service.overview("9999-11").to()).isEqualTo(LocalDate.of(9999, 11, 30));
        verify(repository).paidTotals(LocalDateTime.of(9999, 11, 1, 0, 0), LocalDateTime.of(9999, 12, 1, 0, 0));

        assertThatThrownBy(() -> service.overview("1899-12")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 1900-01 and 9999-11");
        assertThatThrownBy(() -> service.overview("9999-12")).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 1900-01 and 9999-11");
    }

    private static LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    @Test
    @DisplayName("Amounts: no grouping, no trailing zeros, no exponent")
    void plainAmount() {
        assertThat(FinanceReportServiceImpl.plainAmount(new BigDecimal("380000.00"))).isEqualTo("380000");
        assertThat(FinanceReportServiceImpl.plainAmount(new BigDecimal("1000000"))).isEqualTo("1000000");
        assertThat(FinanceReportServiceImpl.plainAmount(new BigDecimal("12.50"))).isEqualTo("12.5");
        assertThat(FinanceReportServiceImpl.plainAmount(new BigDecimal("0.00"))).isEqualTo("0");
    }
}
