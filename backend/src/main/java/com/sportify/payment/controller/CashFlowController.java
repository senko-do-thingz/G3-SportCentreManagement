package com.sportify.payment.controller;

import com.sportify.payment.dto.finance.CashFlowDtos.*;
import com.sportify.payment.dto.finance.RevenueGroupBy;
import com.sportify.payment.service.impl.CashFlowReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/finance/cash-flow")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
public class CashFlowController {
    private final CashFlowReportService service;
    @GetMapping("/overview")
    public Overview overview(@RequestParam(required = false) String month) { return service.overview(month); }
    @GetMapping("/trend")
    public List<Overview> trend(@RequestParam(required = false) Integer months) { return service.trend(months); }
    @GetMapping("/breakdown")
    public Breakdown breakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ITEM_TYPE") RevenueGroupBy groupBy) { return service.breakdown(from, to, groupBy); }
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var result = service.export(from, to);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .body(result.content());
    }
}
