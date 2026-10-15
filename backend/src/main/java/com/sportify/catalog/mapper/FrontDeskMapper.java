package com.sportify.catalog.mapper;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FrontDeskMapper {
    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberCode", source = "member.memberCode")
    @Mapping(target = "memberName", source = "member.userAccount.fullName")
    @Mapping(target = "tierCode", source = "tier.code")
    @Mapping(target = "tierName", source = "tier.name")
    @Mapping(target = "amount", source = "pricePaid")
    @Mapping(target = "requestedAt", source = "createdAt")
    FrontDeskCardResponse toCard(MemberCard card);

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "memberCode", source = "member.memberCode")
    @Mapping(target = "memberName", source = "member.userAccount.fullName")
    @Mapping(target = "packageId", source = "sportPackage.id")
    @Mapping(target = "packageName", source = "sportPackage.name")
    @Mapping(target = "amount", source = "paidAmount")
    @Mapping(target = "requestedAt", source = "createdAt")
    FrontDeskPackageResponse toPackage(SportPackageRegistration registration);

    FrontDeskCheckInResponse toCheckIn(CheckIn checkIn);
}
