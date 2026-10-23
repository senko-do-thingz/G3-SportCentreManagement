package com.sportify.catalog.dto;

import com.sportify.catalog.entity.FacilityType;

/**
 * Public facility row for the guest Home page ("Where you play"): name, type, sport and capacity only.
 * {@code sportId} is null for a multi-purpose facility.
 */
public record PublicFacilityResponse(Long id, String name, FacilityType facilityType, Long sportId, Integer capacity) {
}
