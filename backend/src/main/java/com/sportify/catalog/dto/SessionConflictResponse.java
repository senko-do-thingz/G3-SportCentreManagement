package com.sportify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionConflictResponse(
        Long sessionId, LocalDate requestedDate, LocalDate date, LocalTime startTime, LocalTime endTime,
        @JsonProperty("class") ManagerSessionResponse.ClassSummary sportClass,
        ManagerSessionResponse.ResourceSummary coach, ManagerSessionResponse.ResourceSummary facility,
        ConflictType conflictType
) {
    public enum ConflictType { COACH, FACILITY, BOTH }
}
