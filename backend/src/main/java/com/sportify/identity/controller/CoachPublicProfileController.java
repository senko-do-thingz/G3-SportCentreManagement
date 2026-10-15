package com.sportify.identity.controller;

import com.sportify.identity.dto.CoachPublicProfileResponse;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.CoachPublicProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Separate from the existing staff-only coach dropdown contract. */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
public class CoachPublicProfileController {
    private final CoachPublicProfileService service;

    @GetMapping("/api/v1/coaches/{id}")
    public CoachPublicProfileResponse getProfile(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return service.getProfile(id, principal == null ? null : principal.getUserAccount());
    }
}
