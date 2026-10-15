package com.sportify.catalog.controller;

import com.sportify.catalog.dto.MemberOverviewResponse;
import com.sportify.catalog.service.FrontDeskMemberOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FrontDeskMemberOverviewController {
    private final FrontDeskMemberOverviewService service;

    @GetMapping("/api/v1/members/{id}/overview")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public MemberOverviewResponse overview(@PathVariable Long id) {
        return service.overview(id);
    }
}
