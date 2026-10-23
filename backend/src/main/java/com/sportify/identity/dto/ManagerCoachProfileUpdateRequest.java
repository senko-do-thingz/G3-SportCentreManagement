package com.sportify.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Manager update of a coach's public profile. Blank text fields are stored as null.
 * {@code avatarUrl} is either an http or https link, or the link of a photo uploaded through
 * POST /api/v1/manager/users/{id}/coach-profile/photo (/api/v1/users/{id}/avatar?v=...), or a demo photo shipped with
 * the web app (/images/coaches/name.jpg). Empty removes the photo.
 */
public record ManagerCoachProfileUpdateRequest(
        @Size(max = 500, message = "Photo link must be at most 500 characters")
        @Pattern(regexp = "^\\s*$|^https?://\\S+$|^/api/v1/users/\\d+/avatar(\\?v=\\d+)?$|^/images/coaches/[a-z0-9-]+\\.(jpe?g|png|webp)$", message = "Photo link must be an http(s) link, an uploaded photo or a demo photo path")
        String avatarUrl,

        @Size(max = 150, message = "Headline must be at most 150 characters")
        String headline,

        @Size(max = 1000, message = "Bio must be at most 1000 characters")
        String bio,

        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 60, message = "Years of experience must be at most 60")
        Integer yearsExperience,

        @Size(max = 255, message = "Specialties must be at most 255 characters")
        String specialties,

        @NotNull(message = "Show on Home is required")
        Boolean isPublic,

        @NotNull(message = "Display order is required")
        @Min(value = 0, message = "Display order cannot be negative")
        @Max(value = 999, message = "Display order must be at most 999")
        Integer displayOrder
) {
}
