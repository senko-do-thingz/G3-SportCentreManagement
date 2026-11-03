package com.sportify.catalog.controller;

import com.sportify.catalog.dto.AttendanceBackfillRequest;
import com.sportify.catalog.dto.TrainingWriteResponse.Created;
import com.sportify.catalog.service.AttendanceBackfillService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manager/training/attendance/backfill")
@PreAuthorize("hasRole('MANAGER') and principal.enabled")
public class ManagerTrainingController {
    private final AttendanceBackfillService service;

    @GetMapping
    public AttendanceBackfillService.Workspace workspace(@AuthenticationPrincipal CustomUserDetails user) {
        return service.workspace(user.getUserAccount().getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Created backfill(@Valid @RequestBody AttendanceBackfillRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.backfill(user.getUserAccount().getId(), request);
    }
}
