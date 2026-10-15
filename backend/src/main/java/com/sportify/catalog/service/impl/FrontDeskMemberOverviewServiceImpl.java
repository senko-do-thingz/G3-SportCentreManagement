package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.MemberOverviewResponse;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.mapper.FrontDeskMapper;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.FrontDeskMemberOverviewService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.mapper.MemberSearchMapper;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class FrontDeskMemberOverviewServiceImpl implements FrontDeskMemberOverviewService {
    private final MemberProfileRepository members;
    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;
    private final CheckInRepository checkIns;
    private final FrontDeskMapper catalogMapper;
    private final MemberSearchMapper searchMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MemberOverviewResponse overview(Long memberId) {
        MemberProfile member = members.findWithUserById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        LocalDate today = LocalDate.now(clock);
        var current = cards.findCurrentForOverview(memberId, today, PageRequest.of(0, 1)).stream()
                .findFirst().map(catalogMapper::toCard).orElse(null);
        var pending = cards.findFirstByMemberIdAndStatusOrderByCreatedAtAscIdAsc(memberId, CardStatus.PENDING_PAYMENT)
                .map(catalogMapper::toCard).orElse(null);
        var packages = registrations.findForOverview(memberId, today);
        var activePackages = packages.stream().filter(r -> r.getStatus() == PackageRegistrationStatus.ACTIVE)
                .map(catalogMapper::toPackage).toList();
        var pendingPackages = packages.stream().filter(r -> r.getStatus() == PackageRegistrationStatus.PENDING_PAYMENT)
                .map(catalogMapper::toPackage).toList();
        var recent = checkIns.findTop5ByMemberIdOrderByCheckedInAtDescIdDesc(memberId).stream()
                .map(catalogMapper::toCheckIn).toList();
        return catalogMapper.toOverview(searchMapper.toProfile(member, current == null ? null : current.tierCode()),
                current, pending, activePackages, pendingPackages, recent);
    }

}
