package com.sportify.catalog.controller;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.catalog.dto.SportPackageCreateRequest;
import com.sportify.catalog.dto.SportPackageResponse;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/packages")
@RequiredArgsConstructor
public class SportPackageController {

    private final SportPackageService sportPackageService;

    @GetMapping
    public List<SportPackageResponse> getAllActivePackages() {
        return sportPackageService.getAllActivePackages();
    }

    @GetMapping("/sport/{sportId}")
    public List<SportPackageResponse> getPackagesBySport(@PathVariable Long sportId) {
        return sportPackageService.getPackagesBySport(sportId);
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public SportPackageResponse createPackage(
            @Valid @RequestBody SportPackageCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sportPackageService.createPackage(request, userDetails.getUserAccount());
    }

    @PostMapping("/registrations")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public PackageRegistrationResponse registerPackage(
            @Valid @RequestBody PackageRegistrationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sportPackageService.registerPackage(request, userDetails.getUserAccount());
    }

    @GetMapping("/registrations/my")
    @PreAuthorize("hasRole('MEMBER')")
    public List<PackageRegistrationResponse> getMyRegistrations(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sportPackageService.getMyRegistrations(userDetails.getUserAccount());
    }

    @GetMapping("/registrations/member/{memberId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public List<PackageRegistrationResponse> getMemberRegistrations(@PathVariable Long memberId) {
        return sportPackageService.getMemberRegistrations(memberId);
    }

    @PutMapping("/registrations/{id}/activate")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public PackageRegistrationResponse activateRegistration(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return sportPackageService.activateRegistration(id, userDetails.getUserAccount());
    }
}
