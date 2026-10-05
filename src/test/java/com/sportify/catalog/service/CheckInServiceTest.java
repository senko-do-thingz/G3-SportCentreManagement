package com.sportify.catalog.service;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.Membership;
import com.sportify.catalog.entity.MembershipStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.CheckInServiceImpl;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CheckInServiceTest {

    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private SportPackageRegistrationRepository packageRegistrationRepository;
    @Mock
    private MembershipRepository membershipRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private AuditService auditService;
    @org.mockito.Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private CheckInServiceImpl checkInService;

    private UserAccount receptionist;
    private MemberProfile memberProfile;
    private Sport sport;
    private ClassSession classSession;
    private Booking todayBooking;

    @BeforeEach
    void setUp() {
        Role recRole = new Role();
        recRole.setCode("RECEPTIONIST");
        receptionist = UserAccount.builder().id(2L).role(recRole).build();

        UserAccount memberUser = UserAccount.builder().id(1L).fullName("Test Member").build();
        memberProfile = MemberProfile.builder().id(1L).userAccount(memberUser).memberCode("MEM-123").build();

        sport = Sport.builder().id(10L).name("Badminton").build();
        classSession = ClassSession.builder().id(20L).sport(sport).sessionDate(LocalDate.of(2026, 10, 5)).build();
        todayBooking = Booking.builder()
                .id(30L)
                .member(memberProfile)
                .session(classSession)
                .status(BookingStatus.CONFIRMED)
                .build();
    }

    @Test
    void recordCheckIn_NoTodayBooking_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(bookingRepository.findTodayBookingsForMember(eq(1L), eq(LocalDate.of(2026, 10, 5)), eq(BookingStatus.CONFIRMED)))
                .thenReturn(Collections.emptyList());
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("No confirmed booking for today", res.getDenialReason());
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void recordCheckIn_SpecifiedBookingNotFound_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");
        req.setBookingId(999L);

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(bookingRepository.findTodayBookingsForMember(eq(1L), eq(LocalDate.of(2026, 10, 5)), eq(BookingStatus.CONFIRMED)))
                .thenReturn(List.of(todayBooking));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(101L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Specified booking is not scheduled for today or is not confirmed", res.getDenialReason());
    }

    @Test
    void recordCheckIn_AlreadyCheckedIn_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(bookingRepository.findTodayBookingsForMember(eq(1L), eq(LocalDate.of(2026, 10, 5)), eq(BookingStatus.CONFIRMED)))
                .thenReturn(List.of(todayBooking));
        when(checkInRepository.existsByBookingIdAndResult(eq(30L), eq(CheckInResult.ALLOWED)))
                .thenReturn(true);
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(102L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("Already checked in for this session", res.getDenialReason());
    }

    @Test
    void recordCheckIn_WithTodayBookingAndActivePackage_ResultAllowed() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("1"); // By profile ID fallback

        SportPackage pkg = SportPackage.builder().id(50L).name("Badminton Monthly").sport(sport).build();
        SportPackageRegistration registration = SportPackageRegistration.builder()
                .id(60L)
                .member(memberProfile)
                .sportPackage(pkg)
                .status(PackageRegistrationStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .build();

        when(memberProfileRepository.findByMemberCode("1")).thenReturn(Optional.empty());
        when(memberProfileRepository.findById(1L)).thenReturn(Optional.of(memberProfile));
        when(bookingRepository.findTodayBookingsForMember(eq(1L), eq(LocalDate.of(2026, 10, 5)), eq(BookingStatus.CONFIRMED)))
                .thenReturn(List.of(todayBooking));
        when(checkInRepository.existsByBookingIdAndResult(eq(30L), eq(CheckInResult.ALLOWED)))
                .thenReturn(false);
        when(packageRegistrationRepository.findActiveRegistrationsForSport(eq(1L), eq(10L), eq(PackageRegistrationStatus.ACTIVE), eq(LocalDate.of(2026, 10, 5))))
                .thenReturn(List.of(registration));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(103L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("ALLOWED", res.getResult());
        assertEquals(30L, res.getBookingId());
        assertEquals(60L, res.getPackageRegistrationId());
        verify(auditService).record(any(AuditEvent.class));
    }

    @Test
    void recordCheckIn_WithTodayBookingAndLegacyMembership_ResultAllowed() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        Membership active = new Membership();
        active.setId(5L);
        active.setStatus(MembershipStatus.ACTIVE);
        active.setStartDate(LocalDate.of(2026, 10, 1));
        active.setEndDate(LocalDate.of(2026, 10, 10));

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(bookingRepository.findTodayBookingsForMember(eq(1L), eq(LocalDate.of(2026, 10, 5)), eq(BookingStatus.CONFIRMED)))
                .thenReturn(List.of(todayBooking));
        when(checkInRepository.existsByBookingIdAndResult(eq(30L), eq(CheckInResult.ALLOWED)))
                .thenReturn(false);
        when(packageRegistrationRepository.findActiveRegistrationsForSport(eq(1L), eq(10L), eq(PackageRegistrationStatus.ACTIVE), eq(LocalDate.of(2026, 10, 5))))
                .thenReturn(Collections.emptyList());
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L)))
                .thenReturn(List.of(active));
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(104L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("ALLOWED", res.getResult());
        assertEquals(5L, res.getMembershipId());
        assertEquals(30L, res.getBookingId());
    }

    @Test
    void recordCheckIn_WithTodayBookingNoPackageNoMembership_ResultDenied() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("MEM-123");

        when(memberProfileRepository.findByMemberCode("MEM-123")).thenReturn(Optional.of(memberProfile));
        when(bookingRepository.findTodayBookingsForMember(eq(1L), eq(LocalDate.of(2026, 10, 5)), eq(BookingStatus.CONFIRMED)))
                .thenReturn(List.of(todayBooking));
        when(checkInRepository.existsByBookingIdAndResult(eq(30L), eq(CheckInResult.ALLOWED)))
                .thenReturn(false);
        when(packageRegistrationRepository.findActiveRegistrationsForSport(eq(1L), eq(10L), eq(PackageRegistrationStatus.ACTIVE), eq(LocalDate.of(2026, 10, 5))))
                .thenReturn(Collections.emptyList());
        when(membershipRepository.findAllByMemberIdOrderByStartDateDesc(eq(1L)))
                .thenReturn(Collections.emptyList());
        when(checkInRepository.save(any(CheckIn.class))).thenAnswer(i -> {
            CheckIn c = i.getArgument(0);
            c.setId(105L);
            return c;
        });

        CheckInResponse res = checkInService.recordCheckIn(req, receptionist);

        assertEquals("DENIED", res.getResult());
        assertEquals("No active sport package", res.getDenialReason());
    }

    @Test
    void recordCheckIn_MemberNotFound_ThrowsResourceNotFoundException() {
        CheckInRequest req = new CheckInRequest();
        req.setIdentifier("NON-EXISTENT");

        when(memberProfileRepository.findByMemberCode("NON-EXISTENT")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> checkInService.recordCheckIn(req, receptionist));
    }

    @Test
    void getCheckInHistory_ReturnsResponses() {
        CheckIn record = CheckIn.builder()
                .id(200L)
                .member(memberProfile)
                .result(CheckInResult.ALLOWED)
                .build();
        when(checkInRepository.findByMemberIdOrderByCheckedInAtDesc(eq(1L))).thenReturn(List.of(record));

        List<CheckInResponse> history = checkInService.getCheckInHistory(1L);

        assertEquals(1, history.size());
        assertEquals(200L, history.get(0).getId());
        assertEquals("ALLOWED", history.get(0).getResult());
    }
}
