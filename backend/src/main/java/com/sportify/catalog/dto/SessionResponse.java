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
        int capacity, long bookedCount, long freeSeats, boolean full, SessionStatus status) { }
