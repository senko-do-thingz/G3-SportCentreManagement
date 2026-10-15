package com.sportify.catalog.dto;

import com.sportify.identity.dto.MemberSearchResponse;

import java.util.List;

public record MemberOverviewResponse(MemberSearchResponse profile, FrontDeskCardResponse currentCard,
        FrontDeskCardResponse pendingCardRequest, List<FrontDeskPackageResponse> activePackageRegistrations,
        List<FrontDeskPackageResponse> pendingPackageRegistrations, List<FrontDeskCheckInResponse> recentCheckIns) {
}
