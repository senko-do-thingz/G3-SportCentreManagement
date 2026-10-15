package com.sportify.identity.dto;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SessionResponse;

import java.time.LocalDate;
import java.util.List;

/** Deliberately excludes account contacts, document URLs and internal audit metadata. */
public record CoachPublicProfileResponse(Long id, String name, String headline, String bio,
        Integer yearsExperience, String specialties, List<TaughtSport> sports,
        List<Certification> certifications, PageResponse<SessionResponse> upcomingSessions) {
    public record TaughtSport(Long id, String name) { }
    public record Certification(Long id, Long sportId, String name, String issuer,
            LocalDate issuedOn, LocalDate expiresOn, boolean verified) { }
}
