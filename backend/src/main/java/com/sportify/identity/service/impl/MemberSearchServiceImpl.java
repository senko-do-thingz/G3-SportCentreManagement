package com.sportify.identity.service.impl;

import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.entity.MemberProfile;
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

    @Override
    @Transactional(readOnly = true)
    public Page<MemberSearchResponse> searchMembers(String query, Pageable pageable) {
        Page<MemberProfile> profiles;

        if (query == null || query.trim().isEmpty()) {
            profiles = memberProfileRepository.findAllWithUser(pageable);
        } else {
            String pattern = "%" + query.trim().toLowerCase(java.util.Locale.ROOT) + "%";
            profiles = memberProfileRepository.search(pattern, pageable);
        }

        return profiles.map(p -> MemberSearchResponse.builder()
                .userId(p.getId())
                .memberCode(p.getMemberCode())
                .fullName(p.getUserAccount().getFullName())
                .email(p.getUserAccount().getEmail())
                .phone(p.getUserAccount().getPhone())
                .currentLevel(p.getCurrentLevel())
                .build());
    }
}
