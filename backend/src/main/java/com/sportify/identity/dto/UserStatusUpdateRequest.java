package com.sportify.identity.dto;

import com.sportify.identity.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Change the status of an account: ACTIVE or INACTIVE.
 */
public record UserStatusUpdateRequest(
        @NotNull(message = "Status is required")
        UserStatus status
) {
}
