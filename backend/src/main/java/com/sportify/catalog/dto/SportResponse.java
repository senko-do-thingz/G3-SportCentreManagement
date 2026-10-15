package com.sportify.catalog.dto;

import com.sportify.catalog.entity.SportVenueType;

public record SportResponse(
        Long id,
        String code,
        String name,
        SportVenueType venueType,
        String description,
        String imageUrl,
        Integer displayOrder
) {
}
