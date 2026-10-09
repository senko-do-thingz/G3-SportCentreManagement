package com.sportify.catalog.controller;

import com.sportify.catalog.dto.SportResponse;
import com.sportify.catalog.service.SportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public (no token) catalog endpoint for sports.
 */
@RestController
@RequestMapping("/api/v1/sports")
@RequiredArgsConstructor
public class SportController {

    private final SportService sportService;

    @GetMapping
    public ResponseEntity<List<SportResponse>> getActiveSports() {
        return ResponseEntity.ok(sportService.getActiveSports());
    }
}
