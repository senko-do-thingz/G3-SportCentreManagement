package com.sportify.payment.controller;

import com.sportify.payment.service.OperationsReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/manager/reports")
@PreAuthorize("hasRole('MANAGER') and principal.enabled")
@RequiredArgsConstructor
public class OperationsReportController {
    private final OperationsReportService reports;
    @GetMapping("/operations")
    public OperationsReportService.Report report(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.report(from,to);
    }
}
