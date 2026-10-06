package com.sportify.catalog.service;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.BookingServiceImpl;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import org.springframework.security.access.AccessDeniedException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ClassSessionRepository sessionRepository;
    @Mock
    private SportPackageRegistrationRepository registrationRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private CodeFormatter codeFormatter;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Sport sport;
    private ClassSession session;
    private SportPackageRegistration packageReg;
    private UserAccount memberUser;
    private MemberProfile memberProfile;

    @BeforeEach
    void setUp() {
        sport = Sport.builder().id(1L).name("Badminton").code("BADMINTON").build();
        session = ClassSession.builder()
                .id(20L)
                .sport(sport)
                .trainingType(TrainingFormat.COACH_LED)
                .sessionDate(LocalDate.of(2026, 10, 6))
                .capacity(10)
                .bookedCount(2)
                .build();

        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        memberUser = UserAccount.builder().id(100L).role(memberRole).fullName("Member User").build();
        memberProfile = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-100").build();

        SportPackage pkg = SportPackage.builder().id(5L).sport(sport).trainingFormat(TrainingFormat.COACH_LED).sessionCount(8).build();
        packageReg = SportPackageRegistration.builder()
                .id(50L)
                .member(memberProfile)
                .sportPackage(pkg)
                .status(PackageRegistrationStatus.ACTIVE)
                .totalSessions(8)
                .remainingSessions(5)
                .startDate(LocalDate.of(2026, 10, 1))
                .endDate(LocalDate.of(2026, 10, 31))
                .build();
    }

    @Test
    void createBooking_Success_DecrementsRemainingSessionsAndIncrementsBookedCount() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatBookingCode(1L)).thenReturn("BKG-001");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(1L);
            return b;
        });

        BookingResponse res = bookingService.createBooking(req, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CONFIRMED, res.getStatus());
        assertEquals(4, packageReg.getRemainingSessions()); // Remaining sessions decremented from 5 to 4
        assertEquals(3, session.getBookedCount()); // Session booked count incremented from 2 to 3
        verify(registrationRepository).save(packageReg);
        verify(sessionRepository).save(session);
    }

    @Test
    void createBooking_AsMember_IgnoresSpoofedMemberIdAndEnforcesActorId() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .memberId(999L) // Spoofed member ID
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(2L);
        when(codeFormatter.formatBookingCode(2L)).thenReturn("BKG-002");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(2L);
            return b;
        });

        BookingResponse res = bookingService.createBooking(req, memberUser);

        assertNotNull(res);
        assertEquals(100L, res.getMemberId()); // Enforced 100L
    }

    @Test
    void createBooking_SessionFull_ThrowsBusinessRuleException() {
        session.setBookedCount(10); // Full capacity

        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Session is fully booked", ex.getMessage());
        verifyNoInteractions(registrationRepository);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_AlreadyBooked_ThrowsBusinessRuleException() {
        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();
        Booking existing = Booking.builder().id(10L).status(BookingStatus.CONFIRMED).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.of(existing));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Member already has a confirmed booking for this session", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_AsOwner_Success_RestoresSessionAndIncrementsRemainingSessions() {
        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals(1, session.getBookedCount()); // Decremented session booked count (seat restored)
        assertEquals(6, packageReg.getRemainingSessions()); // Incremented package remaining sessions (session restored)
    }

    @Test
    void cancelBooking_ByDifferentMember_ThrowsAccessDeniedException() {
        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        UserAccount otherMember = UserAccount.builder().id(999L).role(memberRole).build();

        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile) // Belongs to member 100L
                .session(session)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThrows(AccessDeniedException.class, () -> bookingService.cancelBooking(1L, otherMember));
    }

    @Test
    void createBooking_WithAnotherMembersRegistration_AsMember_ThrowsAccessDeniedException() {
        MemberProfile otherMember = MemberProfile.builder().id(999L).build();
        SportPackageRegistration otherReg = SportPackageRegistration.builder()
                .id(51L)
                .member(otherMember)
                .status(PackageRegistrationStatus.ACTIVE)
                .build();

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(51L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(51L)).thenReturn(Optional.of(otherReg));

        assertThrows(AccessDeniedException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void createBooking_WithPendingPaymentRegistration_ThrowsBusinessRuleException() {
        packageReg.setStatus(PackageRegistrationStatus.PENDING_PAYMENT);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));

        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void createBooking_WithCancelledRegistration_ThrowsBusinessRuleException() {
        packageReg.setStatus(PackageRegistrationStatus.CANCELLED);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));

        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void createBooking_WithExpiredRegistration_ThrowsBusinessRuleException() {
        packageReg.setStatus(PackageRegistrationStatus.EXPIRED);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));

        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void createBooking_WithExpiredByDateRegistration_ThrowsBusinessRuleException() {
        packageReg.setStartDate(LocalDate.of(2026, 9, 1));
        packageReg.setEndDate(LocalDate.of(2026, 9, 30)); // Session is 2026-10-06

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));

        assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
    }

    @Test
    void createBooking_WithWrongSportRegistration_ThrowsBusinessRuleException() {
        Sport otherSport = Sport.builder().id(99L).name("Tennis").code("TENNIS").build();
        SportPackage otherPkg = SportPackage.builder()
                .id(6L)
                .sport(otherSport)
                .trainingFormat(TrainingFormat.COACH_LED)
                .sessionCount(8)
                .build();
        packageReg.setSportPackage(otherPkg);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Package sport does not match session sport", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_WithValidExplicitRegistration_Success() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(10L);
        when(codeFormatter.formatBookingCode(10L)).thenReturn("BKG-010");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(10L);
            return b;
        });

        BookingResponse res = bookingService.createBooking(req, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CONFIRMED, res.getStatus());
        assertEquals(50L, res.getPackageRegistrationId());
        verify(registrationRepository).save(packageReg);
    }

    @Test
    void createBooking_WithWrongTrainingFormat_ThrowsBusinessRuleException() {
        SportPackage selfTrainingPkg = SportPackage.builder()
                .id(7L)
                .sport(sport)
                .trainingFormat(TrainingFormat.SELF_TRAINING)
                .sessionCount(8)
                .build();
        packageReg.setSportPackage(selfTrainingPkg);

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Package training format does not match session training type", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_WithAnotherMembersRegistration_AsStaff_ThrowsBusinessRuleException() {
        Role staffRole = Role.builder().id(2L).code("RECEPTIONIST").build();
        UserAccount staffUser = UserAccount.builder().id(200L).role(staffRole).fullName("Staff User").build();

        MemberProfile otherMember = MemberProfile.builder().id(999L).build();
        SportPackageRegistration otherReg = SportPackageRegistration.builder()
                .id(51L)
                .member(otherMember)
                .status(PackageRegistrationStatus.ACTIVE)
                .build();

        BookingCreateRequest req = BookingCreateRequest.builder()
                .memberId(100L) // booking for member 100L
                .sessionId(20L)
                .packageRegistrationId(51L) // registration belongs to 999L
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(51L)).thenReturn(Optional.of(otherReg));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, staffUser));
        assertEquals("Selected package registration does not belong to the member", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_WithUnpublishedSession_ThrowsBusinessRuleException() {
        session.setStatus(SessionStatus.DRAFT);

        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Session is not published for booking", ex.getMessage());
        verifyNoInteractions(registrationRepository);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_WithPastSessionDate_ThrowsBusinessRuleException() {
        session.setSessionDate(LocalDate.of(2026, 10, 5)); // Past date (clock is 2026-10-06)

        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Cannot book a session in the past", ex.getMessage());
        verifyNoInteractions(registrationRepository);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void cancelBooking_AlreadyCheckedIn_ThrowsBusinessRuleException() {
        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(checkInRepository.existsByBookingIdAndResult(eq(1L), eq(CheckInResult.ALLOWED))).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> bookingService.cancelBooking(1L, memberUser));
    }

    @Test
    void cancelBooking_PastSessionDate_ThrowsBusinessRuleException() {
        ClassSession pastSession = ClassSession.builder()
                .id(21L)
                .sport(sport)
                .sessionDate(LocalDate.of(2026, 10, 5)) // Past date
                .bookedCount(2)
                .build();

        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(pastSession)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(checkInRepository.existsByBookingIdAndResult(eq(1L), eq(CheckInResult.ALLOWED))).thenReturn(false);

        assertThrows(BusinessRuleException.class, () -> bookingService.cancelBooking(1L, memberUser));
    }

    @Test
    void cancelBooking_WithInactivePackageRegistration_DoesNotRestorePackageSessions() {
        packageReg.setStatus(PackageRegistrationStatus.EXPIRED);

        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(checkInRepository.existsByBookingIdAndResult(eq(1L), eq(CheckInResult.ALLOWED))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals(1, session.getBookedCount()); // Decremented session seat
        assertEquals(5, packageReg.getRemainingSessions()); // Remaining sessions NOT incremented (remains 5)
        verify(registrationRepository, never()).save(packageReg);
    }

    @Test
    void recordSelfTrainingAttendance_Success_ReturnsAcknowledgedResponseAndNoSaveCalled() {
        Booking booking = Booking.builder()
                .id(1L)
                .bookingCode("BK-000001")
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        SelfTrainingAttendanceRequest req = SelfTrainingAttendanceRequest.builder()
                .bookingId(1L)
                .build();

        UserAccount receptionist = UserAccount.builder()
                .id(200L)
                .fullName("Receptionist User")
                .role(Role.builder().id(2L).code("RECEPTIONIST").build())
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        SelfTrainingAttendanceResponse response = bookingService.recordSelfTrainingAttendance(req, receptionist);

        assertNotNull(response);
        assertEquals(1L, response.getBookingId());
        assertEquals("Self-training attendance acknowledged (persistence pending Flow 4 implementation)", response.getMessage());
        assertEquals(5, response.getRemainingSessions());
        verify(bookingRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }
}
