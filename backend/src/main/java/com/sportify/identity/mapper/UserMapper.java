package com.sportify.identity.mapper;

import com.sportify.identity.dto.UserProfileResponse;
import com.sportify.identity.entity.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "role", source = "role.code")
    UserProfileResponse toProfileResponse(UserAccount userAccount);
}
