package com.sportify.catalog.dto;

import com.sportify.catalog.entity.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/** Teaching calendar data only; no roster contacts or private cancellation/profile metadata. */
public record CoachSessionResponse(Long id, Long classId, String className,
        Long sportId, String sportName, Long coachId, String coachName,
        Long facilityId, String facilityName, LocalDate sessionDate, LocalTime startTime,
        LocalTime endTime, Integer capacity, long bookedCount, SessionStatus status) {
    public CoachSessionResponse(Long id, Long classId, String className, Long sportId, String sportName,
            Long coachId, String coachName, Long facilityId, String facilityName, LocalDate sessionDate,
            LocalTime startTime, LocalTime endTime, Integer capacity, SessionStatus status) {
        this(id, classId, className, sportId, sportName, coachId, coachName, facilityId, facilityName,
                sessionDate, startTime, endTime, capacity, 0, status);
    }

    public CoachSessionResponse withBookedCount(long count) {
        return new CoachSessionResponse(id, classId, className, sportId, sportName, coachId, coachName,
                facilityId, facilityName, sessionDate, startTime, endTime, capacity, count, status);
    }
}
