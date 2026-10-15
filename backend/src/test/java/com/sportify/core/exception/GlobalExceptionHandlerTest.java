package com.sportify.core.exception;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleJwtException() {
        JwtException ex = new JwtException("expired");
        ResponseEntity<Map<String, Object>> response = handler.handleJwtException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid or expired token", response.getBody().get("message"));
    }

    @Test
    void testHandleDataIntegrityViolationException() {
        org.springframework.dao.DataIntegrityViolationException ex = new org.springframework.dao.DataIntegrityViolationException("duplicate key");
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("The request conflicts with existing data", response.getBody().get("message"));
    }

    @Test
    void testHandleGlobalException() {
        Exception ex = new IllegalStateException("Internal secret message");
        ResponseEntity<Map<String, Object>> response = handler.handleGlobalException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal server error", response.getBody().get("message"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().containsValue("Internal secret message"));
    }
}
