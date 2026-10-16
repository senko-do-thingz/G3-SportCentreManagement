package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SessionDetailResponse;
import com.sportify.catalog.dto.SessionResponse;
import com.sportify.catalog.dto.SessionSearchQuery;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.service.MemberScheduleService;
import com.sportify.core.common.PagingDefaults;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sessions")
@PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
@RequiredArgsConstructor
public class SessionController {
    private final MemberScheduleService service;

    @GetMapping
    public PageResponse<SessionResponse> listSessions(
            @RequestParam(required = false) Long sportId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TrainingFormat format,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) Long coachId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PagingDefaults.DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.listSessions(new SessionSearchQuery(sportId, from, to, format, level, coachId, page, size),
                principal == null ? null : principal.getUserAccount());
    }

    @GetMapping("/{id}")
    public SessionDetailResponse getSession(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.getSession(id, principal == null ? null : principal.getUserAccount());
    }

    @GetMapping("/{id}/eligibility")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public SessionDetailResponse getMemberEligibility(@PathVariable Long id, @RequestParam Long memberId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.getMemberEligibility(id, memberId, principal == null ? null : principal.getUserAccount());
    }
}
