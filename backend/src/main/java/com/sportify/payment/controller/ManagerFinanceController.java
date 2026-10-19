package com.sportify.payment.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.payment.dto.finance.FinanceOverviewResponse;
import com.sportify.payment.dto.finance.FinanceTransactionResponse;
import com.sportify.payment.dto.finance.RevenueBreakdownResponse;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.dto.finance.RevenueTrendPoint;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.FinanceReportService;
import com.sportify.payment.service.FinanceReportService.CsvExport;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Manager finance reports (F3-08 Financial Overview, F3-09 Transactions, F3-10 Revenue Reports, S3-Export).
 * MANAGER only; every other role gets 403.
 */
@RestController
@RequestMapping("/api/v1/manager/finance")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class ManagerFinanceController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final FinanceReportService financeReportService;

    /** month = yyyy-MM, default the current month. */
    @GetMapping("/overview")
    public FinanceOverviewResponse overview(@RequestParam(required = false) String month) {
        return financeReportService.overview(month);
    }

    @GetMapping("/trend")
    public List<RevenueTrendPoint> trend(@RequestParam(required = false) Integer months) {
        return financeReportService.trend(months);
    }

    @GetMapping("/transactions")
    public PageResponse<FinanceTransactionResponse> transactions(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) InvoiceItemType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + FinanceReportService.DEFAULT_PAGE_SIZE) int size) {
        return financeReportService.transactions(status, method, type, from, to, search, page, size);
    }

    @GetMapping("/revenue")
    public RevenueBreakdownResponse revenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ITEM_TYPE") RevenueGroupBy groupBy) {
        return financeReportService.revenue(from, to, groupBy);
    }

    /** S3-Export: the paid payments of the range as a CSV file (UTF-8 with BOM, so Excel shows Vietnamese names). */
    @GetMapping("/revenue/export")
    public ResponseEntity<byte[]> exportRevenue(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CsvExport export = financeReportService.exportRevenue(from, to);
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.fileName()).build().toString())
                .body(export.content());
    }
}
