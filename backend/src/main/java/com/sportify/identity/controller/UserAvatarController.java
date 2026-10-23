package com.sportify.identity.controller;

import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.repository.UserAvatarStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves uploaded profile photos for the public Home coach cards. Permitted for anonymous GET in SecurityConfig.
 * Eligibility is checked on every request. Versioned URLs do not authorize a hidden or inactive photo.
 */
@RestController
@RequiredArgsConstructor
public class UserAvatarController {

    private final UserAvatarStore avatarStore;

    @GetMapping("/api/v1/users/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long id) {
        UserAvatarStore.StoredAvatar avatar = avatarStore.findPublicCoachAvatar(id)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.data());
    }
}
