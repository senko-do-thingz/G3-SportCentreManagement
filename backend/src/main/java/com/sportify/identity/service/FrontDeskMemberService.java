package com.sportify.identity.service;

import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.dto.MemberCreateResponse;

public interface FrontDeskMemberService {
    MemberCreateResponse create(MemberCreateRequest request);
}
