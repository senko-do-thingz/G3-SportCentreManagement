package com.sportify.identity.service;

import com.sportify.identity.dto.CoachPublicProfileResponse;
import com.sportify.identity.entity.UserAccount;

public interface CoachPublicProfileService {
    CoachPublicProfileResponse getProfile(Long id, UserAccount actor);
}
