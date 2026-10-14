package com.sportify.core.exception;

/**
 * The request conflicts with the current state of a resource (duplicate code, stale version). Mapped to HTTP 409.
 * An optional machine readable code lets clients react to one conflict without parsing the message.
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String message) {
        this(message, null);
    }

    public ConflictException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
