package com.sportify.identity.controller;

import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.repository.UserAvatarStore;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Serves uploaded profile photos for public coach cards and authenticated users.
 */
@RestController
@RequiredArgsConstructor
public class UserAvatarController {

    private final UserAvatarStore avatarStore;

    @GetMapping("/api/v1/users/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Optional<UserAvatarStore.StoredAvatar> avatarOpt = avatarStore.findPublicCoachAvatar(id);
        if (avatarOpt.isEmpty() && userDetails != null && userDetails.getUserAccount() != null
                && id.equals(userDetails.getUserAccount().getId())) {
            avatarOpt = avatarStore.findByUserId(id);
        }
        UserAvatarStore.StoredAvatar avatar = avatarOpt
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.data());
    }
}
