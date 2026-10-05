package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.Membership;
import com.sportify.catalog.entity.MembershipStatus;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.service.CheckInService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditAction;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final CheckInRepository checkInRepository;
    private final MembershipRepository membershipRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional
    public CheckInResponse recordCheckIn(CheckInRequest request, UserAccount receptionist) {
        String identifier = request.getIdentifier();
        Optional<MemberProfile> profileOpt = memberProfileRepository.findByMemberCode(identifier);
        if (profileOpt.isEmpty()) {
            try {
                Long id = Long.parseLong(identifier);
                profileOpt = memberProfileRepository.findById(id);
            } catch (NumberFormatException ignored) {}
        }

        MemberProfile profile = profileOpt.orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        List<Membership> allMemberships = membershipRepository.findAllByMemberIdOrderByStartDateDesc(profile.getId());

        LocalDate today = LocalDate.now(clock);
        Membership activeMembership = null;
        String denialReason = null;

        if (allMemberships.isEmpty()) {
            denialReason = "No membership found";
        } else {
            // Find an active and valid membership
            activeMembership = allMemberships.stream()
                    .filter(m -> m.getStatus() == MembershipStatus.ACTIVE
                            && m.getStartDate() != null && m.getEndDate() != null
                            && !today.isBefore(m.getStartDate())
                            && !today.isAfter(m.getEndDate()))
                    .findFirst()
                    .orElse(null);

            if (activeMembership == null) {
                denialReason = resolveDenialReason(allMemberships, today);
            }
        }

        CheckInResult result = activeMembership != null ? CheckInResult.ALLOWED : CheckInResult.DENIED;

        CheckIn checkIn = CheckIn.builder()
                .member(profile)
                .membership(activeMembership)
                .checkedInAt(LocalDateTime.now(clock))
                .result(result)
                .denialReason(denialReason)
                .recordedBy(receptionist)
                .note(request.getNote())
                .build();

        checkIn = checkInRepository.save(checkIn);

        auditService.record(AuditEvent.builder()
                .actor(receptionist)
                .action(AuditAction.MEMBER_CHECKED_IN)
                .entityType("CHECK_IN")
                .entityId(checkIn.getId())
                .entityCode(profile.getMemberCode())
                .summary("Check-in " + result.name() + " for member " + profile.getMemberCode())
                .build());

        return mapToResponse(checkIn);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckInResponse> getCheckInHistory(Long memberId) {
        return checkInRepository.findByMemberIdOrderByCheckedInAtDesc(memberId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private CheckInResponse mapToResponse(CheckIn checkIn) {
        return CheckInResponse.builder()
                .id(checkIn.getId())
                .memberId(checkIn.getMember().getId())
                .memberName(checkIn.getMember().getUserAccount().getFullName())
                .membershipId(checkIn.getMembership() != null ? checkIn.getMembership().getId() : null)
                .checkedInAt(checkIn.getCheckedInAt())
                .result(checkIn.getResult().name())
                .denialReason(checkIn.getDenialReason())
                .note(checkIn.getNote())
                .build();
    }

    private String resolveDenialReason(List<Membership> memberships, LocalDate today) {
        if (memberships.isEmpty()) {
            return "No membership found";
        }
        if (memberships.stream().anyMatch(m -> m.getStatus() == MembershipStatus.PENDING_PAYMENT)) {
            return "Membership is PENDING_PAYMENT";
        }
        if (memberships.stream().anyMatch(m -> m.getStatus() == MembershipStatus.SCHEDULED)) {
            return "Membership SCHEDULED but not yet started";
        }
        if (memberships.stream().anyMatch(m -> m.getStatus() == MembershipStatus.EXPIRED ||
                (m.getStatus() == MembershipStatus.ACTIVE && m.getEndDate() != null && today.isAfter(m.getEndDate())))) {
            return "Membership is EXPIRED";
        }
        return "Membership is CANCELLED";
    }
}
