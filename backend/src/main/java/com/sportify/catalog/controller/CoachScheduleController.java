package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CoachRosterResponse;
import com.sportify.catalog.dto.CoachSessionResponse;
import com.sportify.catalog.service.CoachScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/coach/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('COACH', 'MANAGER')")
public class CoachScheduleController {
    private final CoachScheduleService service;

    @GetMapping
    public List<CoachSessionResponse> listSessions(@RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) Long coachId) {
        return service.listSessions(from, to, coachId);
    }

    @GetMapping("/{id}/roster")
    public List<CoachRosterResponse> roster(@PathVariable Long id) {
        return service.roster(id);
    }
}
