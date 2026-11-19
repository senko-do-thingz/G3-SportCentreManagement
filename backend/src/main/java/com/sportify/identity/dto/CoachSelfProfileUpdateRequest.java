package com.sportify.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Yeu cau cap nhat ho so cong khai cua huan luyen vien (khong cho phep sua isPublic hay displayOrder)
public record CoachSelfProfileUpdateRequest(
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
        String specialties
) {
}
