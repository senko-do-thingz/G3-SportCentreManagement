package com.sportify.catalog.controller;

import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.catalog.dto.TrainingWriteResponse.*;
import com.sportify.catalog.service.TrainingWriteService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/coach/training")
@PreAuthorize("hasRole('COACH') and principal.enabled")
@RequiredArgsConstructor
public class CoachTrainingController {
    private final TrainingWriteService service;
    @GetMapping("/classes")
    public List<ClassSummary> classes(@AuthenticationPrincipal CustomUserDetails user) { return service.coachClasses(user.getUserAccount().getId()); }
    @GetMapping("/classes/{id}/members")
    public List<BookingCandidate> members(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) { return service.coachMembers(user.getUserAccount().getId(), id); }
    @GetMapping("/plans")
    public List<CoachPlan> plans(@AuthenticationPrincipal CustomUserDetails user) { return service.coachPlans(user.getUserAccount().getId()); }
    @GetMapping("/plans/{id}")
    public CoachPlanDetail plan(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) { return service.coachPlan(user.getUserAccount().getId(), id); }
    @PostMapping("/plans") @ResponseStatus(HttpStatus.CREATED)
    public Created createPlan(@Valid @RequestBody TrainingWriteRequest.Plan request, @AuthenticationPrincipal CustomUserDetails user) { return service.createPlan(user.getUserAccount().getId(), request); }
    @PostMapping("/plans/{id}/feedback") @ResponseStatus(HttpStatus.CREATED)
    public Created feedback(@PathVariable long id, @Valid @RequestBody TrainingWriteRequest.Feedback request, @AuthenticationPrincipal CustomUserDetails user) { return service.feedback(user.getUserAccount().getId(), id, request); }
    @GetMapping("/sessions")
    public List<SessionSummary> sessions(@AuthenticationPrincipal CustomUserDetails user) { return service.coachSessions(user.getUserAccount().getId()); }
    @GetMapping("/sessions/{id}/members")
    public List<SessionMember> sessionMembers(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) { return service.coachSessionMembers(user.getUserAccount().getId(), id); }
    @GetMapping("/metrics")
    public List<Metric> metrics(@RequestParam long sportId) { return service.metrics(sportId); }
    @PostMapping("/results") @ResponseStatus(HttpStatus.CREATED)
    public Created result(@Valid @RequestBody TrainingWriteRequest.Result request, @AuthenticationPrincipal CustomUserDetails user) { return service.createResult(user.getUserAccount().getId(), request); }
    @PostMapping("/attendance") @ResponseStatus(HttpStatus.CREATED)
    public Created attendance(@Valid @RequestBody TrainingWriteRequest.Attendance request, @AuthenticationPrincipal CustomUserDetails user) { return service.coachAttendance(user.getUserAccount().getId(), request); }
    @PutMapping("/attendance/{id}")
    public Created correctAttendance(@PathVariable long id, @Valid @RequestBody TrainingWriteRequest.Correction request, @AuthenticationPrincipal CustomUserDetails user) { return service.coachCorrection(user.getUserAccount().getId(), id, request); }
}
