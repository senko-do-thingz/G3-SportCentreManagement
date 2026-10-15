package com.sportify.identity.service.impl;

import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.core.common.FrontDeskPaging;
import com.sportify.core.common.SearchUtils;
import com.sportify.identity.mapper.MemberSearchMapper;
import java.time.Clock;
import java.time.LocalDate;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.service.MemberSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberSearchServiceImpl implements MemberSearchService {

    private final MemberProfileRepository memberProfileRepository;
    private final MemberSearchMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Page<MemberSearchResponse> searchMembers(String query, Pageable pageable) {
        String pattern = query == null || query.isBlank() ? null : "%" + SearchUtils.escapeLike(query.trim()) + "%";
        return memberProfileRepository.searchFrontDesk(pattern, LocalDate.now(clock), FrontDeskPaging.search(pageable))
                .map(mapper::toResponse);
    }
}
