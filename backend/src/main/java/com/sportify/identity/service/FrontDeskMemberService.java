package com.sportify.identity.service;

import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.dto.MemberCreateResponse;
import com.sportify.identity.dto.MemberOverviewResponse;

public interface FrontDeskMemberService {
    MemberOverviewResponse overview(Long memberId);
    MemberCreateResponse create(MemberCreateRequest request);
}
