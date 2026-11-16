package com.sportify.identity.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.dto.ActivityLogDetailResponse;
import com.sportify.identity.dto.ActivityLogQuery;
import com.sportify.identity.dto.ActivityLogResponse;
import com.sportify.identity.entity.ActivityLog;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasRole('MANAGER')")
public class ActivityLogServiceImpl implements ActivityLogService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final String TIMEZONE = "Asia/Ho_Chi_Minh (UTC+7)";

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwordhash", "secret", "token", "apikey", "authorization",
            "refreshtoken", "accesstoken", "temporarypassword"
    );

    private final ActivityLogRepository logs;
    private final ObjectMapper objectMapper;

    @Override
    public PageResponse<ActivityLogResponse> list(Long actorId, String action, String from, String to,
            String search, int page, int size) {
        var query = ActivityLogQuery.parse(actorId, action, from, to, search, page, size);
        return PageResponse.from(logs.search(query));
    }

    @Override
    public ActivityLogDetailResponse getDetail(Long id) {
        ActivityLog logEntity = logs.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity log not found with id: " + id));

        Long actorId = logEntity.getActor() != null ? logEntity.getActor().getId() : null;
        String actorName = logEntity.getActor() != null && logEntity.getActor().getFullName() != null && !logEntity.getActor().getFullName().isBlank()
                ? logEntity.getActor().getFullName()
                : "System / unavailable actor";
        String actorRole = logEntity.getActor() != null && logEntity.getActor().getRole() != null
                ? logEntity.getActor().getRole().getCode()
                : null;
        String actorType = logEntity.getActor() != null ? "USER" : "SYSTEM";

        String formattedCreatedAt = logEntity.getCreatedAt() != null
                ? logEntity.getCreatedAt().format(FORMATTER)
                : null;

        String action = logEntity.getAction() != null ? logEntity.getAction() : "UNKNOWN";
        String actionName = humanizeAction(action);
        String category = categorizeAction(action);

        Map<String, Object> rawDetails = parseDetails(logEntity.getDetails());
        Map<String, Object> sanitized = sanitizeMap(rawDetails);

        String status = extractStatus(action, sanitized);
        String reason = extractReason(action, status, logEntity.getSummary(), sanitized);
        String errorCode = extractErrorCode(action, status, sanitized);
        String errorMessage = extractErrorMessage(sanitized);

        Map<String, Object> inputs = extractInputs(sanitized);
        Map<String, Object> outputs = extractOutputs(sanitized);
        Map<String, Object> metadata = extractMetadata(sanitized, inputs, outputs);

        return new ActivityLogDetailResponse(
                logEntity.getId(),
                logEntity.getCreatedAt(),
                formattedCreatedAt,
                TIMEZONE,
                actorId,
                actorName,
                actorRole,
                actorType,
                action,
                actionName,
                category,
                logEntity.getEntityType(),
                logEntity.getEntityId(),
                logEntity.getEntityCode(),
                logEntity.getSummary(),
                status,
                reason,
                errorCode,
                errorMessage,
                inputs,
                outputs,
                metadata,
                logEntity.getIpAddress()
        );
    }

    private Map<String, Object> parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(detailsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.debug("Could not parse activity log details JSON: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeMap(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            String normalizedKey = key.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            if (SENSITIVE_KEYS.contains(normalizedKey)) {
                continue;
            }
            Object val = entry.getValue();
            if (val instanceof Map) {
                result.put(key, sanitizeMap((Map<String, Object>) val));
            } else {
                result.put(key, val);
            }
        }
        return result;
    }

    private String humanizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "Activity recorded";
        }
        String[] parts = action.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
              .append(part.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String categorizeAction(String action) {
        if (action == null) return "SYSTEM_OPERATION";
        String upper = action.toUpperCase(Locale.ROOT);
        if (upper.contains("LOGIN") || upper.contains("LOGOUT") || upper.contains("PASSWORD") || upper.contains("AUTH")) {
            return "AUTHENTICATION";
        }
        if (upper.contains("USER") || upper.contains("STAFF") || upper.contains("PROFILE") || upper.contains("COACH_PROFILE")) {
            return "USER_MANAGEMENT";
        }
        if (upper.contains("MEMBER") || upper.contains("CARD")) {
            return "MEMBERSHIP";
        }
        if (upper.contains("PACKAGE") || upper.contains("PLAN")) {
            return "PACKAGE_MANAGEMENT";
        }
        if (upper.contains("CLASS") && !upper.contains("SESSION")) {
            return "CLASS_MANAGEMENT";
        }
        if (upper.contains("SESSION") || upper.contains("SCHEDULE")) {
            return "SESSION_MANAGEMENT";
        }
        if (upper.contains("CHECK_IN") || upper.contains("ATTENDANCE")) {
            return "ATTENDANCE";
        }
        if (upper.contains("PAYMENT") || upper.contains("REFUND") || upper.contains("ORDER") || upper.contains("SALE")) {
            return "FINANCE_AND_PAYMENT";
        }
        return "SYSTEM_OPERATION";
    }

    private String extractStatus(String action, Map<String, Object> sanitized) {
        if (sanitized.containsKey("status") && sanitized.get("status") != null) {
            return String.valueOf(sanitized.get("status")).trim().toUpperCase(Locale.ROOT);
        }
        if (action.endsWith("_FAILED") || action.contains("FAIL") || action.contains("REJECT")) {
            return "FAILED";
        }
        return "SUCCESS";
    }

    private String extractReason(String action, String status, String summary, Map<String, Object> sanitized) {
        if (sanitized.containsKey("reason") && sanitized.get("reason") != null) {
            return String.valueOf(sanitized.get("reason")).trim();
        }
        if (sanitized.containsKey("message") && sanitized.get("message") != null) {
            return String.valueOf(sanitized.get("message")).trim();
        }
        if (sanitized.containsKey("errorMessage") && sanitized.get("errorMessage") != null) {
            return String.valueOf(sanitized.get("errorMessage")).trim();
        }

        if ("FAILED".equalsIgnoreCase(status)) {
            if ("LOGIN_FAILED".equalsIgnoreCase(action)) {
                return "The authentication attempt failed because the supplied credentials were invalid.";
            }
            return "The operation failed or was rejected during execution.";
        }

        if (summary != null && !summary.isBlank()) {
            return summary + ".";
        }

        return switch (action.toUpperCase(Locale.ROOT)) {
            case "LOGIN" -> "User successfully authenticated and established an active session.";
            case "LOGOUT" -> "User session was terminated normally.";
            case "USER_CREATED" -> "A new user account was successfully created and provisioned in the system.";
            case "USER_STATUS_CHANGED" -> "User account operational status was modified.";
            case "PASSWORD_CHANGED" -> "Account security credentials were successfully updated.";
            case "PASSWORD_RESET_COMPLETED" -> "User account password was reset by an authorized administrator.";
            default -> "Action " + humanizeAction(action).toLowerCase(Locale.ROOT) + " executed successfully.";
        };
    }

    private String extractErrorCode(String action, String status, Map<String, Object> sanitized) {
        if (sanitized.containsKey("errorCode") && sanitized.get("errorCode") != null) {
            return String.valueOf(sanitized.get("errorCode")).trim();
        }
        if (sanitized.containsKey("error_code") && sanitized.get("error_code") != null) {
            return String.valueOf(sanitized.get("error_code")).trim();
        }
        if ("FAILED".equalsIgnoreCase(status) && "LOGIN_FAILED".equalsIgnoreCase(action)) {
            return "AUTH_INVALID_CREDENTIALS";
        }
        return null;
    }

    private String extractErrorMessage(Map<String, Object> sanitized) {
        if (sanitized.containsKey("errorMessage") && sanitized.get("errorMessage") != null) {
            return String.valueOf(sanitized.get("errorMessage")).trim();
        }
        if (sanitized.containsKey("error_message") && sanitized.get("error_message") != null) {
            return String.valueOf(sanitized.get("error_message")).trim();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractInputs(Map<String, Object> sanitized) {
        if (sanitized.containsKey("inputs") && sanitized.get("inputs") instanceof Map) {
            return (Map<String, Object>) sanitized.get("inputs");
        }
        Set<String> inputKeys = Set.of(
                "email", "role", "sportids", "from", "to", "targetstatus", "phone", "fullname",
                "name", "source", "filters", "searchquery", "amount", "date", "slotid", "classid"
        );
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : sanitized.entrySet()) {
            String norm = e.getKey().toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            if (inputKeys.contains(norm)) {
                inputs.put(e.getKey(), e.getValue());
            }
        }
        return inputs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractOutputs(Map<String, Object> sanitized) {
        if (sanitized.containsKey("outputs") && sanitized.get("outputs") instanceof Map) {
            return (Map<String, Object>) sanitized.get("outputs");
        }
        Set<String> outputKeys = Set.of(
                "userid", "membercode", "orderid", "paymentid", "status", "newstatus",
                "previousstatus", "affectedrows", "passwordreset", "createdid"
        );
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : sanitized.entrySet()) {
            String norm = e.getKey().toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            if (outputKeys.contains(norm) && !norm.equals("status")) {
                outputs.put(e.getKey(), e.getValue());
            }
        }
        return outputs;
    }

    private Map<String, Object> extractMetadata(Map<String, Object> sanitized, Map<String, Object> inputs, Map<String, Object> outputs) {
        if (sanitized.containsKey("metadata") && sanitized.get("metadata") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> explicit = (Map<String, Object>) sanitized.get("metadata");
            return explicit;
        }
        Set<String> knownTopLevel = Set.of(
                "inputs", "outputs", "metadata", "status", "reason", "message",
                "errorcode", "error_code", "errormessage", "error_message"
        );
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : sanitized.entrySet()) {
            String key = e.getKey();
            String norm = key.toLowerCase(Locale.ROOT).replaceAll("[_-]", "");
            if (!knownTopLevel.contains(norm) && !inputs.containsKey(key) && !outputs.containsKey(key)) {
                metadata.put(key, e.getValue());
            }
        }
        return metadata;
    }
}
