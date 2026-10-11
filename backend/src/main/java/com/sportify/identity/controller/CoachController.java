package com.sportify.identity.controller;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.service.CoachDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Coach dropdown for staff forms (F2-02 "Assigned coach").
 */
@RestController
@RequestMapping("/api/v1/coaches")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
public class CoachController {

    private final CoachDirectoryService coachDirectoryService;

    @GetMapping
    public ResponseEntity<List<CoachOptionResponse>> getCoaches(@RequestParam(required = false) Long sportId) {
        return ResponseEntity.ok(coachDirectoryService.getActiveCoaches(sportId));
    }
}
