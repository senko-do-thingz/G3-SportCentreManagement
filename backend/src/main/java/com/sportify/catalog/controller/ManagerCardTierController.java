package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MembershipCardTierCreateRequest;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import com.sportify.catalog.service.MembershipCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

/**
 * Manager workspace "Membership Cards & Benefits" (screen F1-03).
 */
@RestController
@RequestMapping("/api/v1/manager/card-tiers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerCardTierController {

    private final MembershipCardService membershipCardService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CardTierResponse create(@Valid @RequestBody MembershipCardTierCreateRequest request,
                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return membershipCardService.createTier(request, actor.getUserAccount());
    }

    @GetMapping
    public List<CardTierResponse> getAllCardTiers() {
        return membershipCardService.getAllTiersForManager();
    }
}
