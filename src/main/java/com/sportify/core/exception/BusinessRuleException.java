package com.sportify.core.exception;

/**
 * A business rule was violated by an otherwise well-formed request. Mapped to HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
