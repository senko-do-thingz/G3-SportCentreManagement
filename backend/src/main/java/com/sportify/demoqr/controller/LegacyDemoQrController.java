package com.sportify.demoqr.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Volatile demo tokens were never durable and are deliberately not accepted by the new API. */
@RestController
@PreAuthorize("hasRole('MEMBER')")
public class LegacyDemoQrController {
    @RequestMapping({"/api/v1/demo/qr/sessions", "/api/v1/demo/qr/sessions/**"})
    public ResponseEntity<Map<String, String>> retired() {
        return ResponseEntity.status(HttpStatus.GONE).body(Map.of("code", "SIMULATOR_REISSUE_REQUIRED",
                "message", "This demo API has retired. Start a new QR Payment (Simulation) session when available."));
    }
}
