package com.sportify.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Full replacement (PUT) of the editable fields of a membership plan.
 * {@code code} and {@code status} are not editable here (status uses PATCH /status).
 * Nullable columns (tagline, description) are cleared when sent as null.
 * {@code version} is optional: when present it must match the current version, otherwise 409.
 */
public record PlanUpdateRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 100, message = "Tagline must be at most 100 characters")
        String tagline,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must be greater than or equal to 0")
        @Digits(integer = 12, fraction = 2, message = "Price must have at most 12 integer digits and 2 decimals")
        BigDecimal price,

        @NotNull(message = "Duration (days) is required")
        @Min(value = 1, message = "Duration (days) must be greater than 0")
        Integer durationDays,

        @NotNull(message = "Max sports is required")
        @Min(value = 1, message = "Max sports must be at least 1")
        Integer maxSports,

        @NotNull(message = "Featured flag is required")
        Boolean isFeatured,

        @NotNull(message = "Display order is required")
        Integer displayOrder,

        @NotNull(message = "Features are required (use an empty list for none)")
        List<@NotBlank(message = "Feature text must not be blank")
             @Size(max = 150, message = "Feature text must be at most 150 characters") String> features,

        @NotNull(message = "Eligible sport ids are required (use an empty list for none)")
        Set<@NotNull(message = "Sport id must not be null") Long> eligibleSportIds,

        Integer version
) {
}
