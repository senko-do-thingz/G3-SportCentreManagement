package com.sportify.catalog.controller;

import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.dto.SportPackageUpdateRequest;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

/**
 * Manager workspace "Sport Packages & Pricing" (screen F1-02 and overlay "Edit Sport Package").
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
            @RequestParam(required = false) String format,
            @RequestParam(required = false) Boolean active) {
        TrainingFormat trainingFormat = parseFormat(format);
        return sportPackageService.getManagerPackages(sportId, trainingFormat, active);
    }

    @PutMapping("/{id}")
    public SportPackageResponse updatePackage(
            @PathVariable Long id,
            @Valid @RequestBody SportPackageUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sportPackageService.updatePackage(id, request, userDetails != null ? userDetails.getUserAccount() : null);
    }

    private TrainingFormat parseFormat(String format) {
        if (format == null || format.trim().isEmpty()) {
            return null;
        }
        try {
            return TrainingFormat.valueOf(format.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid training format: " + format);
        }
    }
}
