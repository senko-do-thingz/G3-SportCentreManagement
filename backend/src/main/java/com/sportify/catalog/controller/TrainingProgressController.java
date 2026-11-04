package com.sportify.catalog.controller;

import com.sportify.catalog.service.TrainingProgressService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TrainingProgressController {
    private final TrainingProgressService progress;
    @GetMapping("/member/training/progress")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public TrainingProgressService.Progress member(@AuthenticationPrincipal CustomUserDetails actor) {
        return progress.member(actor.getUserAccount().getId());
    }
    @GetMapping("/coach/training/classes/{classId}/members/{memberId}/progress")
    @PreAuthorize("hasRole('COACH') and principal.enabled")
    public TrainingProgressService.Progress coach(@PathVariable long classId,@PathVariable long memberId,@AuthenticationPrincipal CustomUserDetails actor) {
        return progress.coach(actor.getUserAccount().getId(),classId,memberId);
    }
}
