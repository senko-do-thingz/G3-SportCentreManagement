package com.sportify.catalog.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record SessionConflictValidationRequest(
        List<LocalDate> dates,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        Long coachId,
        Long facilityId,
        Long excludedSessionId
) {
}
