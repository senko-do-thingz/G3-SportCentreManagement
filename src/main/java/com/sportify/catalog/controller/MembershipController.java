package com.sportify.catalog.controller;

import com.sportify.catalog.dto.MembershipRegistrationRequest;
import com.sportify.catalog.dto.MembershipResponse;
import com.sportify.catalog.service.MembershipService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @PostMapping("/memberships")
    @PreAuthorize("hasRole('MEMBER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse registerMembership(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MembershipRegistrationRequest request) {
        return membershipService.registerMembership(userDetails.getUserAccount(), request);
    }

    @PostMapping("/memberships/{id}/cancel")
    @PreAuthorize("hasAnyRole('MEMBER','RECEPTIONIST','MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelMembership(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        membershipService.cancelMembership(id, userDetails.getUserAccount());
    }

    @GetMapping("/memberships/me")
    @PreAuthorize("hasRole('MEMBER')")
    public List<MembershipResponse> getMyMemberships(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return membershipService.getMyMemberships(userDetails.getUserAccount());
    }

    @PostMapping("/memberships/register-for-member")
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipResponse registerForMember(
            @RequestParam Long memberId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MembershipRegistrationRequest request) {
        return membershipService.registerForMember(memberId, userDetails.getUserAccount(), request);
    }
}
