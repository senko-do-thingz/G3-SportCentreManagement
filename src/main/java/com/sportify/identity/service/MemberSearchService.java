package com.sportify.identity.service;

import com.sportify.identity.dto.MemberSearchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MemberSearchService {
    Page<MemberSearchResponse> searchMembers(String query, Pageable pageable);
}
