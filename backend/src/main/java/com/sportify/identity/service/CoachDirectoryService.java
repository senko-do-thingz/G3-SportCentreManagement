package com.sportify.identity.service;

import com.sportify.identity.dto.CoachOptionResponse;

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
}
