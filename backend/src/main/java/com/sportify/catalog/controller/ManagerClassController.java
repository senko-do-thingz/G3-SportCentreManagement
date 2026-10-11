package com.sportify.catalog.controller;

import com.sportify.catalog.dto.ClassStatusUpdateRequest;
import com.sportify.catalog.dto.ManagerClassResponse;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SportClassRequest;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.service.ManagerClassService;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager workspace "Class Management" (F2-01) and "Create / Edit Class" (F2-02).
 */
@RestController
@RequestMapping("/api/v1/manager/classes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerClassController {

    private final ManagerClassService managerClassService;

    @GetMapping
    public ResponseEntity<PageResponse<ManagerClassResponse>> listClasses(
            @RequestParam(required = false) Long sportId,
            @RequestParam(required = false) ClassLevel level,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(managerClassService.listClasses(sportId, level, coachId, active, search, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ManagerClassResponse> getClass(@PathVariable Long id) {
        return ResponseEntity.ok(managerClassService.getClass(id));
    }

    @PostMapping
    public ResponseEntity<ManagerClassResponse> createClass(
            @Valid @RequestBody SportClassRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerClassService.createClass(request, actor(principal)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ManagerClassResponse> updateClass(
            @PathVariable Long id,
            @Valid @RequestBody SportClassRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(managerClassService.updateClass(id, request, actor(principal)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ManagerClassResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ClassStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(managerClassService.changeStatus(id, request.active(), actor(principal)));
    }

    private static UserAccount actor(CustomUserDetails principal) {
        return principal == null ? null : principal.getUserAccount();
    }
}
