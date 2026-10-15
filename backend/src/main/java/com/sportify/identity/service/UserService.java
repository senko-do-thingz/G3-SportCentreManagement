package com.sportify.identity.service;

import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.security.CustomUserDetails;

public interface UserService {
    UserProfileResponse getCurrentUserProfile(CustomUserDetails userDetails);
}
