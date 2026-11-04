package com.sportify.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionUpdateRequest(
        @NotNull(message = "Date is required") LocalDate date,
        @NotNull(message = "Start time is required") LocalTime startTime,
        @NotNull(message = "End time is required") LocalTime endTime,
        @NotNull(message = "Facility is required") Long facilityId,
        Long coachId,
        @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") Integer capacity,
        @PositiveOrZero Long expectedRevision,
        @Size(max = 500) String rescheduleReason
) {
    /** Preserve the existing same-date Java and JSON update contract. */
    public SessionUpdateRequest(LocalDate date, LocalTime startTime, LocalTime endTime,
            Long facilityId, Long coachId, Integer capacity) {
        this(date, startTime, endTime, facilityId, coachId, capacity, null, null);
    }
}
