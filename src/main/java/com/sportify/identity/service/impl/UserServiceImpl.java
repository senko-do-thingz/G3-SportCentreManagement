package com.sportify.identity.service.impl;

import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.mapper.UserMapper;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserProfileResponse getCurrentUserProfile(CustomUserDetails userDetails) {
        return userMapper.toProfileResponse(userDetails.getUserAccount());
    }
}
