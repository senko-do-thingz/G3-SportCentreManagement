package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CatalogMaintenanceRequest.*;
import com.sportify.catalog.service.CatalogMaintenanceService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager/catalog")
@PreAuthorize("hasRole('MANAGER') and principal.enabled")
@RequiredArgsConstructor
public class CatalogMaintenanceController {
    private final CatalogMaintenanceService service;
    @GetMapping("/sports") public List<SportView> sports() { return service.sports(); }
    @GetMapping("/facilities") public List<FacilityView> facilities() { return service.facilities(); }
    @PostMapping("/sports") @ResponseStatus(HttpStatus.CREATED)
    public SportView createSport(@Valid @RequestBody SportWrite request,@AuthenticationPrincipal CustomUserDetails actor) { return service.saveSport(null,request,actor.getUserAccount()); }
    @PutMapping("/sports/{id}")
    public SportView updateSport(@PathVariable long id,@Valid @RequestBody SportWrite request,@AuthenticationPrincipal CustomUserDetails actor) { return service.saveSport(id,request,actor.getUserAccount()); }
    @PostMapping("/facilities") @ResponseStatus(HttpStatus.CREATED)
    public FacilityView createFacility(@Valid @RequestBody FacilityWrite request,@AuthenticationPrincipal CustomUserDetails actor) { return service.saveFacility(null,request,actor.getUserAccount()); }
    @PutMapping("/facilities/{id}")
    public FacilityView updateFacility(@PathVariable long id,@Valid @RequestBody FacilityWrite request,@AuthenticationPrincipal CustomUserDetails actor) { return service.saveFacility(id,request,actor.getUserAccount()); }
}
