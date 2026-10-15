package com.sportify.catalog.controller;

import com.sportify.catalog.dto.FrontDeskCardResponse;
import com.sportify.catalog.dto.FrontDeskPackageResponse;
import com.sportify.catalog.service.FrontDeskQueueService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
public class FrontDeskQueueController {
    private final FrontDeskQueueService service;

    @GetMapping("/api/v1/membership-cards/pending")
    public Page<FrontDeskCardResponse> pendingCards(@PageableDefault(size = 20) Pageable pageable) {
        return service.pendingCards(pageable);
    }

    @GetMapping("/api/v1/packages/registrations/pending")
    public Page<FrontDeskPackageResponse> pendingPackages(@PageableDefault(size = 20) Pageable pageable) {
        return service.pendingPackages(pageable);
    }
}
