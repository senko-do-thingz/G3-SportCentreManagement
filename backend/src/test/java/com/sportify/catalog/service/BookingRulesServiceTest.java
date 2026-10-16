package com.sportify.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.impl.BookingServiceImpl;
import com.sportify.core.audit.*;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.MemberProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Transport mocks verify decisions and command ordering; database rollback is covered by SQL Server tests. */
@ExtendWith(MockitoExtension.class)
class BookingRulesServiceTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    @Mock BookingRepository bookings;
    @Mock ClassSessionRepository sessions;
    @Mock SportPackageRegistrationRepository registrations;
    @Mock MemberProfileRepository members;
    @Mock CheckInRepository checkIns;
    @Mock CodeFormatter codes;
    @Mock EntityManager em;
    @Mock AuditService audit;
    @Spy Clock clock = Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
    @InjectMocks BookingServiceImpl service;
    UserAccount actor;
    MemberProfile member;
    ClassSession session;
    SportPackageRegistration registration;
    Booking booking;

    @BeforeEach void fixture() {
        actor = account(30L, "MEMBER");
        member = MemberProfile.builder().id(actor.getId()).memberCode("MEM-30").userAccount(actor).build();
        var sport = Sport.builder().id(10L).name("Badminton").build();
        session = ClassSession.builder().id(1L).sport(sport).sessionDate(TODAY.plusDays(1))
                .startTime(LocalTime.NOON).endTime(LocalTime.of(13, 30)).capacity(10).bookedCount(0)
                .sportClass(SportClass.builder().id(4L).name("Fundamentals").isActive(true).build())
                .facility(Facility.builder().id(5L).name("Court 1").isActive(true).build())
                .coach(CoachProfile.builder().id(6L).userAccount(account(6L, "COACH")).build()).build();
        registration = SportPackageRegistration.builder().id(2L).member(member).registrationCode("REG-2")
                .sportPackage(SportPackage.builder().id(7L).name("Purchased package").sport(sport)
                        .trainingFormat(TrainingFormat.COACH_LED).build())
                .status(PackageRegistrationStatus.ACTIVE).totalSessions(5).remainingSessions(3)
                .startDate(TODAY.minusDays(1)).endDate(TODAY.plusDays(30)).build();
        booking = Booking.builder().id(3L).bookingCode("BK-3").member(member).session(session)
                .packageRegistration(registration).status(BookingStatus.CONFIRMED).bookedAt(NOW).build();
        lenient().when(members.findById(30L)).thenReturn(Optional.of(member));
        lenient().when(sessions.findForUpdate(1L)).thenReturn(Optional.of(session));
        lenient().when(sessions.findVisibleById(eq(1L), any(), any())).thenReturn(Optional.of(session));
        lenient().when(sessions.reserveSeat(eq(1L), any())).thenReturn(1);
        lenient().when(sessions.releaseSeats(eq(1L), eq(1), any())).thenReturn(1);
        lenient().when(registrations.findById(2L)).thenReturn(Optional.of(registration));
        lenient().when(registrations.consumeSession(eq(2L), eq("ACTIVE"), any(), any())).thenReturn(1);
        lenient().when(bookings.getNextBookingCodeSequence()).thenReturn(33L);
        lenient().when(codes.formatBookingCode(33L)).thenReturn("BK-33");
        lenient().when(bookings.save(any())).thenAnswer(i -> {
            Booking b = i.getArgument(0); if (b.getId() == null) b.setId(3L); return b;
        });
        lenient().when(bookings.findSessionIdByBookingId(3L)).thenReturn(Optional.of(1L));
        lenient().when(bookings.findById(3L)).thenReturn(Optional.of(booking));
        lenient().when(bookings.cancelConfirmedSessionBookings(any(), any(), any(), any(), any())).thenReturn(1);
    }

    static UserAccount account(Long id, String role) {
        return UserAccount.builder().id(id).fullName(role + " name").email(role.toLowerCase() + "@private.test")
                .role(Role.builder().code(role).build()).status("ACTIVE").build();
    }
    BookingCreateRequest request() { return BookingCreateRequest.builder().sessionId(1L).packageRegistrationId(2L).build(); }

    @Test void success_reservesBeforeCreditAndAuditsInSameTransactionWithOneClockRead() {
        var result = service.createBooking(request(), actor);
        assertThat(result.getPackageRegistrationId()).isEqualTo(2L);
        assertThat(result.getClassId()).isEqualTo(4L);
        assertThat(result.getClassName()).isEqualTo("Fundamentals");
        assertThat(result.getCoachId()).isEqualTo(6L);
        assertThat(result.getFacilityName()).isEqualTo("Court 1");
        assertThat(result.getPackageName()).isEqualTo("Purchased package");
        var order = inOrder(sessions, em, registrations, bookings, audit);
        order.verify(sessions).findForUpdate(1L);
        order.verify(em).refresh(session);
        order.verify(sessions).reserveSeat(1L, NOW);
        order.verify(em).refresh(session);
        order.verify(registrations).consumeSession(2L, "ACTIVE", session.getSessionDate(), NOW);
        order.verify(bookings).save(any());
        order.verify(bookings).flush();
        var event = ArgumentCaptor.forClass(AuditEvent.class);
        order.verify(audit).record(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo(AuditAction.BOOKING_CREATED);
        assertThat(event.getValue().isRequiresNew()).isFalse();
        assertThat(event.getValue().getActor()).isSameAs(actor);
        verify(clock).instant();
        verify(registrations, never()).save(any());
        verify(sessions, never()).save(any());
    }

    @Test void explicitExhaustedSelectionNeverSubstitutesAnotherUsableRegistration() {
        registration.setRemainingSessions(0);
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_SESSIONS_LEFT"));
        verify(registrations, never()).findForSessionEligibility(any(), any(), any());
        verify(sessions, never()).reserveSeat(any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING_PAYMENT", "CANCELLED", "REFUNDED", "EXPIRED", "FUTURE", "EXPIRED_DATE", "SPORT", "FORMAT"})
    void selectedPackage_reportsSpecificReasonWithoutBookingWrites(String failure) {
        String code = "NO_PACKAGE";
        switch (failure) {
            case "FUTURE" -> { registration.setStartDate(session.getSessionDate().plusDays(1)); code = "PACKAGE_NOT_STARTED_BY_THEN"; }
            case "EXPIRED_DATE" -> { registration.setEndDate(TODAY); code = "PACKAGE_EXPIRED_BY_THEN"; }
            case "SPORT" -> registration.getSportPackage().setSport(Sport.builder().id(99L).build());
            case "FORMAT" -> registration.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
            default -> { registration.setStatus(PackageRegistrationStatus.valueOf(failure)); if (failure.equals("EXPIRED")) code = "PACKAGE_EXPIRED_BY_THEN"; }
        }
        String expected = code;
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo(expected));
        verify(sessions, never()).reserveSeat(any(), any());
        verifyNoInteractions(audit);
    }

    @Test void autoSelection_reusesOwnedSportFormatAndDeterministicBe11Order() {
        var later = SportPackageRegistration.builder().id(8L).member(member).sportPackage(registration.getSportPackage())
                .status(PackageRegistrationStatus.ACTIVE).startDate(TODAY).endDate(TODAY.plusDays(40)).remainingSessions(2).build();
        var wrongFormat = SportPackageRegistration.builder().id(9L).member(member)
                .sportPackage(SportPackage.builder().sport(session.getSport()).trainingFormat(TrainingFormat.SELF_TRAINING).build())
                .status(PackageRegistrationStatus.ACTIVE).startDate(TODAY).endDate(TODAY.plusDays(3)).remainingSessions(3).build();
        when(registrations.findForSessionEligibility(30L, 10L, TrainingFormat.COACH_LED)).thenReturn(List.of(later, wrongFormat, registration));
        var request = BookingCreateRequest.builder().sessionId(1L).build();
        assertThat(service.createBooking(request, actor).getPackageRegistrationId()).isEqualTo(2L);
        verify(registrations).consumeSession(2L, "ACTIVE", session.getSessionDate(), NOW);
    }

    @Test void noMatchingPackage_returnsNoPackageReason() {
        assertThatThrownBy(() -> service.createBooking(BookingCreateRequest.builder().sessionId(1L).build(), actor))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("NO_PACKAGE"));
    }

    @Test void realFullCountBlocksDespiteStaleLowCacheWith409FullCode() {
        when(bookings.countBySessionIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(10L);
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("FULL"));
        verify(registrations, never()).consumeSession(any(), any(), any(), any());
    }

    @Test void cachedCapacityGuardPreservesBe11DistinctionFromActualFull() {
        session.setBookedCount(10);
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("CAPACITY_UNAVAILABLE"));
    }

    @Test void duplicatePrecedesFullAndKeeps409Reason() {
        session.setBookedCount(10);
        when(bookings.findBySessionIdAndMemberIdAndStatus(1L, 30L, BookingStatus.CONFIRMED)).thenReturn(Optional.of(booking));
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("ALREADY_BOOKED"));
        verify(bookings, never()).countBySessionIdAndStatus(any(), any());
    }

    @Test void noLongerVisibleAssignmentFailsFreshWriteDespiteEarlierEligibility() {
        when(sessions.findVisibleById(eq(1L), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("SESSION_UNAVAILABLE"));
        verify(sessions, never()).reserveSeat(any(), any());
    }

    @Test void conditionalSeatFailureDoesNotConsumeCreditOrSaveBooking() {
        when(sessions.reserveSeat(1L, NOW)).thenReturn(0);
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("FULL"));
        verify(registrations, never()).consumeSession(any(), any(), any(), any());
        verify(bookings, never()).save(any());
    }

    @Test void creditRacePropagatesRollbackExceptionWithoutManualCompensation() {
        when(registrations.consumeSession(any(), any(), any(), any())).thenReturn(0);
        var state = mock(SportPackageRegistrationRepository.BookingState.class);
        when(state.getStatus()).thenReturn(PackageRegistrationStatus.ACTIVE);
        when(state.getStartDate()).thenReturn(registration.getStartDate());
        when(state.getEndDate()).thenReturn(registration.getEndDate());
        when(registrations.findBookingStateById(2L)).thenReturn(Optional.of(state));
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_SESSIONS_LEFT"));
        verify(sessions).reserveSeat(1L, NOW);
        verify(bookings, never()).save(any());
        verify(registrations, never()).restoreSession(any(), any(), any());
    }

    @Test void deferredUniqueFailureIsMappedButUnrelatedIntegrityFailureIsNotConcealed() {
        doThrow(new DataIntegrityViolationException("unique index ux_booking_active")).when(bookings).flush();
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("ALREADY_BOOKED"));
        verifyNoInteractions(audit);
        reset(bookings);
        var original = new DataIntegrityViolationException("different constraint");
        when(bookings.save(any())).thenThrow(original);
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isSameAs(original);
    }

    @Test void auditFailureEscapesAndDoesNotAttemptNontransactionalCreditCompensation() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(any());
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOf(IllegalStateException.class);
        verify(registrations, never()).restoreSession(any(), any(), any());
    }

    @ParameterizedTest @ValueSource(longs = {-1L, 0L})
    void invalidSessionIdsFailBeforeRepositoryAccess(long id) {
        var request = request(); request.setSessionId(id);
        assertThatThrownBy(() -> service.createBooking(request, actor)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(members, sessions);
    }

    @Test void missingSessionAndSelectedRegistrationPreserve404ExceptionPolicy() {
        when(sessions.findForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOf(ResourceNotFoundException.class);
        when(sessions.findForUpdate(1L)).thenReturn(Optional.of(session));
        when(registrations.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test void selfTrainingSupportsNullCoachAndClassWithoutSubstitution() {
        session.setCoach(null); session.setSportClass(null); session.setTrainingType(TrainingFormat.SELF_TRAINING);
        registration.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
        var response = service.createBooking(request(), actor);
        assertThat(response.getCoachId()).isNull(); assertThat(response.getCoachName()).isNull();
        assertThat(response.getClassId()).isNull(); assertThat(response.getPackageId()).isEqualTo(7L);
    }

    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void existingStaffCreationCanTargetAMemberWithoutChangingSelectedPackage(String role) {
        var request = request(); request.setMemberId(30L);
        assertThat(service.createBooking(request, account(99L, role)).getMemberId()).isEqualTo(30L);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1})
    void memberCancellationAtOrBeforeCutoffRestoresOnceAndAuditsMetadata(int beforeSeconds) {
        session.setSessionDate(TODAY); session.setStartTime(LocalTime.NOON.plusSeconds(beforeSeconds));
        var response = service.cancelBooking(3L, actor);
        assertThat(response.getCancelledAt()).isEqualTo(NOW);
        assertThat(response.getCancelledById()).isEqualTo(30L);
        assertThat(response.getCancelReason()).isEqualTo("Cancelled by user");
        verify(bookings).cancelConfirmedSessionBookings(List.of(3L), 1L, 30L, "Cancelled by user", NOW);
        verify(sessions).releaseSeats(1L, 1, NOW);
        verify(registrations).restoreSession(2L, "ACTIVE", NOW);
        var event = ArgumentCaptor.forClass(AuditEvent.class); verify(audit).record(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo(AuditAction.BOOKING_CANCELLED);
        assertThat(event.getValue().isRequiresNew()).isFalse();
        verify(clock).instant();
    }

    @Test void memberAfterCutoffGets400ReasonWithNoCounterChanges() {
        session.setSessionDate(TODAY); session.setStartTime(LocalTime.of(11, 59, 59));
        assertThatThrownBy(() -> service.cancelBooking(3L, actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("CANCELLATION_CUTOFF"));
        verify(sessions, never()).releaseSeats(any(), anyInt(), any());
        verify(registrations, never()).restoreSession(any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void staffMayCancelAfterMemberCutoffIncludingInProgressButNotCheckedIn(String role) {
        session.setSessionDate(TODAY); session.setStartTime(LocalTime.of(9, 0)); session.setEndTime(LocalTime.of(11, 0));
        assertThat(service.cancelBooking(3L, account(99L, role)).getCancelledByRole()).isEqualTo(role);
    }

    @ParameterizedTest @ValueSource(strings = {"COMPLETED", "CANCELLED", "ENDED", "CHECKED_IN", "OTHER_OWNER"})
    void historicalCheckedInOrForeignBookingsCannotBeCancelled(String scenario) {
        UserAccount caller = actor;
        switch (scenario) {
            case "ENDED" -> { session.setSessionDate(TODAY); session.setEndTime(LocalTime.of(10, 0)); caller = account(99L, "MANAGER"); }
            case "CHECKED_IN" -> when(checkIns.existsByBookingIdAndResult(3L, CheckInResult.ALLOWED)).thenReturn(true);
            case "OTHER_OWNER" -> caller = account(88L, "MEMBER");
            default -> session.setStatus(SessionStatus.valueOf(scenario));
        }
        UserAccount finalCaller = caller;
        assertThatThrownBy(() -> service.cancelBooking(3L, finalCaller)).isInstanceOfAny(BusinessRuleException.class, AccessDeniedException.class);
        verify(registrations, never()).restoreSession(any(), any(), any());
    }

    @Test void lostConditionalCancellationCannotRestoreSeatOrCreditTwice() {
        when(bookings.cancelConfirmedSessionBookings(any(), any(), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.cancelBooking(3L, actor)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("ALREADY_CANCELLED"));
        verify(sessions, never()).releaseSeats(any(), anyInt(), any());
        verify(registrations, never()).restoreSession(any(), any(), any());
    }

    @Test void pagedHistoryUsesAuthenticatedOwnerStableSqlSortAndPageMetadata() {
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        when(bookings.findMyBookings(eq(30L), eq("UPCOMING"), eq(TODAY), eq(NOW.toLocalTime()), any()))
                .thenReturn(new PageImpl<>(List.of(booking), PageRequest.of(1, 2), 5));
        var result = service.getMyBookings(actor, BookingWhen.UPCOMING, 1, 2);
        assertThat(result.page()).isEqualTo(1); assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        verify(bookings).findMyBookings(eq(30L), eq("UPCOMING"), eq(TODAY), eq(NOW.toLocalTime()), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("session.sessionDate", "session.startTime", "id"));
    }

    @Test void filteredLegacyArrayUsesDatabaseFilterWithoutInMemoryPaging() {
        when(bookings.findMyBookings(eq(30L), eq("PAST"), any(), any(), any())).thenReturn(new PageImpl<>(List.of(booking)));
        assertThat(service.getMyBookings(actor, BookingWhen.PAST)).hasSize(1);
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findMyBookings(eq(30L), eq("PAST"), any(), any(), page.capture());
        assertThat(page.getValue().isUnpaged()).isTrue();
        assertThat(page.getValue().getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test void legacyCancelledBookingMetadataDoesNotExposeGeneratedActorEmail() throws Exception {
        booking.setCancelledBy(account(99L, "MANAGER")); booking.setCancelReason("Cancelled by user: manager@private.test");
        when(bookings.findByMemberIdOrderByBookedAtDesc(30L)).thenReturn(List.of(booking));
        var response = service.getMyBookings(actor).getFirst();
        assertThat(response.getCancelledById()).isEqualTo(99L);
        assertThat(response.getCancelReason()).isEqualTo("Cancelled by user");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response)).doesNotContain("@private.test", "passwordHash", "phone");
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101", "2147483647,100"})
    void unsafePaginationFailsBeforeQuery(int page, int size) {
        assertThatThrownBy(() -> service.getMyBookings(actor, BookingWhen.PAST, page, size)).isInstanceOfSatisfying(BusinessRuleException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_PAGINATION"));
        verify(bookings, never()).findMyBookings(any(), any(), any(), any(), any());
    }

    @ParameterizedTest @ValueSource(strings = {"NULL", "NO_ID", "NO_ROLE", "INACTIVE", "COACH"})
    void directServiceCallsRejectInvalidActorsBeforeDatabaseAccess(String scenario) {
        UserAccount caller = account(30L, "MEMBER");
        switch (scenario) {
            case "NULL" -> caller = null;
            case "NO_ID" -> caller.setId(null);
            case "NO_ROLE" -> caller.setRole(null);
            case "INACTIVE" -> caller.setStatus("INACTIVE");
            default -> caller.setRole(Role.builder().code("COACH").build());
        }
        UserAccount invalid = caller;
        assertThatThrownBy(() -> service.createBooking(request(), invalid)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.cancelBooking(3L, invalid)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getMyBookings(invalid, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(bookings, sessions, members);
    }
}
