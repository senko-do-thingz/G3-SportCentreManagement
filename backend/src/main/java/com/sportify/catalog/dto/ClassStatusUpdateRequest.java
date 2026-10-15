package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Body of PATCH /api/v1/manager/classes/{id}/status.
 */
public record ClassStatusUpdateRequest(
        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
