package com.sportify.catalog.service;

import com.sportify.catalog.dto.MembershipRegistrationRequest;
import com.sportify.catalog.dto.MembershipResponse;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.MembershipPlanRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.impl.MembershipServiceImpl;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.core.audit.AuditService;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditAction;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MembershipServiceTest {

    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private MembershipPlanRepository planRepository;
    @Mock
    private SportRepository sportRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private com.sportify.core.common.CodeFormatter codeFormatter;
    @org.mockito.Spy
    private java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-05T00:00:00Z"), java.time.ZoneId.of("UTC"));

    @InjectMocks
    private MembershipServiceImpl membershipService;

    private UserAccount memberUser;
    private MemberProfile memberProfile;
    private MembershipPlan plan;
    private Sport sport1;

    @BeforeEach
    void setUp() {
        Role memberRole = new Role();
        memberRole.setCode("MEMBER");
        memberUser = UserAccount.builder().id(1L).email("test@example.com").role(memberRole).build();
        memberProfile = MemberProfile.builder().id(1L).userAccount(memberUser).memberCode("MEM-000001").build();

        sport1 = new Sport();
        sport1.setId(10L);
        sport1.setName("Basketball");

        plan = new MembershipPlan();
        plan.setId(100L);
        plan.setPrice(BigDecimal.valueOf(500000));
        plan.setDurationDays(30);
        plan.setMaxSports(1);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setEligibleSports(Set.of(sport1));

    }

    @Test
    void register_ExceedMaxSports_ThrowsException() {
        plan.setMaxSports(0); // Forcing failure
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(10L));

        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));

        assertThrows(BusinessRuleException.class, () -> membershipService.registerMembership(memberUser, req));
    }

    @Test
    void register_SportNotInPool_ThrowsException() {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(99L)); // 99 not in eligible

        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));

        assertThrows(BusinessRuleException.class, () -> membershipService.registerMembership(memberUser, req));
    }

    @Test
    void register_PlanNotActive_ThrowsException() {
        plan.setStatus(PlanStatus.DRAFT);
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(10L));

        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));

        assertThrows(BusinessRuleException.class, () -> membershipService.registerMembership(memberUser, req));
    }

    @Test
    void register_DoublePending_ThrowsException() {
        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(membershipRepository.existsByMemberIdAndStatus(1L, MembershipStatus.PENDING_PAYMENT)).thenReturn(true);

        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(10L));

        assertThrows(ConflictException.class, () -> membershipService.registerMembership(memberUser, req));
    }

    @Test
    void cancel_ByNonOwnerAndNonPrivileged_ThrowsException() {
        Membership m = new Membership();
        m.setId(5L);
        m.setMember(memberProfile);
        m.setStatus(MembershipStatus.PENDING_PAYMENT);

        when(membershipRepository.findById(5L)).thenReturn(Optional.of(m));

        UserAccount otherUser = UserAccount.builder().id(2L).role(memberUser.getRole()).build();

        assertThrows(AccessDeniedException.class, () -> membershipService.cancelMembership(5L, otherUser));
    }

    @Test
    void activate_NewMembership_SetsActiveToday() {
        Membership m = new Membership();
        m.setId(5L);
        m.setMember(memberProfile);
        m.setStatus(MembershipStatus.PENDING_PAYMENT);
        m.setDurationDays(30);

        when(membershipRepository.findById(5L)).thenReturn(Optional.of(m));
        when(membershipRepository.findActiveOrScheduled(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        UserAccount actor = new UserAccount();
        membershipService.activateMembership(5L, actor);

        assertEquals(MembershipStatus.ACTIVE, m.getStatus());
        assertEquals(LocalDate.of(2026, 10, 5), m.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 5).plusDays(30), m.getEndDate());
        assertNotNull(m.getActivatedAt());
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void activate_RenewalWhileActive_SetsScheduledAfterActiveEnd() {
        Membership active = new Membership();
        active.setEndDate(LocalDate.of(2026, 10, 5).plusDays(10));

        Membership m = new Membership();
        m.setId(5L);
        m.setMember(memberProfile);
        m.setStatus(MembershipStatus.PENDING_PAYMENT);
        m.setDurationDays(30);

        when(membershipRepository.findById(5L)).thenReturn(Optional.of(m));
        when(membershipRepository.findActiveOrScheduled(eq(1L), any(), any())).thenReturn(List.of(active));

        UserAccount actor = new UserAccount();
        membershipService.activateMembership(5L, actor);

        assertEquals(MembershipStatus.SCHEDULED, m.getStatus());
        assertEquals(LocalDate.of(2026, 10, 5).plusDays(11), m.getStartDate());
        assertEquals(LocalDate.of(2026, 10, 5).plusDays(11).plusDays(30), m.getEndDate());
    }

    @Test
    void activate_RenewalWhenExpired_SetsActiveToday() {
        // Find active or scheduled returns empty because expired is excluded
        Membership m = new Membership();
        m.setId(5L);
        m.setMember(memberProfile);
        m.setStatus(MembershipStatus.PENDING_PAYMENT);
        m.setDurationDays(30);

        when(membershipRepository.findById(5L)).thenReturn(Optional.of(m));
        when(membershipRepository.findActiveOrScheduled(eq(1L), any(), any())).thenReturn(Collections.emptyList());

        UserAccount actor = new UserAccount();
        membershipService.activateMembership(5L, actor);

        assertEquals(MembershipStatus.ACTIVE, m.getStatus());
        assertEquals(LocalDate.of(2026, 10, 5), m.getStartDate());
    }

    @Test
    void register_LazyMemberProfileCreation_Success() {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(10L));

        when(memberProfileRepository.findById(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(memberUser));
        when(memberProfileRepository.getNextMemberCode()).thenReturn(1L);
        when(memberProfileRepository.save(any(MemberProfile.class))).thenAnswer(i -> {
            MemberProfile p = i.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(membershipRepository.existsByMemberIdAndStatus(anyLong(), any())).thenReturn(false);
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(membershipRepository.getNextRegistrationCode()).thenReturn(1L);
        when(codeFormatter.formatMemberCode(1L)).thenReturn("MEM-0001");
        when(codeFormatter.formatRegistrationCode(1L)).thenReturn("REG-0001");
        when(membershipRepository.findActiveOrScheduled(anyLong(), any(), any())).thenReturn(Collections.emptyList());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> {
            Membership m = i.getArgument(0);
            m.setId(20L);
            return m;
        });

        MembershipResponse res = membershipService.registerMembership(memberUser, req);

        assertNotNull(res);
        verify(memberProfileRepository).save(any(MemberProfile.class));
        
        org.mockito.ArgumentCaptor<AuditEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        assertEquals(AuditAction.MEMBERSHIP_REGISTERED, eventCaptor.getValue().getAction());
        assertEquals("REG-0001", eventCaptor.getValue().getEntityCode());
    }

    @Test
    void register_DuplicateSportsInRequest_DeduplicatesAndPasses() {
        plan.setMaxSports(1);
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(10L, 10L)); // Duplicate sport ID, should become just [10L]

        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(membershipRepository.existsByMemberIdAndStatus(1L, MembershipStatus.PENDING_PAYMENT)).thenReturn(false);
        when(membershipRepository.getNextRegistrationCode()).thenReturn(1L);
        when(codeFormatter.formatRegistrationCode(1L)).thenReturn("REG-0001");
        when(membershipRepository.findActiveOrScheduled(anyLong(), any(), any())).thenReturn(Collections.emptyList());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> {
            Membership m = i.getArgument(0);
            m.setId(20L);
            return m;
        });

        MembershipResponse res = membershipService.registerMembership(memberUser, req);

        assertNotNull(res);
        assertEquals("PENDING_PAYMENT", res.getStatus());
        
        org.mockito.ArgumentCaptor<AuditEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        assertEquals(AuditAction.MEMBERSHIP_REGISTERED, eventCaptor.getValue().getAction());
        assertEquals("REG-0001", eventCaptor.getValue().getEntityCode());
    }

    @Test
    void register_Renewal_LogsRenewal() {
        MembershipRegistrationRequest req = new MembershipRegistrationRequest();
        req.setPlanId(100L);
        req.setSportIds(List.of(10L));

        Membership active = new Membership();
        active.setId(99L);
        active.setStatus(MembershipStatus.ACTIVE);

        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.existsByMemberIdAndStatus(1L, MembershipStatus.PENDING_PAYMENT)).thenReturn(false);
        when(planRepository.findById(100L)).thenReturn(Optional.of(plan));
        when(membershipRepository.getNextRegistrationCode()).thenReturn(2L);
        when(codeFormatter.formatRegistrationCode(2L)).thenReturn("REG-0002");
        when(membershipRepository.findActiveOrScheduled(anyLong(), any(), any())).thenReturn(List.of(active));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(i -> {
            Membership m = i.getArgument(0);
            m.setId(21L);
            return m;
        });

        MembershipResponse res = membershipService.registerMembership(memberUser, req);

        assertNotNull(res);
        org.mockito.ArgumentCaptor<AuditEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        assertEquals(AuditAction.MEMBERSHIP_RENEWED, eventCaptor.getValue().getAction());
        assertEquals("REG-0002", eventCaptor.getValue().getEntityCode());
    }

    @Test
    void processScheduledMemberships_LogsEvents() {
        Membership scheduled = new Membership();
        scheduled.setId(10L);
        scheduled.setRegistrationCode("REG-0010");
        scheduled.setStatus(MembershipStatus.SCHEDULED);

        when(membershipRepository.findByStatusAndStartDateLessThanEqual(eq(MembershipStatus.SCHEDULED), any()))
                .thenReturn(List.of(scheduled));

        membershipService.processScheduledMemberships(LocalDate.now(clock));

        assertEquals(MembershipStatus.ACTIVE, scheduled.getStatus());
        verify(membershipRepository).save(scheduled);

        org.mockito.ArgumentCaptor<AuditEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        assertEquals(AuditAction.MEMBERSHIP_ACTIVATED_BY_JOB, eventCaptor.getValue().getAction());
        assertNull(eventCaptor.getValue().getActor());
        assertEquals("REG-0010", eventCaptor.getValue().getEntityCode());
    }

    @Test
    void processExpiredMemberships_LogsEvents() {
        Membership active = new Membership();
        active.setId(11L);
        active.setRegistrationCode("REG-0011");
        active.setStatus(MembershipStatus.ACTIVE);

        when(membershipRepository.findByStatusAndEndDateLessThan(eq(MembershipStatus.ACTIVE), any()))
                .thenReturn(List.of(active));

        membershipService.processExpiredMemberships(LocalDate.now(clock));

        assertEquals(MembershipStatus.EXPIRED, active.getStatus());
        verify(membershipRepository).save(active);

        org.mockito.ArgumentCaptor<AuditEvent> eventCaptor = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(eventCaptor.capture());
        assertEquals(AuditAction.MEMBERSHIP_EXPIRED_BY_JOB, eventCaptor.getValue().getAction());
        assertNull(eventCaptor.getValue().getActor());
        assertEquals("REG-0011", eventCaptor.getValue().getEntityCode());
    }

    @Test
    void processScheduledMemberships_NoChanges_NoLogs() {
        when(membershipRepository.findByStatusAndStartDateLessThanEqual(eq(MembershipStatus.SCHEDULED), any()))
                .thenReturn(Collections.emptyList());

        membershipService.processScheduledMemberships(LocalDate.now(clock));

        verify(membershipRepository, never()).save(any());
        verify(auditService, never()).record(any());
    }
}
