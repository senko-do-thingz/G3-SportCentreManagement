package com.sportify.catalog.controller;

import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.catalog.dto.TrainingWriteResponse.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reception/training")
@PreAuthorize("denyAll()")
public class ReceptionTrainingController {
    private static AccessDeniedException denied() {
        return new AccessDeniedException("Session attendance is restricted to the assigned Coach");
    }
    @GetMapping("/sessions") public List<SessionSummary> sessions() { throw denied(); }
    @GetMapping("/sessions/{id}/members") public List<SessionMember> sessionMembers(@PathVariable long id) { throw denied(); }
    @GetMapping("/sessions/by-code/{sessionId}/members") public List<SessionMember> membersByCode(@PathVariable String sessionId) { throw denied(); }
    @PostMapping("/attendance") @ResponseStatus(HttpStatus.CREATED)
    public Created attendance(@Valid @RequestBody TrainingWriteRequest.Attendance request) { throw denied(); }
    @PutMapping("/attendance/{id}")
    public Created correct(@PathVariable long id, @Valid @RequestBody TrainingWriteRequest.Correction request) { throw denied(); }
}
