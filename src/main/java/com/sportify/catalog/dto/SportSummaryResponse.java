package com.sportify.catalog.dto;

/**
 * Compact sport reference used inside plan responses (eligible sport pool).
 */
public record SportSummaryResponse(
        Long id,
        String code,
        String name
) {
}
