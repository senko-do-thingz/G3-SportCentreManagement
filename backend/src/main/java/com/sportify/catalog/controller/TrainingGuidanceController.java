package com.sportify.catalog.controller;

import com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences;
import com.sportify.catalog.dto.TrainingGuidanceResponse.Recommendations;
import com.sportify.catalog.service.TrainingGuidanceService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/v1/member/training")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class TrainingGuidanceController {
    private final TrainingGuidanceService service;
    @GetMapping("/recommendations")
    public Recommendations recommendations(@AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(required=false) Long sportId, @RequestParam(required=false) String level,
        @RequestParam(required=false) String goal, @RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to, @RequestParam(required=false) LocalTime earliest,
        @RequestParam(required=false) LocalTime latest, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="10") int size) {
        return service.recommendations(user.getUserAccount().getId(), new Preferences(sportId,level,goal,from,to,earliest,latest,page,size));
    }
}
