package com.sportify.payment.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.payment.dto.finance.FinanceOverviewResponse;
import com.sportify.payment.dto.finance.FinanceTransactionResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownResponse;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.dto.finance.RevenueTrendPoint;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;

import java.time.LocalDate;
import java.util.List;

/**
 * Manager finance reports (Flow 3: F3-08 Financial Overview, F3-09 Transactions, F3-10 Revenue Reports, S3-Export).
 * Revenue counts only SUCCESS payments, by payment_time. PENDING, FAILED and REFUNDED payments appear in the ledger
 * but never in revenue; refunds are reported separately.
 */
public interface FinanceReportService {

    int DEFAULT_TREND_MONTHS = 6;
    int MAX_TREND_MONTHS = 24;
    int DEFAULT_PAGE_SIZE = 20;
    int MAX_PAGE_SIZE = 100;
    /** Longest range accepted by the revenue report and the export, in days (inclusive). */
    int MAX_RANGE_DAYS = 366;
    /**
     * Most paid payments one CSV export may hold. The CSV is built in memory (about 200 bytes per row, so about
     * 10 MB at the limit); a larger export answers 400 and the manager picks a shorter range.
     */
    int MAX_EXPORT_ROWS = 50_000;
    /**
     * Earliest date accepted by every date parameter and month. Older dates are not business data, and
     * java.sql.Timestamp uses the Julian calendar before 1582, which could shift a bound sent to SQL Server.
     */
    LocalDate MIN_REPORT_DATE = LocalDate.of(1900, 1, 1);
    /**
     * Latest date accepted by every date parameter and month. An inclusive date d is sent to SQL Server as the
     * exclusive bound d + 1 day at 00:00, and DATETIME2 ends at 9999-12-31, so 9999-12-30 is the last date whose
     * bound fits. Later dates (9999-12-31, LocalDate.MAX, years above 9999) answer 400 instead of failing with 500.
     */
    LocalDate MAX_REPORT_DATE = LocalDate.of(9999, 12, 30);

    /** @param month yyyy-MM, null for the current month; the whole month must lie between MIN_REPORT_DATE and MAX_REPORT_DATE */
    FinanceOverviewResponse overview(String month);

    /** Revenue of the last {@code months} months, ending with the current month, oldest first. */
    List<RevenueTrendPoint> trend(Integer months);

    PageResponse<FinanceTransactionResponse> transactions(PaymentStatus status, PaymentMethod method,
                                                          InvoiceItemType type, LocalDate from, LocalDate to,
                                                          String search, int page, int size);

    /**
     * from and to are inclusive dates between MIN_REPORT_DATE and MAX_REPORT_DATE; null from means the first day of
     * the current month, null to means today.
     */
    RevenueBreakdownResponse revenue(LocalDate from, LocalDate to, RevenueGroupBy groupBy);

    /**
     * CSV of the paid payments of the range: UTF-8 with a byte order mark, header row, CRLF line ends.
     * Same range rules as revenue; more than MAX_EXPORT_ROWS payments answers 400.
     */
    CsvExport exportRevenue(LocalDate from, LocalDate to);

    /** The export file. */
    record CsvExport(String fileName, byte[] content) {
    }
}
