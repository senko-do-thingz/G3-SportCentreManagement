package com.sportify.identity.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Safe Manager audit projection: raw summary, IP and arbitrary JSON metadata are never sent to clients.
 * Only the known ACTIVE/INACTIVE transition for USER_STATUS_CHANGED is allowed through.
 */
public record ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId,
        String actorName, String actorRole, String action, String entityType, Long entityId,
        String shortDetail, String previousStatus, String newStatus) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId, String actorName,
            String actorRole, String action, String entityType, Long entityId) {
        this(id, createdAt, actorId, displayActor(actorName), actorRole, action, entityType,
                entityId, describe(action), null, null);
    }

    public ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId, String actorName,
            String actorRole, String action, String entityType, Long entityId, String shortDetail) {
        this(id, createdAt, actorId, displayActor(actorName), actorRole, action, entityType,
                entityId, shortDetail, null, null);
    }

    /** Called by the database projection. Summary is deliberately ignored because it may contain private data. */
    public ActivityLogResponse(Long id, LocalDateTime createdAt, Long actorId, String actorName,
            String actorRole, String action, String entityType, Long entityId, String summary, String details) {
        this(id, createdAt, actorId, displayActor(actorName), actorRole, action, entityType,
                entityId, describe(action), statusValue(action, details, "from"),
                statusValue(action, details, "to"));
    }

    private static String displayActor(String name) {
        return name == null || name.isBlank() ? "System / unavailable actor" : name;
    }

    private static String statusValue(String action, String details, String field) {
        if (!"USER_STATUS_CHANGED".equals(action) || details == null || details.length() > 4096) return null;
        try {
            var value = JSON.readTree(details).path(field);
            String status = value.isTextual() ? value.asText() : "";
            return "ACTIVE".equals(status) || "INACTIVE".equals(status) ? status : null;
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String describe(String action) {
        if (!ActivityLogQuery.ACTIONS.contains(action == null ? "" : action)) return "Activity recorded";
        String label = action.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }
}
