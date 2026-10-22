package com.sportify.identity.controller;

import com.sportify.identity.dto.CoachOptionResponse;
import com.sportify.identity.service.CoachDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, read-only coach list for the guest Home page ("Train with the best").
 * Returns the same fields as the staff dropdown (name, headline, specialties, experience, sport ids)
 * and no contact data. Permitted for anonymous GET in SecurityConfig.
 */
@RestController
@RequiredArgsConstructor
public class PublicCoachController {

    private final CoachDirectoryService coachDirectoryService;

    @GetMapping("/api/v1/coaches/public")
    public ResponseEntity<List<CoachOptionResponse>> getPublicCoaches(@RequestParam(required = false) Long sportId) {
        return ResponseEntity.ok(coachDirectoryService.getActiveCoaches(sportId));
    }
}
