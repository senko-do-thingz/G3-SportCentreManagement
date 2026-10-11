package com.sportify.catalog.dto;

/**
 * Age group option for the class form dropdown.
 *
 * @param maxAge inclusive upper bound, or null when there is no upper bound
 */
public record AgeGroupResponse(
        Long id,
        String code,
        String label,
        Integer minAge,
        Integer maxAge
) {
}
