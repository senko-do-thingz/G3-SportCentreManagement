package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MembershipCardTierUpdateRequest;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Manager workspace "Membership Cards & Benefits" (screen F1-03 and overlay "Edit Membership Card").
 */
@RestController
@RequestMapping("/api/v1/manager/card-tiers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerCardTierController {

    private final MembershipCardService membershipCardService;

    @GetMapping
    public List<CardTierResponse> getAllCardTiers() {
        return membershipCardService.getAllTiersForManager();
    }

    @PutMapping("/{id}")
    public CardTierResponse updateTier(
            @PathVariable Long id,
            @Valid @RequestBody MembershipCardTierUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return membershipCardService.updateTier(id, request, userDetails != null ? userDetails.getUserAccount() : null);
    }
}
