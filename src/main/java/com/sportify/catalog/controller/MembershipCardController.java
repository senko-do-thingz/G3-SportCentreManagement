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
