package com.sportify.identity.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.dto.ManagerUserResponse;
import com.sportify.identity.dto.StaffAccountCreateRequest;
import com.sportify.identity.dto.UserStatusUpdateRequest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.ManagerUserService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager workspace "User Management" (screen F1-01 and overlay "Add Staff Account").
 */
@RestController
@RequestMapping("/api/v1/manager/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerUserController {

    private final ManagerUserService managerUserService;

    @GetMapping
    public ResponseEntity<PageResponse<ManagerUserResponse>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(managerUserService.listUsers(role, status, search, page, size));
    }

    @PostMapping
    public ResponseEntity<ManagerUserResponse> createStaffAccount(
            @Valid @RequestBody StaffAccountCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managerUserService.createStaffAccount(request, actor(principal)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ManagerUserResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(managerUserService.changeStatus(id, request.status(), actor(principal)));
    }

    private static UserAccount actor(CustomUserDetails principal) {
        return principal == null ? null : principal.getUserAccount();
    }
}
