package com.sportify.identity.mapper;

import com.sportify.identity.dto.ManagerUserResponse;
import com.sportify.identity.entity.UserAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ManagerUserMapper {

    @Mapping(target = "role", source = "role.code")
    ManagerUserResponse toResponse(UserAccount userAccount);
}
