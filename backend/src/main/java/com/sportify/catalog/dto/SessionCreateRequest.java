package com.sportify.catalog.dto;

import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.TrainingFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/** Exactly one of date or the complete weekly recurrence fields must be supplied. */
public record SessionCreateRequest(
        LocalDate date,
        Set<DayOfWeek> daysOfWeek,
        LocalDate fromDate,
        LocalDate toDate,
        @NotNull(message = "Start time is required") LocalTime startTime,
        @NotNull(message = "End time is required") LocalTime endTime,
        @NotNull(message = "Facility is required") Long facilityId,
        Long coachId,
        @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") Integer capacity,
        @NotNull(message = "Training type is required") TrainingFormat trainingType,
        SessionStatus status
) {
}
