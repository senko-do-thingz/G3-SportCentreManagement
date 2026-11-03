package com.sportify.catalog.dto;

import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.TrainingFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/** Member-facing session data only, without cancellation or account metadata. */
public record SessionResponse(Long id, Long sportId, String sportName,
        Long classId, String className, TrainingFormat trainingFormat, String level,
        Long coachId, String coachName, Long facilityId, String facilityName,
        LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
        int capacity, long bookedCount, long freeSeats, boolean full, SessionStatus status, String sessionId) {
    /** Transitional Java constructor; live mappers supply the persisted public identity. */
    public SessionResponse(Long id, Long sportId, String sportName,
        Long classId, String className, TrainingFormat trainingFormat, String level,
        Long coachId, String coachName, Long facilityId, String facilityName,
        LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
        int capacity, long bookedCount, long freeSeats, boolean full, SessionStatus status) {
        this(id, sportId, sportName, classId, className, trainingFormat, level, coachId, coachName, facilityId, facilityName, sessionDate, startTime, endTime, capacity, bookedCount, freeSeats, full, status, null);
    }
 }
