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
@RequestMapping("/api/v1/reception/training")
@PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
@RequiredArgsConstructor
public class ReceptionTrainingController {
    private final TrainingWriteService service;
    @GetMapping("/sessions") public List<SessionSummary> sessions() { return service.receptionSessions(); }
    @GetMapping("/sessions/{id}/members") public List<SessionMember> sessionMembers(@PathVariable long id) { return service.receptionSessionMembers(id); }
    @GetMapping("/sessions/by-code/{sessionId}/members") public List<SessionMember> membersByCode(@PathVariable String sessionId) { return service.receptionSessionMembersByCode(sessionId); }
    @PostMapping("/attendance") @ResponseStatus(HttpStatus.CREATED)
    public Created attendance(@Valid @RequestBody TrainingWriteRequest.Attendance request, @AuthenticationPrincipal CustomUserDetails user) { return service.attendance(user.getUserAccount().getId(), request); }
    @PutMapping("/attendance/{id}")
    public Created correct(@PathVariable long id, @Valid @RequestBody TrainingWriteRequest.Correction request, @AuthenticationPrincipal CustomUserDetails user) { return service.correctAttendance(user.getUserAccount().getId(), id, request); }
}
