package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.service.MembershipCardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping
    public List<CardTierResponse> getAllCardTiers() {
        return membershipCardService.getAllTiersForManager();
    }
}
