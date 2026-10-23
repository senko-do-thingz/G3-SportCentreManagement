package com.sportify.identity.service;

import com.sportify.identity.dto.ManagerCoachProfileResponse;
import com.sportify.identity.dto.ManagerCoachProfileUpdateRequest;
import com.sportify.identity.entity.UserAccount;

/**
 * Manager editing of a coach's public profile (photo link, headline, bio, experience, specialties,
 * visibility on the Home page and display order).
 */
public interface ManagerCoachProfileService {

    /**
     * @throws com.sportify.core.exception.ResourceNotFoundException the user has no coach profile
     */
    ManagerCoachProfileResponse getProfile(Long userId);

    /**
     * Saves the profile and the account photo link and writes one activity log row when something changed.
     *
     * @throws com.sportify.core.exception.ResourceNotFoundException the user has no coach profile
     */
    ManagerCoachProfileResponse updateProfile(Long userId, ManagerCoachProfileUpdateRequest request, UserAccount actor);

    /**
     * Stores an uploaded JPEG, PNG or WebP photo (at most 2 MB, type detected from the file content) and points the
     * account photo link at it. Saved immediately and written to the activity log.
     *
     * @throws com.sportify.core.exception.ResourceNotFoundException the user has no coach profile
     * @throws com.sportify.core.exception.BusinessRuleException     empty, too large or not a supported image
     */
    ManagerCoachProfileResponse uploadPhoto(Long userId, byte[] data, UserAccount actor);
}
