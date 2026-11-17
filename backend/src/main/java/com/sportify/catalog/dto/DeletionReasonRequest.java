package com.sportify.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeletionReasonRequest(
        @NotBlank(message = "Deletion reason is required")
        @Size(min = 1, max = 255, message = "Deletion reason must be between 1 and 255 characters")
        String reason
) {
}
