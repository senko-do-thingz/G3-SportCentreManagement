package com.sportify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionConflictResponse(
        Long sessionId, LocalDate requestedDate, LocalDate date, LocalTime startTime, LocalTime endTime,
        @JsonProperty("class") ManagerSessionResponse.ClassSummary sportClass,
        ManagerSessionResponse.ResourceSummary coach, ManagerSessionResponse.ResourceSummary facility,
        ConflictType conflictType
, String sessionCode) {
    /** Transitional Java constructor; live mappers supply the persisted public identity. */
    public SessionConflictResponse(
        Long sessionId, LocalDate requestedDate, LocalDate date, LocalTime startTime, LocalTime endTime,
        @JsonProperty("class") ManagerSessionResponse.ClassSummary sportClass,
        ManagerSessionResponse.ResourceSummary coach, ManagerSessionResponse.ResourceSummary facility,
        ConflictType conflictType
) {
        this(sessionId, requestedDate, date, startTime, endTime, sportClass, coach, facility, conflictType, null);
    }

    public enum ConflictType { COACH, FACILITY, BOTH }
}
