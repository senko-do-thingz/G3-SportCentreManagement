package com.sportify.core.exception;

import java.util.Map;

/** Business validation with the same field details as Bean Validation errors. */
public class FieldValidationException extends RuntimeException {
    private final Map<String, String> details;

    public FieldValidationException(String field, String message) {
        super("Validation Error");
        details = Map.of(field, message);
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
