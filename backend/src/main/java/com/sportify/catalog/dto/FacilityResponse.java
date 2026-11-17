package com.sportify.catalog.dto;

public record FacilityResponse(Long id, String name, boolean active, Long sportId, Integer capacity) {
    public FacilityResponse(Long id, String name, boolean active) {
        this(id, name, active, null, null);
    }
}
