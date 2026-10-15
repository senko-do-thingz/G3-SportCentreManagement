package com.sportify.catalog.controller;

import com.sportify.catalog.dto.AgeGroupResponse;
import com.sportify.catalog.service.AgeGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Age group dropdown for staff forms (F2-02 "Age group").
 */
@RestController
@RequestMapping("/api/v1/age-groups")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
public class AgeGroupController {

    private final AgeGroupService ageGroupService;

    @GetMapping
    public ResponseEntity<List<AgeGroupResponse>> getAgeGroups() {
        return ResponseEntity.ok(ageGroupService.getActiveAgeGroups());
    }
}
