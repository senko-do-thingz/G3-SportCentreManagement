package com.sportify.catalog.controller;

import com.sportify.catalog.dto.FacilityResponse;
import com.sportify.catalog.service.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/facilities")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class FacilityController {
    private final FacilityService facilityService;

    @GetMapping
    public ResponseEntity<List<FacilityResponse>> getFacilities() {
        return ResponseEntity.ok(facilityService.getFacilities());
    }
}
