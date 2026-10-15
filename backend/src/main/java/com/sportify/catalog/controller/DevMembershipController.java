package com.sportify.catalog.controller;

import com.sportify.catalog.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dev/memberships")
@RequiredArgsConstructor
@Profile("dev")
public class DevMembershipController {

    private final MembershipService membershipService;

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.OK)
    public void activateMembership(@PathVariable Long id, @org.springframework.security.core.annotation.AuthenticationPrincipal com.sportify.identity.security.CustomUserDetails userDetails) {
        // This is a temporary endpoint for Step 2B, to be replaced by Payment step
        membershipService.activateMembership(id, userDetails.getUserAccount());
    }
}
