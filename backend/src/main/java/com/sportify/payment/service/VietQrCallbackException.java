package com.sportify.payment.service;

public class VietQrCallbackException extends RuntimeException {
    private final String errorReason;

    public VietQrCallbackException(String errorReason, String message) {
        super(message);
        this.errorReason = errorReason;
    }

    public String getErrorReason() {
        return errorReason;
    }
}
