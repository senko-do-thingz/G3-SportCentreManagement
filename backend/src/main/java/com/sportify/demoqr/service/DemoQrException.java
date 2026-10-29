package com.sportify.demoqr.service;

import org.springframework.http.HttpStatus;

public final class DemoQrException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public DemoQrException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }

    public static DemoQrException unavailable() {
        return new DemoQrException(HttpStatus.NOT_FOUND, "DEMO_UNAVAILABLE", "Demo session or eligible payment unavailable.");
    }
}
