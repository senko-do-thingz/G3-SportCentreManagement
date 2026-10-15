package com.sportify.identity.mapper;

import com.sportify.catalog.dto.*;
import com.sportify.identity.dto.*;
import com.sportify.identity.entity.*;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDate;
import java.util.List;

@Mapper(componentModel = "spring")
public interface FrontDeskMemberMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "fullName", source = "request.fullName")
    @Mapping(target = "phone", source = "request.phone")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "status", constant = "ACTIVE")
    UserAccount toUser(MemberCreateRequest request, Role role, String passwordHash);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "userAccount", source = "user")
    @Mapping(target = "memberCode", source = "memberCode")
    @Mapping(target = "joinedOn", source = "joinedOn")
    @Mapping(target = "currentLevel", constant = "BEGINNER")
    MemberProfile toMember(UserAccount user, String memberCode, LocalDate joinedOn);

    @Mapping(target = "userId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.userAccount.fullName")
    @Mapping(target = "phone", source = "profile.userAccount.phone")
    @Mapping(target = "email", source = "profile.userAccount.email")
    @Mapping(target = "status", source = "profile.userAccount.status")
    @Mapping(target = "temporaryPassword", source = "temporaryPassword")
    MemberCreateResponse toCreated(MemberProfile profile, String temporaryPassword);

    MemberOverviewResponse toOverview(MemberSearchResponse profile, FrontDeskCardResponse currentCard,
            FrontDeskCardResponse pendingCardRequest, List<FrontDeskPackageResponse> activePackageRegistrations,
            List<FrontDeskPackageResponse> pendingPackageRegistrations, List<FrontDeskCheckInResponse> recentCheckIns);
}
