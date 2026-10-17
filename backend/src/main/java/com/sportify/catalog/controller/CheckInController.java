package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.service.CheckInService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.time.LocalDate;
import jakarta.validation.Valid;

@RestController
@RequestMapping({"/api/v1/check-ins", "/api/v1/check-in"})
@RequiredArgsConstructor
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckInResponse recordCheckIn(
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return checkInService.recordCheckIn(request, userDetails.getUserAccount());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public List<CheckInResponse> getCheckInHistory(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date != null) {
            return checkInService.getCheckInsOn(date, memberId);
        }
        if (memberId == null) {
            throw new BusinessRuleException("Provide memberId or date");
        }
        return checkInService.getCheckInHistory(memberId);
    }
}
