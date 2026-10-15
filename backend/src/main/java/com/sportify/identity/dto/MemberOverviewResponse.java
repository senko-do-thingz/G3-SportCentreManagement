package com.sportify.identity.dto;

import com.sportify.catalog.dto.FrontDeskCardResponse;
import com.sportify.catalog.dto.FrontDeskPackageResponse;
import com.sportify.catalog.dto.FrontDeskCheckInResponse;
import java.util.List;

public record MemberOverviewResponse(MemberSearchResponse profile, FrontDeskCardResponse currentCard,
        FrontDeskCardResponse pendingCardRequest, List<FrontDeskPackageResponse> activePackageRegistrations,
        List<FrontDeskPackageResponse> pendingPackageRegistrations, List<FrontDeskCheckInResponse> recentCheckIns) {
}
