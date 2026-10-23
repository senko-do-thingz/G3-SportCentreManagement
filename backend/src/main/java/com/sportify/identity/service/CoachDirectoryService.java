package com.sportify.identity.service;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.dto.PublicCoachResponse;

import java.util.List;

/**
 * Coach lookup for staff forms (class form, schedule form).
 */
public interface CoachDirectoryService {

    /**
     * Active coaches ordered by name.
     *
     * @param sportId when not null, only coaches who teach this sport are returned
     */
    List<CoachOptionResponse> getActiveCoaches(Long sportId);

    /**
     * Active coaches whose profile is public, ordered by display order then name,
     * with the public profile fields (bio, avatar) for the guest Home page.
     *
     * @param sportId when not null, only coaches who teach this sport are returned
     */
    List<PublicCoachResponse> getPublicCoaches(Long sportId);
}
