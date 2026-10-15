package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SessionCancelRequest(
        @NotBlank(message = "Cancellation reason is required")
        @Size(max = 255, message = "Cancellation reason must be at most 255 characters") String reason
) {
    public SessionCancelRequest {
        reason = reason == null ? null : reason.trim();
    }
}
