package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PublicFacilityResponse;
import com.sportify.catalog.service.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, read-only list of active facilities for the guest Home page.
 * The manager directory at /api/v1/facilities (including inactive rows) stays manager-only.
 * Permitted for anonymous GET in SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
public class PublicFacilityController {

    private final FacilityService facilityService;

    @GetMapping("/api/v1/facilities/public")
    public ResponseEntity<List<PublicFacilityResponse>> getPublicFacilities() {
        return ResponseEntity.ok(facilityService.getPublicFacilities());
    }
}
