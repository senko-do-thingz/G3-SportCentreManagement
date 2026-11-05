package com.sportify.catalog.controller;

import com.sportify.catalog.service.ManagerAttendanceReadService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/training/attendance")
@PreAuthorize("hasRole('MANAGER') and principal.enabled")
public class ManagerAttendanceReadController {
    private final ManagerAttendanceReadService service;
    @GetMapping("/overview")
    public ManagerAttendanceReadService.Overview overview(@AuthenticationPrincipal CustomUserDetails actor,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) Long classId, @RequestParam(required=false) Long coachId) {
        return service.overview(actor.getUserAccount().getId(),from,to,classId,coachId);
    }
    @GetMapping("/sessions/{code}")
    public ManagerAttendanceReadService.Detail detail(@AuthenticationPrincipal CustomUserDetails actor, @PathVariable String code) {
        return service.detail(actor.getUserAccount().getId(),code);
    }
}
