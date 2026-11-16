package com.sportify.identity.controller;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.dto.UpdateProfileRequest;
import com.sportify.identity.dto.ChangePasswordRequest;
import com.sportify.identity.repository.UserAvatarStore;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(userService.getCurrentUserProfile(userDetails));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateCurrentUserProfile(userDetails, request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changeCurrentUserPassword(userDetails, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/avatar")
    public ResponseEntity<byte[]> getCurrentUserAvatar(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserAvatarStore.StoredAvatar avatar = userService.getCurrentUserAvatar(userDetails);
        if (avatar == null) {
            return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.data());
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserProfileResponse> uploadCurrentUserAvatar(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Choose a photo to upload");
        }
        return ResponseEntity.ok(userService.uploadCurrentUserAvatar(userDetails, file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteCurrentUserAvatar(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteCurrentUserAvatar(userDetails);
        return ResponseEntity.noContent().build();
    }
}
