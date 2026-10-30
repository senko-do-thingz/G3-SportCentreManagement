package com.sportify.demoqr.controller;

import com.sportify.demoqr.service.DemoQrException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.core.MethodParameter;

import java.util.Map;

/** Never echo rejected token/body values, even through validation error messages. */
@RestControllerAdvice(assignableTypes = {DemoQrController.class, LegacyDemoQrController.class, SimulatorCapabilityController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DemoQrAdvice implements ResponseBodyAdvice<Object> {
    @ExceptionHandler(DemoQrException.class)
    public ResponseEntity<Map<String, String>> demoError(DemoQrException ex) {
        return ResponseEntity.status(ex.status()).header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").body(Map.of("code", ex.code(), "message", ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidBody(Exception ignored) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").body(Map.of("code", "SIMULATOR_INVALID_REQUEST", "message", "Invalid simulation request."));
    }

    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public ResponseEntity<Map<String, String>> databaseUnavailable(Exception ignored) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer").body(Map.of("code", "SIMULATOR_RETRY",
                "message", "Simulation state unavailable. Refresh before retrying; payment was not settled."));
    }

    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
                                  Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request,
                                  ServerHttpResponse response) {
        response.getHeaders().set("Cache-Control", "no-store");
        response.getHeaders().set("Referrer-Policy", "no-referrer");
        return body;
    }
}
