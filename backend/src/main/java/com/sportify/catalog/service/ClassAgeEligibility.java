package com.sportify.catalog.service;

import com.sportify.catalog.entity.AgeGroup;
import com.sportify.catalog.entity.ClassSession;

import java.time.LocalDate;
import java.time.Period;

/**
 * Class age-group rule shared by session eligibility reads and transactional booking. Age is the member's completed
 * years on the session date; both bounds are inclusive and a null maximum means no upper bound. A session without a
 * class or a class without an age group is open to every age. An unknown date of birth is not rejected, matching the
 * Flow 5 recommendation rule ({@code TrainingGuidanceService.fitsAge}).
 */
public final class ClassAgeEligibility {
    private ClassAgeEligibility() { }

    /** The class age group in force; an inactive (retired) group restricts nothing, as in Flow 5 recommendations. */
    public static AgeGroup ageGroup(ClassSession session) {
        AgeGroup group = session == null || session.getSportClass() == null ? null : session.getSportClass().getAgeGroup();
        return group == null || Boolean.FALSE.equals(group.getIsActive()) ? null : group;
    }

    public static boolean fits(LocalDate dateOfBirth, ClassSession session) {
        AgeGroup group = ageGroup(session);
        if (group == null || dateOfBirth == null || session.getSessionDate() == null) return true;
        int age = Period.between(dateOfBirth, session.getSessionDate()).getYears();
        return (group.getMinAge() == null || age >= group.getMinAge())
                && (group.getMaxAge() == null || age <= group.getMaxAge());
    }

    /** Display label such as "Ages 16+" or "Ages 6-12"; null when the session has no age group. */
    public static String label(ClassSession session) {
        AgeGroup group = ageGroup(session);
        if (group == null || group.getMinAge() == null) return null;
        return group.getMaxAge() == null ? "Ages " + group.getMinAge() + "+"
                : "Ages " + group.getMinAge() + "-" + group.getMaxAge();
    }
}
