package com.sportify.catalog.controller;

import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.service.SportPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Manager workspace "Sport Packages & Pricing" (screen F1-02).
 */
@RestController
@RequestMapping("/api/v1/manager/packages")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerPackageController {

    private final SportPackageService sportPackageService;

    @GetMapping
    public List<SportPackageResponse> listPackages(
            @RequestParam(required = false) Long sportId,
            @RequestParam(required = false) TrainingFormat format,
            @RequestParam(required = false) Boolean active) {
        return sportPackageService.getManagerPackages(sportId, format, active);
    }
}
