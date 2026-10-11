package com.sportify.catalog.dto;

import java.time.LocalDateTime;

/**
 * One class as shown on F2-01 "Class Management" and loaded into F2-02 "Create / Edit Class".
 *
 * @param upcomingSessions number of DRAFT or PUBLISHED sessions of the class that have not started yet
 */
public record ManagerClassResponse(
        Long id,
        String code,
        String name,
        Long sportId,
        String sportName,
        Long ageGroupId,
        String ageGroupLabel,
        String level,
        Long coachId,
        String coachName,
        Integer maxMembers,
        long upcomingSessions,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
