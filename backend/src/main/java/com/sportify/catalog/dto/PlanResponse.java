package com.sportify.catalog.dto;

import com.sportify.catalog.entity.PlanStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Membership plan with its feature bullets and eligible sport pool.
 * {@code version} is returned so that clients can send it back on update for optimistic locking.
 */
public record PlanResponse(
        Long id,
        String code,
        String name,
        String tagline,
        String description,
        BigDecimal price,
        Integer durationDays,
        Integer maxSports,
        Boolean isFeatured,
        PlanStatus status,
        Integer displayOrder,
        List<String> features,
        List<SportSummaryResponse> eligibleSports,
        Integer version
) {
}
