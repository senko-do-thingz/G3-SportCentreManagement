package com.sportify.core.exception;

/**
 * Requested resource does not exist (or is not visible to the caller). Mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
