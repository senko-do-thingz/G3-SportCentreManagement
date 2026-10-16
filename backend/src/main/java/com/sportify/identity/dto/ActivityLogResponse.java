package com.sportify.identity.dto;

import java.time.LocalDateTime;
import java.util.Locale;

/** Scalar projection only: no credentials, contacts, IP, free-form summary or JSON metadata. */
public record ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId,
        String actorName, String actorRole, String action, String entityType, Long entityId,
        String shortDetail) {
    public ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId, String actorName,
            String actorRole, String action, String entityType, Long entityId) {
        this(id, createdAt, actorId,
                actorName == null || actorName.isBlank() ? "System / unavailable actor" : actorName,
                actorRole, action, entityType, entityId, describe(action));
    }

    private static String describe(String action) {
        if (!ActivityLogQuery.ACTIONS.contains(action == null ? "" : action)) return "Activity recorded";
        String label = action.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
