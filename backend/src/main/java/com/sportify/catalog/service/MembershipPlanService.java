package com.sportify.catalog.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.PlanCreateRequest;
import com.sportify.catalog.dto.PlanResponse;
import com.sportify.catalog.dto.PlanStatusUpdateRequest;
import com.sportify.catalog.dto.PlanUpdateRequest;

import java.util.List;

@Deprecated
public interface MembershipPlanService {

    // ---------- Public ----------

    /** ACTIVE plans only, ordered by display order. */
    List<PlanResponse> getActivePlans();

    /** One ACTIVE plan; DRAFT, ARCHIVED or missing plans raise ResourceNotFoundException. */
    PlanResponse getActivePlan(Long id);

    // ---------- Manager ----------

    /** All plans in any status, paginated (page is 0-based, size is clamped to 1..100). */
    PageResponse<PlanResponse> getAllPlans(int page, int size);

    PlanResponse createPlan(PlanCreateRequest request, Long actorUserId);

    PlanResponse updatePlan(Long id, PlanUpdateRequest request, Long actorUserId);

    PlanResponse changeStatus(Long id, PlanStatusUpdateRequest request, Long actorUserId);
}
