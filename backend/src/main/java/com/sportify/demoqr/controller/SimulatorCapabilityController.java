package com.sportify.demoqr.controller;

import com.sportify.demoqr.service.DemoQrService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@PreAuthorize("hasRole('MEMBER')")
public class SimulatorCapabilityController {
    private final ObjectProvider<DemoQrService> service;

    public SimulatorCapabilityController(ObjectProvider<DemoQrService> service) { this.service = service; }

    @GetMapping({"/api/v1/payments/simulated-qr/capability", "/api/v1/payments/simulator-config"})
    public Map<String, Object> capability() {
        boolean active = service.getIfAvailable() != null;
        return Map.of("enabled", active, "settlementEnabled", active, "contractVersion", 1);
    }
}
