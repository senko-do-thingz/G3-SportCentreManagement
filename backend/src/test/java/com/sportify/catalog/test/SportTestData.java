package com.sportify.catalog.test;

import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportVenueType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared test fixture helper producing valid Sport instances with all mandatory fields populated.
 */
public final class SportTestData {

    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    private SportTestData() {}

    public static Sport.SportBuilder aValidSport() {
        long seq = SEQUENCE.incrementAndGet();
        return Sport.builder()
                .code("SPORT_" + seq)
                .name("Sport " + seq)
                .venueType(SportVenueType.POOL)
                .displayOrder(1)
                .isActive(true);
    }

    public static Sport aDefaultSport() {
        return aValidSport().build();
    }
}
