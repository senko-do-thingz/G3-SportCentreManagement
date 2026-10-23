package com.sportify.identity.dto;

/**
 * Coach profile as edited by a manager: the fields shown on the public Home coach cards.
 */
public record ManagerCoachProfileResponse(
        Long userId,
        String fullName,
        String avatarUrl,
        String headline,
        String bio,
        Integer yearsExperience,
        String specialties,
        boolean isPublic,
        int displayOrder
) {
}
