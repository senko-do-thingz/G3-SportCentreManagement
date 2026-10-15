package com.sportify.catalog.controller;

import com.sportify.catalog.dto.ManagerSessionResponse;
import com.sportify.catalog.dto.SessionCancelRequest;
import com.sportify.catalog.dto.SessionCreateRequest;
import com.sportify.catalog.dto.SessionUpdateRequest;
import com.sportify.catalog.service.ManagerSessionService;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/manager")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerSessionController {
    private final ManagerSessionService managerSessionService;

    @GetMapping("/sessions")
    public ResponseEntity<List<ManagerSessionResponse>> listSessions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Long facilityId) {
        return ResponseEntity.ok(managerSessionService.listSessions(from, to, classId, coachId, facilityId));
    }

    @PostMapping("/classes/{classId}/sessions")
    public ResponseEntity<List<ManagerSessionResponse>> createSessions(@PathVariable Long classId,
            @Valid @RequestBody SessionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerSessionService.createSessions(classId, request, actor(principal)));
    }

    @PutMapping("/sessions/{id}")
    public ResponseEntity<ManagerSessionResponse> updateSession(@PathVariable Long id,
            @Valid @RequestBody SessionUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(managerSessionService.updateSession(id, request, actor(principal)));
    }

    @PutMapping("/sessions/{id}/cancel")
    public ResponseEntity<ManagerSessionResponse> cancelSession(@PathVariable Long id,
            @Valid @RequestBody SessionCancelRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(managerSessionService.cancelSession(id, request, actor(principal)));
    }

    private static UserAccount actor(CustomUserDetails principal) {
        return principal == null ? null : principal.getUserAccount();
    }
}
