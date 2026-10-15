package com.sportify.catalog.dto;

import com.sportify.catalog.entity.PlanStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Change plan status (DRAFT, ACTIVE, ARCHIVED).
 * {@code version} is optional: when present it must match the current version, otherwise 409.
 */
public record PlanStatusUpdateRequest(
        @NotNull(message = "Status is required")
        PlanStatus status,

        Integer version
) {
}
