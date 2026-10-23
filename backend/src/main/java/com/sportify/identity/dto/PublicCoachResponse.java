package com.sportify.identity.dto;

import java.util.List;

/**
 * Coach card for the public Home page. Profile fields only, no contact data.
 *
 * @param id        user id of the coach (also the coach_profile primary key)
 * @param avatarUrl user_account.avatar_url, null when the coach has no photo
 * @param sportIds  ids of the sports the coach teaches (table coach_sport)
 */
public record PublicCoachResponse(
        Long id,
        String fullName,
        String headline,
        String specialties,
        Integer yearsExperience,
        String bio,
        String avatarUrl,
        List<Long> sportIds
) {
}
