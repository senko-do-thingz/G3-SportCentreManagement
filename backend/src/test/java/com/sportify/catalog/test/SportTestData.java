package com.sportify.catalog.test;

import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportVenueType;

/**
 * Shared test fixture helper producing valid Sport instances with all mandatory fields populated.
 */
public final class SportTestData {

    private SportTestData() {}

    public static Sport.SportBuilder aValidSport() {
        return Sport.builder()
                .code("SWIM")
                .name("Swimming")
                .venueType(SportVenueType.POOL)
                .displayOrder(1)
                .isActive(true);
    }

    public static Sport aDefaultSport() {
        return aValidSport().build();
    }
}
