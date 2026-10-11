package com.sportify.catalog.dto;

import com.sportify.catalog.entity.ClassLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST and PUT /api/v1/manager/classes (screen F2-02 "Create / Edit Class").
 * The class code is never accepted from the client; it is generated from seq_class_code.
 */
public record SportClassRequest(
        @NotBlank(message = "Class name is required")
        @Size(max = 100, message = "Class name must be at most 100 characters")
        String name,

        @NotNull(message = "Sport is required")
        Long sportId,

        @NotNull(message = "Age group is required")
        Long ageGroupId,

        @NotNull(message = "Level is required")
        ClassLevel level,

        @NotNull(message = "Maximum members is required")
        @Min(value = 1, message = "Maximum members must be at least 1")
        @Max(value = 100, message = "Maximum members must be at most 100")
        Integer maxMembers,

        @NotNull(message = "Assigned coach is required")
        Long coachId
) {
}
