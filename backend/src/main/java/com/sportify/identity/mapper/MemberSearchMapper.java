package com.sportify.identity.mapper;

import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.repository.MemberSearchRow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MemberSearchMapper {
    MemberSearchResponse toResponse(MemberSearchRow row);

    @Mapping(target = "userId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.userAccount.fullName")
    @Mapping(target = "email", source = "profile.userAccount.email")
    @Mapping(target = "phone", source = "profile.userAccount.phone")
    @Mapping(target = "status", source = "profile.userAccount.status")
    @Mapping(target = "cardTier", source = "cardTier")
    MemberSearchResponse toProfile(MemberProfile profile, String cardTier);
}
