package com.sportify.identity.service;

import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.dto.UpdateProfileRequest;
import com.sportify.identity.dto.ChangePasswordRequest;
import com.sportify.identity.repository.UserAvatarStore;
import com.sportify.identity.security.CustomUserDetails;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {
    UserProfileResponse getCurrentUserProfile(CustomUserDetails userDetails);
    UserProfileResponse updateCurrentUserProfile(CustomUserDetails userDetails, UpdateProfileRequest request);
    void changeCurrentUserPassword(CustomUserDetails userDetails, ChangePasswordRequest request);
    UserProfileResponse uploadCurrentUserAvatar(CustomUserDetails userDetails, MultipartFile file);
    UserProfileResponse uploadCurrentUserAvatar(CustomUserDetails userDetails, byte[] data);
    UserAvatarStore.StoredAvatar getCurrentUserAvatar(CustomUserDetails userDetails);
    void deleteCurrentUserAvatar(CustomUserDetails userDetails);
}
