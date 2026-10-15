package com.sportify.catalog.mapper;

import com.sportify.catalog.dto.FrontDeskCardResponse;
import com.sportify.catalog.dto.FrontDeskCheckInResponse;
import com.sportify.catalog.dto.FrontDeskPackageResponse;
import com.sportify.catalog.dto.MemberOverviewResponse;
import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.identity.dto.MemberSearchResponse;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

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

    MemberOverviewResponse toOverview(MemberSearchResponse profile, FrontDeskCardResponse currentCard,
            FrontDeskCardResponse pendingCardRequest, List<FrontDeskPackageResponse> activePackageRegistrations,
            List<FrontDeskPackageResponse> pendingPackageRegistrations, List<FrontDeskCheckInResponse> recentCheckIns);
}
