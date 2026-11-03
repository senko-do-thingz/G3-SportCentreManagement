package com.sportify.catalog.controller;

import com.sportify.catalog.dto.MemberTrainingResponse.*;
import com.sportify.catalog.service.MemberTrainingService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/member/training")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class MemberTrainingController {
    private final MemberTrainingService service;
    @GetMapping("/plans")
    public List<Plan> plans(@AuthenticationPrincipal CustomUserDetails user) {
        return service.plans(user.getUserAccount().getId());
    }
    @GetMapping("/plans/{id}")
    public PlanDetail plan(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.plan(user.getUserAccount().getId(), id);
    }
    @GetMapping("/attendance")
    public List<Attendance> attendance(@AuthenticationPrincipal CustomUserDetails user) {
        return service.attendance(user.getUserAccount().getId());
    }
    @GetMapping("/results")
    public List<Result> results(@AuthenticationPrincipal CustomUserDetails user) {
        return service.results(user.getUserAccount().getId());
    }
    @GetMapping("/results/{id}")
    public ResultDetail result(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.result(user.getUserAccount().getId(), id);
    }
    @GetMapping("/feedback")
    public List<Feedback> feedback(@AuthenticationPrincipal CustomUserDetails user) {
        return service.feedback(user.getUserAccount().getId());
    }
}
