package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

import static com.sportify.catalog.dto.SessionDetailResponse.EligibilityReason.*;
import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberScheduleServiceImplTest {
    @Mock ClassSessionRepository sessions;
    @Mock BookingRepository bookings;
    @Mock SportPackageRegistrationRepository registrations;
    @Mock MemberProfileRepository members;
    MemberScheduleServiceImpl service;
    UserAccount actor;

    @BeforeEach void setUp() {
        service = new MemberScheduleServiceImpl(sessions, bookings, registrations, members, CLOCK);
        actor = account("MEMBER", 2);
    }

    static Stream<SessionSearchQuery> filters() {
        return Stream.of(
                new SessionSearchQuery(null, null, null, null, null, null, 0, 20),
                new SessionSearchQuery(10L, null, null, null, null, null, 0, 20),
                new SessionSearchQuery(null, TODAY, null, null, null, null, 0, 20),
                new SessionSearchQuery(null, null, TODAY.plusDays(3), null, null, null, 0, 20),
                new SessionSearchQuery(null, null, null, TrainingFormat.SELF_TRAINING, null, null, 0, 20),
                new SessionSearchQuery(null, null, null, null, ClassLevel.ADVANCED, null, 0, 20),
                new SessionSearchQuery(null, null, null, null, null, 20L, 0, 20),
                new SessionSearchQuery(10L, TODAY, TODAY.plusDays(3), TrainingFormat.COACH_LED, ClassLevel.BEGINNER, 20L, 1, 2));
    }

    @ParameterizedTest @MethodSource("filters")
    void search_forwardsEveryAndFilterAndClockAndRetainsServerMetadataAndOrder(SessionSearchQuery q) {
        var first = session();
        var second = session(); second.setId(101L);
        when(sessions.searchVisible(eq(q.sportId()), eq(q.from()), eq(q.to()), eq(q.format()),
                eq(q.level() == null ? null : q.level().name()), eq(q.coachId()), eq(TODAY), eq(NOW), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(first, second), call.getArgument(8), 45));
        when(bookings.countBySessionIds(List.of(100L, 101L), BookingStatus.CONFIRMED))
                .thenReturn(List.of(count(100, 7), count(101, 10)));
        var result = service.listSessions(q, actor);
        assertThat(result.page()).isEqualTo(q.page());
        assertThat(result.size()).isEqualTo(q.size());
        assertThat(result.totalElements()).isEqualTo(45);
        assertThat(result.content()).extracting(SessionResponse::id).containsExactly(100L, 101L);
        assertThat(result.content().getFirst().freeSeats()).isEqualTo(3);
        assertThat(result.content().getLast().full()).isTrue();
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(sessions).searchVisible(any(), any(), any(), any(), any(), any(), eq(TODAY), eq(NOW), page.capture());
        assertThat(page.getValue().getSort().stream().map(o -> o.getProperty() + ":" + o.getDirection()).toList())
                .containsExactly("sessionDate:ASC", "startTime:ASC", "id:ASC");
        verifyNoInteractions(members, registrations);
    }

    @Test void search_emptyOrOutOfRangePageSkipsCountQuery() {
        var q = new SessionSearchQuery(null, null, null, null, null, null, 9, 20);
        when(sessions.searchVisible(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(8), 0));
        var result = service.listSessions(q, actor);
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(9);
        assertThat(result.totalElements()).isZero();
        verifyNoInteractions(bookings);
    }

    static Stream<SessionSearchQuery> invalidQueries() {
        return Stream.of(
                new SessionSearchQuery(null, null, null, null, null, null, -1, 20),
                new SessionSearchQuery(null, null, null, null, null, null, 0, 0),
                new SessionSearchQuery(null, null, null, null, null, null, 0, 101),
                new SessionSearchQuery(null, null, null, null, null, null, Integer.MAX_VALUE, 100),
                new SessionSearchQuery(0L, null, null, null, null, null, 0, 20),
                new SessionSearchQuery(null, null, null, null, null, -1L, 0, 20),
                new SessionSearchQuery(null, TODAY.plusDays(1), TODAY, null, null, null, 0, 20),
                new SessionSearchQuery(null, LocalDate.of(0, 1, 1), null, null, null, null, 0, 20),
                new SessionSearchQuery(null, null, LocalDate.of(10000, 1, 1), null, null, null, 0, 20));
    }

    @ParameterizedTest @MethodSource("invalidQueries") @NullSource
    void search_rejectsUnsafeParametersBeforeQuery(SessionSearchQuery q) {
        assertThatThrownBy(() -> service.listSessions(q, actor)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(sessions, bookings, members, registrations);
    }

    private void detail(long count) {
        when(sessions.findVisibleById(100L, TODAY, NOW)).thenReturn(Optional.of(session()));
        when(bookings.countBySessionIds(List.of(100L), BookingStatus.CONFIRMED)).thenReturn(List.of(count(100, count)));
    }

    private void member() {
        when(members.findById(2L)).thenReturn(Optional.of(MemberProfile.builder().id(2L).build()));
    }

    private void packages(SportPackageRegistration... rows) {
        when(registrations.findForSessionEligibility(2L, 10L, TrainingFormat.COACH_LED)).thenReturn(List.of(rows));
    }

    @ParameterizedTest @CsvSource({"10,7,3,false", "10,10,0,true", "10,11,0,true", "0,0,0,true"})
    void capacity_usesConfirmedRowsNotStaleCacheAndNeverReturnsNegativeFreeSeats(int capacity, long count, long free, boolean full) {
        var s = session(); s.setCapacity(capacity); s.setBookedCount(99);
        when(sessions.findVisibleById(100L, TODAY, NOW)).thenReturn(Optional.of(s));
        when(bookings.countBySessionIds(List.of(100L), BookingStatus.CONFIRMED)).thenReturn(List.of(count(100, count)));
        var result = service.getSession(100L, account("MANAGER", 3)).session();
        assertThat(result.bookedCount()).isEqualTo(count);
        assertThat(result.freeSeats()).isEqualTo(free);
        assertThat(result.full()).isEqualTo(full);
        assertThat(s.getBookedCount()).isEqualTo(99);
        verify(sessions, never()).save(any());
    }

    @Test void selfTraining_nullCoachAndLegacyNullClassAreRepresentedWithoutFakeCoach() {
        var s = session(); s.setCoach(null); s.setSportClass(null); s.setTrainingType(TrainingFormat.SELF_TRAINING);
        when(sessions.findVisibleById(100L, TODAY, NOW)).thenReturn(Optional.of(s));
        member();
        var r = registration(200); r.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
        when(registrations.findForSessionEligibility(2L, 10L, TrainingFormat.SELF_TRAINING)).thenReturn(List.of(r));
        var result = service.getSession(100L, actor);
        assertThat(result.eligible()).isTrue();
        assertThat(result.session().coachId()).isNull();
        assertThat(result.session().coachName()).isNull();
        assertThat(result.session().classId()).isNull();
        assertThat(result.session().facilityName()).isEqualTo("Test court");
        assertThat(result.session().capacity()).isEqualTo(10);
    }

    @ParameterizedTest @ValueSource(strings = {"START", "END", "MIDDLE"})
    void eligibility_usesPurchasedInclusiveDatesAndReturnsUsableIds(String bound) {
        detail(7); member(); var r = registration(200);
        if (bound.equals("START")) r.setStartDate(TODAY.plusDays(1));
        if (bound.equals("END")) r.setEndDate(TODAY.plusDays(1));
        packages(r);
        var result = service.getSession(100L, actor);
        assertThat(result.eligible()).isTrue();
        assertThat(result.memberContext()).isTrue();
        assertThat(result.alreadyBooked()).isFalse();
        assertThat(result.reason()).isNull();
        assertThat(result.suitableRegistrations()).extracting(SessionDetailResponse.SuitableRegistration::registrationId).containsExactly(200L);
        assertThat(result.suitableRegistrations().getFirst().remainingSessions()).isEqualTo(5);
        verify(bookings).findBySessionIdAndMemberIdAndStatus(100L, 2L, BookingStatus.CONFIRMED);
    }

    @ParameterizedTest @ValueSource(strings = {"NONE", "SPORT", "FORMAT", "OWNER", "PENDING_PAYMENT", "CANCELLED", "REFUNDED"})
    void eligibility_noPackageForMissingUnownedMismatchedOrUnpaidRegistration(String scenario) {
        detail(0); member(); var r = registration(200);
        switch (scenario) {
            case "SPORT" -> r.getSportPackage().getSport().setId(999L);
            case "FORMAT" -> r.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
            case "OWNER" -> r.getMember().setId(999L);
            case "NONE" -> { }
            default -> r.setStatus(PackageRegistrationStatus.valueOf(scenario));
        }
        if (scenario.equals("NONE")) packages(); else packages(r);
        var result = service.getSession(100L, actor);
        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(NO_PACKAGE);
        assertThat(result.suitableRegistrations()).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"ZERO", "EXPIRED_DATE", "EXPIRED_STATUS", "NOT_STARTED"})
    void eligibility_reportsSpecificCreditAndDateReasons(String scenario) {
        detail(0); member(); var r = registration(200);
        var expected = switch (scenario) {
            case "ZERO" -> { r.setRemainingSessions(0); yield NO_SESSIONS_LEFT; }
            case "EXPIRED_DATE" -> { r.setEndDate(TODAY); yield PACKAGE_EXPIRED_BY_THEN; }
            case "EXPIRED_STATUS" -> { r.setStatus(PackageRegistrationStatus.EXPIRED); yield PACKAGE_EXPIRED_BY_THEN; }
            default -> { r.setStartDate(TODAY.plusDays(2)); yield PACKAGE_NOT_STARTED_BY_THEN; }
        };
        packages(r);
        var result = service.getSession(100L, actor);
        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(expected);
        assertThat(result.suitableRegistrations()).isEmpty();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void eligibility_multiplePackagesDoNotDependOnRowOrder(boolean reverse) {
        detail(0); member();
        var first = registration(201); first.setEndDate(TODAY.plusDays(5));
        var second = registration(200); second.setEndDate(TODAY.plusDays(5));
        var exhausted = registration(202); exhausted.setRemainingSessions(0);
        var expired = registration(203); expired.setEndDate(TODAY);
        var rows = new ArrayList<>(List.of(first, exhausted, expired, second));
        if (reverse) Collections.reverse(rows);
        packages(rows.toArray(SportPackageRegistration[]::new));
        var result = service.getSession(100L, actor);
        assertThat(result.eligible()).isTrue();
        assertThat(result.suitableRegistrations()).extracting(SessionDetailResponse.SuitableRegistration::registrationId).containsExactly(200L, 201L);
    }

    @Test void precedence_alreadyBookedWinsOverFullAndPackageFailure() {
        detail(10); member();
        when(bookings.findBySessionIdAndMemberIdAndStatus(100L, 2L, BookingStatus.CONFIRMED)).thenReturn(Optional.of(Booking.builder().id(500L).build()));
        var result = service.getSession(100L, actor);
        assertThat(result.reason()).isEqualTo(ALREADY_BOOKED);
        assertThat(result.alreadyBooked()).isTrue();
        assertThat(result.bookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(result.suitableRegistrations()).isEmpty();
        verifyNoInteractions(registrations);
    }

    @Test void precedence_fullWinsOverPackageFailure() {
        detail(10); member();
        assertThat(service.getSession(100L, actor).reason()).isEqualTo(FULL);
        verifyNoInteractions(registrations);
    }

    @Test void staleCachedCapacityThatBlocksExistingBookingIsNotFalselyEligibleOrFull() {
        var s = session(); s.setBookedCount(10);
        when(sessions.findVisibleById(100L, TODAY, NOW)).thenReturn(Optional.of(s));
        when(bookings.countBySessionIds(List.of(100L), BookingStatus.CONFIRMED)).thenReturn(List.of(count(100, 7)));
        member();
        var result = service.getSession(100L, actor);
        assertThat(result.session().freeSeats()).isEqualTo(3);
        assertThat(result.session().full()).isFalse();
        assertThat(result.eligible()).isFalse();
        assertThat(result.reason()).isEqualTo(CAPACITY_UNAVAILABLE);
        verifyNoInteractions(registrations);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void precedence_validWindowWithZeroCreditsWinsOverOtherExpiredOrFuturePackages(boolean reverse) {
        detail(0); member();
        var zero = registration(200); zero.setRemainingSessions(0);
        var old = registration(201); old.setEndDate(TODAY);
        var future = registration(202); future.setStartDate(TODAY.plusDays(2));
        packages(reverse ? new SportPackageRegistration[]{future, old, zero} : new SportPackageRegistration[]{zero, old, future});
        assertThat(service.getSession(100L, actor).reason()).isEqualTo(NO_SESSIONS_LEFT);
    }

    @ParameterizedTest @ValueSource(strings = {"MANAGER", "RECEPTIONIST"})
    void staff_hasExplicitNoMemberContextWithoutLookingUpMemberPackages(String role) {
        detail(0);
        var result = service.getSession(100L, account(role, 3));
        assertThat(result.memberContext()).isFalse();
        assertThat(result.eligible()).isNull();
        assertThat(result.alreadyBooked()).isNull();
        assertThat(result.bookingStatus()).isNull();
        assertThat(result.reason()).isNull();
        assertThat(result.suitableRegistrations()).isEmpty();
        verifyNoInteractions(members, registrations);
        verify(bookings, never()).findBySessionIdAndMemberIdAndStatus(any(), any(), any());
    }

    @Test void missingMemberProfileIsDeniedSafely() {
        detail(0);
        assertThatThrownBy(() -> service.getSession(100L, actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(registrations);
    }

    @Test void unknownOrHiddenSessionIs404WithoutLoadingPrivateData() {
        assertThatThrownBy(() -> service.getSession(100L, actor)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(members, registrations, bookings);
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void invalidDetailIdIs400BeforeLookup(Long id) {
        assertThatThrownBy(() -> service.getSession(id, actor)).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(sessions, bookings, members, registrations);
    }

    @ParameterizedTest @ValueSource(strings = {"COACH", "ADMIN", "NULL_ACTOR", "NULL_ID", "NULL_ROLE", "INACTIVE"})
    void serviceGuard_rejectsInvalidContextForBothReadMethods(String scenario) {
        var reader = account(scenario, 2);
        if (scenario.equals("NULL_ACTOR")) reader = null;
        else if (scenario.equals("NULL_ID")) reader.setId(null);
        else if (scenario.equals("NULL_ROLE")) reader.setRole(null);
        else if (scenario.equals("INACTIVE")) reader.setStatus("INACTIVE");
        var denied = reader;
        assertThatThrownBy(() -> service.getSession(100L, denied)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listSessions(filters().findFirst().orElseThrow(), denied)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessions, bookings, members, registrations);
    }
}
