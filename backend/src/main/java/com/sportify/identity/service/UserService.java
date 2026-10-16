package com.sportify.identity.service;

import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.dto.UpdateProfileRequest;
import com.sportify.identity.dto.ChangePasswordRequest;
import com.sportify.identity.security.CustomUserDetails;

public interface UserService {
    UserProfileResponse getCurrentUserProfile(CustomUserDetails userDetails);
    UserProfileResponse updateCurrentUserProfile(CustomUserDetails userDetails, UpdateProfileRequest request);
    void changeCurrentUserPassword(CustomUserDetails userDetails, ChangePasswordRequest request);
}
