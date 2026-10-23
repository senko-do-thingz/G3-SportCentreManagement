package com.sportify.identity.controller;

import com.sportify.identity.dto.ManagerCoachProfileResponse;
import com.sportify.identity.dto.ManagerCoachProfileUpdateRequest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.ManagerCoachProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Manager workspace "User Management": edit a coach's public profile shown on the Home page.
 */
@RestController
@RequestMapping("/api/v1/manager/users/{id}/coach-profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class ManagerCoachProfileController {

    private final ManagerCoachProfileService coachProfileService;

    @GetMapping
    public ResponseEntity<ManagerCoachProfileResponse> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(coachProfileService.getProfile(id));
    }

    @PutMapping
    public ResponseEntity<ManagerCoachProfileResponse> updateProfile(
            @PathVariable Long id,
            @Valid @RequestBody ManagerCoachProfileUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachProfileService.updateProfile(id, request, actor));
    }

    /** Multipart field "file": JPEG, PNG or WebP up to 2 MB. Saved immediately. */
    @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ManagerCoachProfileResponse> uploadPhoto(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachProfileService.uploadPhoto(id, file.getBytes(), actor));
    }
}
