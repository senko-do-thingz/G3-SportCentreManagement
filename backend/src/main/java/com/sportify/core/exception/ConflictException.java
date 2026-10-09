package com.sportify.core.exception;

/**
 * The request conflicts with the current state of a resource (duplicate code, stale version). Mapped to HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
