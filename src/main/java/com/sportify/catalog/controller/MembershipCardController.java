package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/membership-cards")
@RequiredArgsConstructor
public class MembershipCardController {

    private final MembershipCardService membershipCardService;

    @GetMapping("/tiers")
    public List<CardTierResponse> getAllActiveTiers() {
        return membershipCardService.getAllActiveTiers();
    }

    @PostMapping("/purchase")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberCardResponse purchaseCard(
            @Valid @RequestBody MemberCardPurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return membershipCardService.purchaseCard(request, userDetails.getUserAccount());
    }

    /** The front desk confirms the payment of a card request: PENDING_PAYMENT -> ACTIVE. */
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public MemberCardResponse activateCard(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return membershipCardService.activateCard(id, userDetails.getUserAccount());
    }

    /** Cancels a card request that has not been paid yet. A member may cancel only their own request. */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    public MemberCardResponse cancelCard(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return membershipCardService.cancelCard(id, userDetails.getUserAccount());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('MEMBER')")
    public List<MemberCardResponse> getMyCards(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return membershipCardService.getMyCards(userDetails.getUserAccount());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public List<MemberCardResponse> getMemberCards(@PathVariable Long memberId) {
        return membershipCardService.getMemberCards(memberId);
    }
}
