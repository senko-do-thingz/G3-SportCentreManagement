package com.sportify.catalog.service;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.Membership;
import com.sportify.catalog.entity.MembershipStatus;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.service.impl.CheckInServiceImpl;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.ActivityLogRepository;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CheckInServiceTest {

    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @org.mockito.Spy
    private java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-10-05T00:00:00Z"), java.time.ZoneId.of("UTC"));

    @InjectMocks
    private CheckInServiceImpl checkInService;

    private UserAccount receptionist;
    private MemberProfile memberProfile;

    @BeforeEach
    void setUp() {
        Role recRole = new Role();
        recRole.setCode("RECEPTIONIST");
        receptionist = UserAccount.builder().id(2L).role(recRole).build();

        UserAccount memberUser = UserAccount.builder().id(1L).fullName("Test Member").build();
        memberProfile = MemberProfile.builder().id(1L).userAccount(memberUser).memberCode("MEM-123").build();
    }

    @Test
    void recordCheckIn_NoActiveMembership_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(Collections.emptyList());
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("No membership found", res.getDenialReason());
        verify(activityLogRepository).save(any());
    }

    @Test
    void recordCheckIn_WithActiveMembership_ResultAllowed() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("1"); // Testing ID parsing

        Membership active = new Membership();
        active.setId(5L);
        active.setStatus(MembershipStatus.ACTIVE);
        active.setStartDate(java.time.LocalDate.of(2026, 10, 1));
        active.setEndDate(java.time.LocalDate.of(2026, 10, 10));

        when(memberProfileRepository.findByMemberCode("1")).thenReturn(Optional.empty());
        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(active));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("ALLOWED", res.getResult());
        assertEquals(5L, res.getMembershipId());
        verify(activityLogRepository).save(any());
    }

    @Test
    void recordCheckIn_ActiveButExpired_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership expired = new Membership();
        expired.setId(5L);
        expired.setStatus(MembershipStatus.ACTIVE);
        expired.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        expired.setEndDate(java.time.LocalDate.of(2026, 10, 4)); // Expired yesterday

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(expired));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Membership is EXPIRED", res.getDenialReason());
    }

    @Test
    void recordCheckIn_ExactlyOnEndDate_ResultAllowed() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership active = new Membership();
        active.setId(5L);
        active.setStatus(MembershipStatus.ACTIVE);
        active.setStartDate(java.time.LocalDate.of(2026, 9, 1));
        active.setEndDate(java.time.LocalDate.of(2026, 10, 5)); // Exactly today

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(active));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("ALLOWED", res.getResult());
    }

    @Test
    void recordCheckIn_ExactlyOnStartDate_ResultAllowed() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership active = new Membership();
        active.setId(5L);
        active.setStatus(MembershipStatus.ACTIVE);
        active.setStartDate(java.time.LocalDate.of(2026, 10, 5)); // Exactly today
        active.setEndDate(java.time.LocalDate.of(2026, 11, 5));

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(active));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("ALLOWED", res.getResult());
    }

    @Test
    void recordCheckIn_OnlyPendingPayment_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership pending = new Membership();
        pending.setId(6L);
        pending.setStatus(MembershipStatus.PENDING_PAYMENT);

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(pending));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Membership is PENDING_PAYMENT", res.getDenialReason());
    }

    @Test
    void recordCheckIn_OnlyScheduled_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership scheduled = new Membership();
        scheduled.setId(7L);
        scheduled.setStatus(MembershipStatus.SCHEDULED);
        scheduled.setStartDate(java.time.LocalDate.of(2026, 10, 10)); // future

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(scheduled));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Membership SCHEDULED but not yet started", res.getDenialReason());
    }

    @Test
    void recordCheckIn_OnlyExpired_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership expired = new Membership();
        expired.setId(8L);
        expired.setStatus(MembershipStatus.EXPIRED);

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(expired));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Membership is EXPIRED", res.getDenialReason());
    }

    @Test
    void recordCheckIn_OnlyCancelled_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership cancelled = new Membership();
        cancelled.setId(9L);
        cancelled.setStatus(MembershipStatus.CANCELLED);

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(cancelled));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Membership is CANCELLED", res.getDenialReason());
    }

    @Test
    void recordCheckIn_MixedExpiredAndPending_ResultDeniedWithPendingReason() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership expired = new Membership();
        expired.setId(10L);
        expired.setStatus(MembershipStatus.EXPIRED);
        expired.setStartDate(java.time.LocalDate.of(2026, 8, 1));
        expired.setEndDate(java.time.LocalDate.of(2026, 9, 1));

        Membership pending = new Membership();
        pending.setId(11L);
        pending.setStatus(MembershipStatus.PENDING_PAYMENT);

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        // Repo returns in order of startDate DESC, so expired (has date) comes first, pending (null date) comes last
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(expired, pending));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> i.getArgument(0));

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Membership is PENDING_PAYMENT", res.getDenialReason());
    }

    @Test
    void recordCheckIn_MixedActiveAndPending_ResultAllowed() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership active = new Membership();
        active.setId(12L);
        active.setStatus(MembershipStatus.ACTIVE);
        active.setStartDate(java.time.LocalDate.of(2026, 10, 1));
        active.setEndDate(java.time.LocalDate.of(2026, 11, 1));

        Membership pending = new Membership();
        pending.setId(13L);
        pending.setStatus(MembershipStatus.PENDING_PAYMENT);

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L))).thenReturn(List.of(active, pending));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("ALLOWED", res.getResult());
        assertEquals(12L, res.getMembershipId());
    }
}
