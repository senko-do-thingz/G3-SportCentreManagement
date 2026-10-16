package com.sportify.catalog.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SessionDetailResponse;
import com.sportify.catalog.dto.SessionResponse;
import com.sportify.catalog.dto.SessionSearchQuery;
import com.sportify.identity.entity.UserAccount;

public interface MemberScheduleService {
    PageResponse<SessionResponse> listSessions(SessionSearchQuery query, UserAccount actor);
    SessionDetailResponse getSession(Long id, UserAccount actor);
    SessionDetailResponse getMemberEligibility(Long id, Long memberId, UserAccount actor);
}
