package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.service.MembershipPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (no token) catalog endpoints for membership plans. Only ACTIVE plans are visible.
 */
@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
public class MembershipPlanController {

    private final MembershipPlanService planService;

    @GetMapping
    public ResponseEntity<List<PlanResponse>> getActivePlans() {
        return ResponseEntity.ok(planService.getActivePlans());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getActivePlan(@PathVariable Long id) {
        return ResponseEntity.ok(planService.getActivePlan(id));
    }
}
