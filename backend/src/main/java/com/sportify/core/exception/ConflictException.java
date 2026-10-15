package com.sportify.core.exception;

import java.util.Map;

/**
 * The request conflicts with the current state of a resource (duplicate code, stale version). Mapped to HTTP 409.
 * An optional machine readable code lets clients react to one conflict without parsing the message, and optional
 * details describe the conflicting resources.
 */
public class ConflictException extends RuntimeException {

    private final String code;
    private final Map<String, ?> details;

    public ConflictException(String message) {
        this(message, null, null);
    }

    public ConflictException(String message, String code) {
        this(message, code, null);
    }

    public ConflictException(String message, Map<String, ?> details) {
        this(message, null, details);
    }

    public ConflictException(String message, String code, Map<String, ?> details) {
        super(message);
        this.code = code;
        this.details = details == null ? null : Map.copyOf(details);
    }

    public String getCode() {
        return code;
    }

    public Map<String, ?> getDetails() {
        return details;
    }
}
