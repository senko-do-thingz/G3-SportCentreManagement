package com.sportify.core.exception;

/**
 * A business rule was violated by an otherwise well-formed request. Mapped to HTTP 400.
 */
public class BusinessRuleException extends RuntimeException {
    private final String code;

    public BusinessRuleException(String message) {
        this(message, null, null);
    }

    public BusinessRuleException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public BusinessRuleException(String message, String code) {
        this(message, code, null);
    }

    private BusinessRuleException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
