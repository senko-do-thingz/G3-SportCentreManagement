package com.sportify.catalog.service;

import com.sportify.catalog.entity.AgeGroup;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.SportClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ClassAgeEligibilityTest {
    private static final LocalDate SESSION_DATE = LocalDate.of(2026, 10, 6);

    private static ClassSession session(Integer min, Integer max) {
        AgeGroup group = min == null ? null : AgeGroup.builder().id(1L).code("G").label("G").minAge(min).maxAge(max).build();
        return ClassSession.builder().id(1L).sessionDate(SESSION_DATE)
                .sportClass(SportClass.builder().id(2L).ageGroup(group).build()).build();
    }

    @ParameterizedTest
    @CsvSource({
            // Age counts completed years on the session date; both bounds are inclusive.
            "2010-10-06,true",   // 16th birthday on the session date
            "2010-10-07,false",  // turns 16 the day after
            "1950-01-01,true"    // no upper bound
    })
    void minimumOnlyGroupUsesCompletedYearsOnTheSessionDate(LocalDate dateOfBirth, boolean fits) {
        assertEquals(fits, ClassAgeEligibility.fits(dateOfBirth, session(16, null)));
    }

    @ParameterizedTest
    @CsvSource({
            "2020-10-06,true",   // exactly 6
            "2020-10-07,false",  // still 5
            "2013-10-07,true",   // still 12
            "2013-10-06,false"   // 13 on the session date
    })
    void boundedGroupIsInclusiveAtBothEnds(LocalDate dateOfBirth, boolean fits) {
        assertEquals(fits, ClassAgeEligibility.fits(dateOfBirth, session(6, 12)));
    }

    @Test
    void unknownBirthDateNoClassOrNoAgeGroupAreNotRestricted() {
        assertTrue(ClassAgeEligibility.fits(null, session(16, null)));
        assertTrue(ClassAgeEligibility.fits(LocalDate.of(2020, 1, 1), session(null, null)));
        assertTrue(ClassAgeEligibility.fits(LocalDate.of(2020, 1, 1),
                ClassSession.builder().id(1L).sessionDate(SESSION_DATE).build()));
    }

    @Test
    void inactiveAgeGroupRestrictsNothingLikeFlow5() {
        ClassSession session = session(16, null);
        session.getSportClass().getAgeGroup().setIsActive(false);
        assertTrue(ClassAgeEligibility.fits(LocalDate.of(2020, 1, 1), session));
        assertNull(ClassAgeEligibility.label(session));
    }

    @Test
    void labelMatchesTheDesignCopy() {
        assertEquals("Ages 16+", ClassAgeEligibility.label(session(16, null)));
        assertEquals("Ages 6-12", ClassAgeEligibility.label(session(6, 12)));
        assertNull(ClassAgeEligibility.label(session(null, null)));
    }
}
