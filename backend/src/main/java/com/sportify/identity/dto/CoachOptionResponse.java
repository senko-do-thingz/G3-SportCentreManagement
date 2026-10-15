package com.sportify.identity.dto;

import java.util.List;

/**
 * Coach option for the class form dropdown and the "Coach qualification" panel of F2-02.
 *
 * @param id       user id of the coach (also the coach_profile primary key)
 * @param sportIds ids of the sports the coach is able to teach (table coach_sport)
 */
public record CoachOptionResponse(
        Long id,
        String fullName,
        String headline,
        String specialties,
        Integer yearsExperience,
        List<Long> sportIds
) {
}
