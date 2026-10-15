package com.sportify.core.exception;

import java.util.Map;

/**
 * The request conflicts with the current state of a resource (duplicate code, stale version). Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    private final Map<String, ?> details;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, Map<String, ?> details) {
        super(message);
        this.details = details == null ? null : Map.copyOf(details);
    }

    public Map<String, ?> getDetails() {
        return details;
    }
}
