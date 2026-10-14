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

    /** @param month yyyy-MM, null for the current month */
    FinanceOverviewResponse overview(String month);

    /** Revenue of the last {@code months} months, ending with the current month, oldest first. */
    List<RevenueTrendPoint> trend(Integer months);

    PageResponse<FinanceTransactionResponse> transactions(PaymentStatus status, PaymentMethod method,
                                                          InvoiceItemType type, LocalDate from, LocalDate to,
                                                          String search, int page, int size);

    /** from and to are inclusive dates; null from means the first day of the current month, null to means today. */
    RevenueBreakdownResponse revenue(LocalDate from, LocalDate to, RevenueGroupBy groupBy);

    /** CSV of the paid payments of the range: UTF-8 with a byte order mark, header row, CRLF line ends. */
    CsvExport exportRevenue(LocalDate from, LocalDate to);

    /** The export file. */
    record CsvExport(String fileName, byte[] content) {
    }
}
