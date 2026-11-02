package com.sportify.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class TrainingWriteRequest {
    private TrainingWriteRequest() {}
    public enum AttendanceStatus { PRESENT, ABSENT, LATE, EXCUSED }
    public record Step(@NotBlank @Size(max = 150) String title,
                       @Min(1) @Max(1440) int durationMinutes,
                       @Size(max = 1000) String instructions) {}
    public record Plan(@NotNull @Positive Long classId, @NotNull @Positive Long memberId, @NotNull @Positive Long bookingId,
                       @NotBlank @Size(max = 150) String title, @Size(max = 1000) String description,
                       @NotEmpty @Size(max = 100) List<@NotNull @Valid Step> steps) {}
    public record Feedback(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 1000) String comments,
                           @Size(max = 500) String strengths, @Size(max = 500) String areasForImprovement) {}
    public record Score(@NotNull @Positive Long metricId, @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal score) {}
    public record Result(@NotNull @Positive Long sessionId, @NotNull @Positive Long memberId,
                         @Size(max = 1000) String notes,
                         @NotNull @Size(max = 100) List<@NotNull @Valid Score> scores) {}
    public record Attendance(@NotNull @Positive Long bookingId, @NotNull AttendanceStatus status,
                             @Size(max = 500) String notes) {}
    public record Correction(@NotNull AttendanceStatus status, @Size(max = 500) String notes,
                             @NotBlank @Size(max = 500) String reason) {}
}
