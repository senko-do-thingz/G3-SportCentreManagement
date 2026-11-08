package com.sportify.identity.controller;

import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.MemberHealthService;
import com.sportify.identity.service.MemberHealthService.Assessment;
import com.sportify.identity.service.MemberHealthService.CoachSummary;
import com.sportify.identity.service.MemberHealthService.SaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MW-02: the Member's own optional health questionnaire, and the consented outcome summary for an assigned Coach. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberHealthController {

    private final MemberHealthService service;

    @GetMapping("/member/health-assessment")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public Assessment get(@AuthenticationPrincipal CustomUserDetails user) {
        return service.get(user.getUserAccount().getId());
    }

    @PutMapping("/member/health-assessment")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public Assessment save(@RequestBody SaveRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.save(user.getUserAccount().getId(), request);
    }

    @DeleteMapping("/member/health-assessment")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public Assessment clear(@AuthenticationPrincipal CustomUserDetails user) {
        return service.clear(user.getUserAccount().getId());
    }

    @GetMapping("/coach/training/classes/{classId}/members/{memberId}/health-summary")
    @PreAuthorize("hasRole('COACH') and principal.enabled")
    public CoachSummary coachSummary(@PathVariable long classId, @PathVariable long memberId,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        return service.coachSummary(user.getUserAccount().getId(), classId, memberId);
    }
}
