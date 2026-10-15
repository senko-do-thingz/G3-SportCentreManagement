package com.sportify.catalog.service;

import com.sportify.catalog.dto.MemberOverviewResponse;

public interface FrontDeskMemberOverviewService {
    MemberOverviewResponse overview(Long memberId);
}
