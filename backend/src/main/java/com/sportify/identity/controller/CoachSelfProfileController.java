package com.sportify.identity.controller;

import com.sportify.identity.dto.CoachSelfCertificationCreateRequest;
import com.sportify.identity.dto.CoachSelfProfileResponse;
import com.sportify.identity.dto.CoachSelfProfileUpdateRequest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.CoachSelfProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.List;

// Endpoint de Coach tu xem va cap nhat ho so cong khai cung chung chi cua chinh minh
@RestController
@RequestMapping("/api/v1/coach/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COACH') and principal.enabled")
public class CoachSelfProfileController {

    private final CoachSelfProfileService coachSelfProfileService;

    @GetMapping
    public ResponseEntity<CoachSelfProfileResponse> getMyProfile(@AuthenticationPrincipal CustomUserDetails principal) {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachSelfProfileService.getMyProfile(actor));
    }

    @GetMapping("/certification-options")
    public ResponseEntity<List<CoachSelfProfileResponse.CertificationOption>> getCertificationOptions(
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachSelfProfileService.getCertificationOptions(actor));
    }

    @PutMapping
    public ResponseEntity<CoachSelfProfileResponse> updateMyProfile(
            @Valid @RequestBody CoachSelfProfileUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachSelfProfileService.updateMyProfile(request, actor));
    }

    @PostMapping(path = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CoachSelfProfileResponse> uploadMyPhoto(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal) throws IOException {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachSelfProfileService.uploadMyPhoto(file.getBytes(), actor));
    }

    @PostMapping("/certifications")
    public ResponseEntity<CoachSelfProfileResponse> addCertification(
            @Valid @RequestBody CoachSelfCertificationCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(coachSelfProfileService.addCertification(request, actor));
    }

    @DeleteMapping("/certifications/{id}")
    public ResponseEntity<CoachSelfProfileResponse> removeCertification(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UserAccount actor = principal == null ? null : principal.getUserAccount();
        return ResponseEntity.ok(coachSelfProfileService.removeCertification(id, actor));
    }
}
