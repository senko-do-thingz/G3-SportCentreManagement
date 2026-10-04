package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.PlanCreateRequest;
import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.dto.PlanStatusUpdateRequest;
import com.sportify.catalog.dto.PlanUpdateRequest;
import com.sportify.catalog.service.MembershipPlanService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager workspace "Membership packages".
 */
@RestController
@RequestMapping("/api/v1/manager/plans")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerMembershipPlanController {

    private final MembershipPlanService planService;

    @GetMapping
    public ResponseEntity<PageResponse<PlanResponse>> getAllPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(planService.getAllPlans(page, size));
    }

    @PostMapping
    public ResponseEntity<PlanResponse> createPlan(
            @Valid @RequestBody PlanCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planService.createPlan(request, actorId(principal)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlanResponse> updatePlan(
            @PathVariable Long id,
            @Valid @RequestBody PlanUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(planService.updatePlan(id, request, actorId(principal)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PlanResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody PlanStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(planService.changeStatus(id, request, actorId(principal)));
    }

    private static Long actorId(CustomUserDetails principal) {
        return principal == null ? null : principal.getUserAccount().getId();
    }
}
