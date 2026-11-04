package com.sportify.catalog.dto;

import jakarta.validation.constraints.*;

public record AttendanceBackfillRequest(
    @NotNull @Positive Long bookingId,
    @NotNull TrainingWriteRequest.AttendanceStatus status,
    @NotBlank @Size(max = 500) String reason,
    @Size(max = 500) String notes
) {}
