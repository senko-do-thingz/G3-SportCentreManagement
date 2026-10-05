package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.MembershipRegistrationRequest;
import com.sportify.catalog.dto.MembershipResponse;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.MembershipPlanRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.MembershipService;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditAction;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.core.common.CodeFormatter;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Deprecated
@Service
@RequiredArgsConstructor
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository membershipRepository;
    private final MembershipPlanRepository planRepository;
    private final SportRepository sportRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional
    public MembershipResponse registerMembership(UserAccount currentUser, MembershipRegistrationRequest request) {
        return processRegistration(currentUser.getId(), currentUser, request, RegistrationChannel.ONLINE);
    }

    @Override
    @Transactional
    public MembershipResponse registerForMember(Long memberId, UserAccount currentUser, MembershipRegistrationRequest request) {
        return processRegistration(memberId, currentUser, request, RegistrationChannel.RECEPTION);
    }

    private MembershipResponse processRegistration(Long targetUserId, UserAccount creator, MembershipRegistrationRequest request, RegistrationChannel channel) {
        MemberProfile profile = memberProfileRepository.findById(targetUserId).orElse(null);
        
        if (profile == null) {
            UserAccount targetUser = userRepository.findById(targetUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetUserId));
            if (!"MEMBER".equals(targetUser.getRole().getCode())) {
                throw new BusinessRuleException("Only users with MEMBER role can have a member profile");
            }
            Long nextSeq = memberProfileRepository.getNextMemberCode();
            String memberCode = codeFormatter.formatMemberCode(nextSeq);
            profile = MemberProfile.builder()
                    .userAccount(targetUser)
                    .memberCode(memberCode)
                    .currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now(clock))
                    .build();
            profile = memberProfileRepository.save(profile);
        }

        MembershipPlan plan = planRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new BusinessRuleException("Plan is not ACTIVE");
        }

        if (request.getSportIds() == null || request.getSportIds().isEmpty()) {
            throw new BusinessRuleException("At least one sport must be selected");
        }
        
        java.util.Set<Long> uniqueSportIds = new java.util.LinkedHashSet<>(request.getSportIds());
        request.setSportIds(new java.util.ArrayList<>(uniqueSportIds));

        if (request.getSportIds().size() > plan.getMaxSports()) {
            throw new BusinessRuleException("Selected sports exceed the maximum allowed by the plan");
        }

        Set<Sport> eligibleSports = plan.getEligibleSports();
        Set<Long> eligibleSportIds = eligibleSports.stream().map(Sport::getId).collect(Collectors.toSet());
        for (Long sportId : request.getSportIds()) {
            if (!eligibleSportIds.contains(sportId)) {
                throw new BusinessRuleException("Sport ID " + sportId + " is not eligible for this plan");
            }
        }

        if (membershipRepository.existsByMemberIdAndStatus(targetUserId, MembershipStatus.PENDING_PAYMENT)) {
            throw new ConflictException("Member already has a PENDING_PAYMENT membership");
        }

        Set<Sport> selectedSports = eligibleSports.stream()
                .filter(s -> request.getSportIds().contains(s.getId()))
                .collect(Collectors.toSet());

        Long nextRegCode = membershipRepository.getNextRegistrationCode();
        String regCode = codeFormatter.formatRegistrationCode(nextRegCode);

        // Calculate previous membership and registrationType
        LocalDate today = LocalDate.now(clock);
        List<Membership> activeOrScheduled = membershipRepository.findActiveOrScheduled(
                targetUserId,
                List.of(MembershipStatus.ACTIVE, MembershipStatus.SCHEDULED),
                today
        );
        
        RegistrationType regType = activeOrScheduled.isEmpty() ? RegistrationType.NEW : RegistrationType.RENEWAL;
        Membership previous = activeOrScheduled.isEmpty() ? null : activeOrScheduled.get(activeOrScheduled.size() - 1);

        Membership membership = Membership.builder()
                .registrationCode(regCode)
                .member(profile)
                .plan(plan)
                .registrationType(regType)
                .previousMembership(previous)
                .channel(channel)
                .status(MembershipStatus.PENDING_PAYMENT)
                .priceAmount(plan.getPrice())
                .durationDays(plan.getDurationDays())
                .createdByUser(creator)
                .sports(selectedSports)
                .build();

        membership = membershipRepository.save(membership);

        String auditAction = regType == RegistrationType.RENEWAL ? AuditAction.MEMBERSHIP_RENEWED : AuditAction.MEMBERSHIP_REGISTERED;
        auditService.record(AuditEvent.builder()
                .actor(creator)
                .action(auditAction)
                .entityType("MEMBERSHIP")
                .entityId(membership.getId())
                .entityCode(membership.getRegistrationCode())
                .summary("Registered new membership")
                .build());
        return mapToResponse(membership);
    }

    @Override
    @Transactional
    public void cancelMembership(Long id, UserAccount currentUser) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        boolean isOwner = membership.getMember().getId().equals(currentUser.getId());
        boolean hasPrivilege = currentUser.getRole().getCode().equals("MANAGER") || currentUser.getRole().getCode().equals("RECEPTIONIST");

        if (!isOwner && !hasPrivilege) {
            throw new org.springframework.security.access.AccessDeniedException("Not authorized to cancel this membership");
        }

        if (membership.getStatus() != MembershipStatus.PENDING_PAYMENT) {
            throw new ConflictException("Only PENDING_PAYMENT memberships can be cancelled");
        }

        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setCancelledAt(LocalDateTime.now(clock));
        membership.setCancelReason("Cancelled by user");
        membershipRepository.save(membership);

        auditService.record(AuditEvent.builder()
                .actor(currentUser)
                .action(AuditAction.MEMBERSHIP_CANCELLED)
                .entityType("MEMBERSHIP")
                .entityId(membership.getId())
                .entityCode(membership.getRegistrationCode())
                .summary("Cancelled membership")
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MembershipResponse> getMyMemberships(UserAccount currentUser) {
        return membershipRepository.findAllByMemberIdOrderByStartDateDesc(currentUser.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void activateMembership(Long id, UserAccount actor) {
        Membership membership = membershipRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Membership not found"));

        if (membership.getStatus() != MembershipStatus.PENDING_PAYMENT) {
            throw new ConflictException("Only PENDING_PAYMENT memberships can be activated");
        }

        LocalDate today = LocalDate.now(clock);
        List<Membership> activeOrScheduled = membershipRepository.findActiveOrScheduled(
                membership.getMember().getId(),
                List.of(MembershipStatus.ACTIVE, MembershipStatus.SCHEDULED),
                today
        );

        LocalDate startDate = today;
        if (!activeOrScheduled.isEmpty()) {
            Membership last = activeOrScheduled.get(activeOrScheduled.size() - 1);
            startDate = last.getEndDate().plusDays(1);
        }

        membership.setStartDate(startDate);
        // Using startDate + durationDays as per docs: "matches 27 Sept - 27 Oct"
        membership.setEndDate(startDate.plusDays(membership.getDurationDays()));
        membership.setActivatedAt(LocalDateTime.now(clock));

        if (startDate.isAfter(today)) {
            membership.setStatus(MembershipStatus.SCHEDULED);
        } else {
            membership.setStatus(MembershipStatus.ACTIVE);
        }

        membershipRepository.save(membership);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.MEMBERSHIP_ACTIVATED)
                .entityType("MEMBERSHIP")
                .entityId(membership.getId())
                .entityCode(membership.getRegistrationCode())
                .summary("Membership activated")
                .build());
    }

    @Override
    @Transactional
    public void processScheduledMemberships(LocalDate today) {
        List<Membership> scheduled = membershipRepository.findByStatusAndStartDateLessThanEqual(MembershipStatus.SCHEDULED, today);
        for (Membership m : scheduled) {
            m.setStatus(MembershipStatus.ACTIVE);
            membershipRepository.save(m);
            auditService.record(AuditEvent.builder()
                    .actor(null)
                    .action(AuditAction.MEMBERSHIP_ACTIVATED_BY_JOB)
                    .entityType("MEMBERSHIP")
                    .entityId(m.getId())
                    .entityCode(m.getRegistrationCode())
                    .summary("Membership activated by scheduled job")
                    .build());
        }
    }

    @Override
    @Transactional
    public void processExpiredMemberships(LocalDate today) {
        List<Membership> active = membershipRepository.findByStatusAndEndDateLessThan(MembershipStatus.ACTIVE, today);
        for (Membership m : active) {
            m.setStatus(MembershipStatus.EXPIRED);
            membershipRepository.save(m);
            auditService.record(AuditEvent.builder()
                    .actor(null)
                    .action(AuditAction.MEMBERSHIP_EXPIRED_BY_JOB)
                    .entityType("MEMBERSHIP")
                    .entityId(m.getId())
                    .entityCode(m.getRegistrationCode())
                    .summary("Membership expired by scheduled job")
                    .build());
        }
    }



    private MembershipResponse mapToResponse(Membership m) {
        return MembershipResponse.builder()
                .id(m.getId())
                .registrationCode(m.getRegistrationCode())
                .planId(m.getPlan().getId())
                .planName(m.getPlan().getName())
                .registrationType(m.getRegistrationType().name())
                .channel(m.getChannel().name())
                .status(m.getStatus().name())
                .priceAmount(m.getPriceAmount())
                .durationDays(m.getDurationDays())
                .startDate(m.getStartDate())
                .endDate(m.getEndDate())
                .sports(m.getSports().stream().map(Sport::getName).collect(Collectors.toList()))
                .build();
    }
}
