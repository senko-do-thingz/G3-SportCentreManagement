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
import com.sportify.core.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
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
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

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
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 30))
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
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
        assertEquals(5, packageReg.getRemainingSessions()); // SQL owns the balance; the managed snapshot is not changed.
        assertEquals(3, session.getBookedCount()); // Session booked count incremented from 2 to 3
        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
        verify(sessionRepository).save(session);
        var order = inOrder(sessionRepository, bookingRepository, registrationRepository);
        order.verify(sessionRepository).findForUpdate(20L);
        order.verify(bookingRepository).findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED);
        order.verify(registrationRepository).findById(50L);
        order.verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        order.verify(sessionRepository).save(session);
        order.verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void createBooking_ZeroRemainingSessionsRejectsBeforeDebitOrBookingWrites() {
        stubExplicitRegistration();
        packageReg.setRemainingSessions(0);

        var error = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(explicitRequest(), memberUser));

        assertEquals("Selected package registration has zero remaining sessions", error.getMessage());
        assertNoBookingWrites();
        verify(registrationRepository, never()).consumeSession(any(), any(), any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = PackageRegistrationStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
    void createBooking_IneligibleRegistrationRejectsBeforeDebitOrBookingWrites(PackageRegistrationStatus status) {
        stubExplicitRegistration();
        packageReg.setStatus(status);

        var error = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(explicitRequest(), memberUser));

        assertEquals("Package registration is not active: " + status, error.getMessage());
        assertNoBookingWrites();
        verify(registrationRepository, never()).consumeSession(any(), any(), any(), any());
    }

    @Test
    void createBooking_ConditionalDebitLosesLastUnitRejectsBeforeCounterOrBookingWrites() {
        stubExplicitRegistration();
        var state = mock(SportPackageRegistrationRepository.BookingState.class);
        when(state.getStatus()).thenReturn(PackageRegistrationStatus.ACTIVE);
        when(state.getStartDate()).thenReturn(packageReg.getStartDate());
        when(state.getEndDate()).thenReturn(packageReg.getEndDate());
        when(registrationRepository.findBookingStateById(50L)).thenReturn(Optional.of(state));

        var error = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(explicitRequest(), memberUser));

        assertEquals("Selected package registration has zero remaining sessions", error.getMessage());
        assertNoBookingWrites();
        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
    }

    @Test
    void createBooking_ConditionalDebitSeesNewInactiveStatusReturnsExistingStatusError() {
        stubExplicitRegistration();
        var state = mock(SportPackageRegistrationRepository.BookingState.class);
        when(state.getStatus()).thenReturn(PackageRegistrationStatus.REFUNDED);
        when(registrationRepository.findBookingStateById(50L)).thenReturn(Optional.of(state));

        var error = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(explicitRequest(), memberUser));

        assertEquals("Package registration is not active: REFUNDED", error.getMessage());
        assertNoBookingWrites();
    }

    @Test
    void createBooking_ConditionalDebitSeesChangedValidityReturnsExistingDateError() {
        stubExplicitRegistration();
        var state = mock(SportPackageRegistrationRepository.BookingState.class);
        when(state.getStatus()).thenReturn(PackageRegistrationStatus.ACTIVE);
        when(state.getStartDate()).thenReturn(session.getSessionDate().plusDays(1));
        when(state.getEndDate()).thenReturn(packageReg.getEndDate());
        when(registrationRepository.findBookingStateById(50L)).thenReturn(Optional.of(state));

        var error = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(explicitRequest(), memberUser));

        assertEquals("Session date 2026-10-06 is outside package validity period (2026-10-07 to 2026-10-31)", error.getMessage());
        assertNoBookingWrites();
    }

    @Test
    void createBooking_AutomaticSelectionConsumesThroughTheSameConditionalCommand() {
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(registrationRepository.findActiveRegistrationsForSport(100L, 1L, PackageRegistrationStatus.ACTIVE, session.getSessionDate()))
                .thenReturn(List.of(packageReg));
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(12L);
        when(codeFormatter.formatBookingCode(12L)).thenReturn("BKG-012");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(BookingStatus.CONFIRMED, bookingService.createBooking(
                BookingCreateRequest.builder().sessionId(20L).build(), memberUser).getStatus());

        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
    }

    private BookingCreateRequest explicitRequest() {
        return BookingCreateRequest.builder().sessionId(20L).packageRegistrationId(50L).build();
    }

    private void stubExplicitRegistration() {
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
    }

    private void assertNoBookingWrites() {
        assertEquals(2, session.getBookedCount());
        verify(sessionRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void createBooking_CancelledSession_RejectsWithoutConsumingPackageOrChangingCounter() {
        session.setStatus(SessionStatus.CANCELLED);
        session.setCancelReason("Weather");
        session.setBookedCount(0);
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(
                BookingCreateRequest.builder().sessionId(20L).build(), memberUser));

        assertEquals("Session is not published for booking", ex.getMessage());
        assertEquals(SessionStatus.CANCELLED, session.getStatus());
        assertEquals("Weather", session.getCancelReason());
        assertEquals(0, session.getBookedCount());
        assertEquals(5, packageReg.getRemainingSessions());
        verify(sessionRepository, never()).save(any());
        verifyNoInteractions(bookingRepository, registrationRepository);
    }

    @Test
    void cancelBooking_AlreadyCancelledAfterSessionLock_RejectsWithoutRefundOrCounterChanges() {
        session.setStatus(SessionStatus.CANCELLED);
        session.setBookedCount(0);
        Booking booking = Booking.builder().id(1L).session(session).member(memberProfile)
                .packageRegistration(packageReg).status(BookingStatus.CANCELLED).build();
        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(20L));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> bookingService.cancelBooking(1L, memberUser));

        assertEquals("Booking is already cancelled", ex.getMessage());
        var order = inOrder(bookingRepository, sessionRepository);
        order.verify(bookingRepository).findSessionIdByBookingId(1L);
        order.verify(sessionRepository).findForUpdate(20L);
        order.verify(bookingRepository).findById(1L);
        assertEquals(0, session.getBookedCount());
        assertEquals(5, packageReg.getRemainingSessions());
        verify(sessionRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(registrationRepository, checkInRepository);
    }

    @Test
    void createBooking_AsMember_IgnoresSpoofedMemberIdAndEnforcesActorId() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .memberId(999L) // Spoofed member ID
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Session is fully booked", ex.getMessage());
        verifyNoInteractions(registrationRepository);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_AlreadyBooked_ThrowsConflictException() {
        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();
        Booking existing = Booking.builder().id(10L).status(BookingStatus.CONFIRMED).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Member already has a confirmed booking for this session", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBooking_SessionFullAndAlreadyBooked_ThrowsConflictException() {
        session.setBookedCount(10); // Full capacity
        BookingCreateRequest req = BookingCreateRequest.builder().sessionId(20L).build();
        Booking existing = Booking.builder().id(10L).status(BookingStatus.CONFIRMED).build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.of(existing));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(req, memberUser));
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

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals(1, session.getBookedCount()); // Decremented session booked count (seat restored)
        assertEquals(5, packageReg.getRemainingSessions()); // Restoration is database arithmetic, not a stale entity save.
        verify(registrationRepository).restoreSession(eq(50L), eq("ACTIVE"), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
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

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
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
        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));

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
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));

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

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
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

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
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

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(checkInRepository.existsByBookingIdAndResult(eq(1L), eq(CheckInResult.ALLOWED))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals(1, session.getBookedCount()); // Decremented session seat
        assertEquals(5, packageReg.getRemainingSessions()); // Remaining sessions NOT incremented (remains 5)
        verify(registrationRepository).restoreSession(eq(50L), eq("ACTIVE"), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
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
        assertEquals("MEM-100", response.getMemberCode());
        assertEquals("Badminton", response.getSportName());
        assertEquals("Receptionist User", response.getRecordedByName());
        assertEquals("Self-training attendance acknowledged (persistence pending Flow 4 implementation)", response.getMessage());
        assertEquals(5, response.getRemainingSessions());
        verify(bookingRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
        verifyNoInteractions(checkInRepository);
    }

    @Test
    void createBooking_SameDayAfterStartTime_ThrowsBusinessRuleException() {
        ClassSession startedSession = ClassSession.builder()
                .id(23L)
                .sport(sport)
                .trainingType(TrainingFormat.COACH_LED)
                .sessionDate(LocalDate.of(2026, 10, 6))
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 30))
                .capacity(10)
                .bookedCount(2)
                .build();

        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(23L)
                .packageRegistrationId(50L)
                .build();

        when(sessionRepository.findForUpdate(23L)).thenReturn(Optional.of(startedSession));
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Cannot book a session that has already started", ex.getMessage());
        verify(bookingRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createBooking_SameDayBeforeStartTime_Success() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED)).thenReturn(Optional.empty());
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(10L);
        when(codeFormatter.formatBookingCode(10L)).thenReturn("BK-000010");
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(200L);
            return b;
        });

        BookingResponse res = bookingService.createBooking(req, memberUser);

        assertNotNull(res);
        assertEquals(200L, res.getId());
        assertEquals(BookingStatus.CONFIRMED, res.getStatus());
        verify(sessionRepository).save(session);
        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void cancelBooking_SameDayAfterStartTime_AsMember_ThrowsBusinessRuleException() {
        ClassSession startedSession = ClassSession.builder()
                .id(23L)
                .sport(sport)
                .trainingType(TrainingFormat.COACH_LED)
                .sessionDate(LocalDate.of(2026, 10, 6))
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 30))
                .bookedCount(2)
                .build();

        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(startedSession)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(checkInRepository.existsByBookingIdAndResult(eq(1L), eq(CheckInResult.ALLOWED))).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> bookingService.cancelBooking(1L, memberUser));
        assertEquals("Cannot cancel a session that has already started", ex.getMessage());
        verify(bookingRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelBooking_SameDayAfterStartTime_AsStaff_Success() {
        ClassSession startedSession = ClassSession.builder()
                .id(23L)
                .sport(sport)
                .trainingType(TrainingFormat.COACH_LED)
                .sessionDate(LocalDate.of(2026, 10, 6))
                .startTime(LocalTime.of(6, 0))
                .endTime(LocalTime.of(7, 30))
                .bookedCount(2)
                .build();

        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(startedSession)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        UserAccount receptionist = UserAccount.builder()
                .id(200L)
                .role(Role.builder().id(2L).code("RECEPTIONIST").build())
                .email("receptionist@sportify.com")
                .build();

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(checkInRepository.existsByBookingIdAndResult(eq(1L), eq(CheckInResult.ALLOWED))).thenReturn(false);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, receptionist);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        verify(bookingRepository).save(any(Booking.class));
        verify(sessionRepository).save(startedSession);
        verify(registrationRepository).restoreSession(eq(50L), eq("ACTIVE"), any(LocalDateTime.class));
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void cancelBooking_AsMember_SetsCancelledByAndReturnsActorNameAndRole() {
        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, memberUser);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals("Member User", res.getCancelledByName());
        assertEquals("MEMBER", res.getCancelledByRole());
        assertEquals(memberUser, booking.getCancelledBy());
    }

    @Test
    void cancelBooking_AsReceptionist_SetsCancelledByAndReturnsActorNameAndRole() {
        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        UserAccount receptionist = UserAccount.builder()
                .id(201L)
                .role(Role.builder().id(2L).code("RECEPTIONIST").build())
                .fullName("Receptionist Staff")
                .email("rec@sportify.com")
                .build();

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, receptionist);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals("Receptionist Staff", res.getCancelledByName());
        assertEquals("RECEPTIONIST", res.getCancelledByRole());
        assertEquals(receptionist, booking.getCancelledBy());
    }

    @Test
    void cancelBooking_AsManager_SetsCancelledByAndReturnsActorNameAndRole() {
        Booking booking = Booking.builder()
                .id(1L)
                .member(memberProfile)
                .session(session)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .build();

        UserAccount manager = UserAccount.builder()
                .id(301L)
                .role(Role.builder().id(3L).code("MANAGER").build())
                .fullName("Center Manager")
                .email("manager@sportify.com")
                .build();

        when(bookingRepository.findSessionIdByBookingId(1L)).thenReturn(Optional.of(booking.getSession().getId()));
        when(sessionRepository.findForUpdate(booking.getSession().getId())).thenReturn(Optional.of(booking.getSession()));
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse res = bookingService.cancelBooking(1L, manager);

        assertNotNull(res);
        assertEquals(BookingStatus.CANCELLED, res.getStatus());
        assertEquals("Center Manager", res.getCancelledByName());
        assertEquals("MANAGER", res.getCancelledByRole());
        assertEquals(manager, booking.getCancelledBy());
    }

    @Test
    void createBooking_WhenDataIntegrityViolationUxBookingActive_ThrowsConflictException() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatBookingCode(1L)).thenReturn("BKG-001");
        when(bookingRepository.save(any(Booking.class))).thenThrow(
                new DataIntegrityViolationException("Cannot insert duplicate key row with unique index 'ux_booking_active'."));

        ConflictException ex = assertThrows(ConflictException.class, () -> bookingService.createBooking(req, memberUser));
        assertEquals("Member already has a confirmed booking for this session", ex.getMessage());
        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        verify(registrationRepository, never()).restoreSession(any(), any(), any());
        verify(registrationRepository, never()).save(any());
    }

    @Test
    void createBooking_WhenDataIntegrityViolationOther_RethrowsException() {
        BookingCreateRequest req = BookingCreateRequest.builder()
                .sessionId(20L)
                .packageRegistrationId(50L)
                .build();

        when(memberProfileRepository.findById(100L)).thenReturn(Optional.of(memberProfile));
        when(sessionRepository.findForUpdate(20L)).thenReturn(Optional.of(session));
        when(bookingRepository.findBySessionIdAndMemberIdAndStatus(20L, 100L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(packageReg));
        when(registrationRepository.consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class))).thenReturn(1);
        when(bookingRepository.getNextBookingCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatBookingCode(1L)).thenReturn("BKG-001");
        when(bookingRepository.save(any(Booking.class))).thenThrow(
                new DataIntegrityViolationException("Cannot insert null into column 'status'."));

        assertThrows(DataIntegrityViolationException.class, () -> bookingService.createBooking(req, memberUser));
        verify(registrationRepository).consumeSession(eq(50L), eq("ACTIVE"), eq(session.getSessionDate()), any(LocalDateTime.class));
        verify(registrationRepository, never()).restoreSession(any(), any(), any());
        verify(registrationRepository, never()).save(any());
    }
}
