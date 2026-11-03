package com.sportify.catalog.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.TrainingFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ManagerSessionResponse(
        Long id, LocalDate date, LocalTime startTime, LocalTime endTime,
        @JsonProperty("class") ClassSummary sportClass,
        ResourceSummary coach, ResourceSummary facility,
        Integer capacity, long bookedCount, TrainingFormat trainingType, SessionStatus status,
        String cancelReason, LocalDateTime cancelledAt, Long cancelledByUserId
, String sessionId, String sportName) {
    /** Transitional Java constructor; live mappers supply the persisted public identity. */
    public ManagerSessionResponse(
        Long id, LocalDate date, LocalTime startTime, LocalTime endTime,
        @JsonProperty("class") ClassSummary sportClass,
        ResourceSummary coach, ResourceSummary facility,
        Integer capacity, long bookedCount, TrainingFormat trainingType, SessionStatus status,
        String cancelReason, LocalDateTime cancelledAt, Long cancelledByUserId
) {
        this(id, date, startTime, endTime, sportClass, coach, facility, capacity, bookedCount, trainingType, status, cancelReason, cancelledAt, cancelledByUserId, null, null);
    }

    public record ClassSummary(Long id, String code, String name) { }
    public record ResourceSummary(Long id, String name) { }
}
