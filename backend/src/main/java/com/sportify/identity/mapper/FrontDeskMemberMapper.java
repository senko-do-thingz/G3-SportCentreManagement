package com.sportify.identity.mapper;

import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.dto.MemberCreateResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDate;

@Mapper(componentModel = "spring", imports = UserStatus.class)
public abstract class FrontDeskMemberMapper {
    private static final String DEFAULT_MEMBER_LEVEL = "BEGINNER";

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "fullName", source = "request.fullName")
    @Mapping(target = "phone", source = "request.phone")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "status", expression = "java(UserStatus.ACTIVE.name())")
    public abstract UserAccount toUser(MemberCreateRequest request, Role role, String passwordHash);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "userAccount", source = "user")
    @Mapping(target = "memberCode", source = "memberCode")
    @Mapping(target = "joinedOn", source = "joinedOn")
    @Mapping(target = "currentLevel", constant = DEFAULT_MEMBER_LEVEL)
    public abstract MemberProfile toMember(UserAccount user, String memberCode, LocalDate joinedOn);

    @Mapping(target = "userId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.userAccount.fullName")
    @Mapping(target = "phone", source = "profile.userAccount.phone")
    @Mapping(target = "email", source = "profile.userAccount.email")
    @Mapping(target = "status", source = "profile.userAccount.status")
    @Mapping(target = "temporaryPassword", source = "temporaryPassword")
    public abstract MemberCreateResponse toCreated(MemberProfile profile, String temporaryPassword);

}
