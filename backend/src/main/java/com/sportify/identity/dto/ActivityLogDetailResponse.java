package com.sportify.identity.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Detailed audit event projection for the Manager Activity Log detail modal.
 * Contains verified safe fields, actor information, request inputs, execution status,
 * resulting values, and verified explanatory reasons.
 * Sensitive fields (passwords, tokens, API keys) are strictly excluded.
 */
public record ActivityLogDetailResponse(
        Long id,
        LocalDateTime createdAt,
        String formattedCreatedAt,
        String timezone,
        Long actorId,
        String actorName,
        String actorRole,
        String actorType,
        String action,
        String actionName,
        String category,
        String entityType,
        Long entityId,
        String entityCode,
        String summary,
        String status,
        String reason,
        String errorCode,
        String errorMessage,
        Map<String, Object> inputs,
        Map<String, Object> outputs,
        Map<String, Object> metadata,
        String ipAddress
) {}
