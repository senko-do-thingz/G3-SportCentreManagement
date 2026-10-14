package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.ManagerSessionResponse;
import com.sportify.catalog.dto.SessionCancelRequest;
import com.sportify.catalog.dto.SessionConflictResponse;
import com.sportify.catalog.dto.SessionCreateRequest;
import com.sportify.catalog.dto.SessionUpdateRequest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.exception.ScheduleConflictException;
import com.sportify.catalog.repository.*;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSportId;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManagerSessionServiceImplTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);
    private static final LocalTime START = LocalTime.of(10, 0);
    private static final LocalTime END = LocalTime.of(11, 0);
    @Mock private ClassSessionRepository sessions;
    @Mock private SportClassRepository classes;
    @Mock private CoachProfileRepository coaches;
    @Mock private CoachSportRepository qualifications;
    @Mock private FacilityRepository facilities;
    @Mock private BookingRepository bookings;
    @Mock private SessionCancellationRepository cancellations;
    @Mock private AuditService audit;
    private ManagerSessionServiceImpl service;
    private SportClass sportClass;
    private CoachProfile coach;
    private Facility facility;
    private UserAccount manager;

    @BeforeEach
    void setUp() {
        service = new ManagerSessionServiceImpl(sessions, classes, coaches, qualifications, facilities,
                bookings, cancellations, audit, CLOCK);
        Sport sport = Sport.builder().id(1L).name("Football").isActive(true).build();
        sportClass = SportClass.builder().id(10L).code("CL-1000").name("Football Fundamentals")
                .sport(sport).isActive(true).maxMembers(20).build();
        coach = CoachProfile.builder().id(20L).userAccount(UserAccount.builder().id(20L).fullName("Daniel Smith")
                .role(Role.builder().code("COACH").build()).status("ACTIVE").build()).build();
        facility = Facility.builder().id(30L).name("Football Pitch 1").isActive(true).build();
        manager = UserAccount.builder().id(40L).role(Role.builder().code("MANAGER").build()).build();
    }

    private SessionCreateRequest single() {
        return create(DATE, null, null, null, 16, TrainingFormat.COACH_LED, null);
    }

    private SessionCreateRequest create(LocalDate date, Set<DayOfWeek> days, LocalDate from, LocalDate to,
            int capacity, TrainingFormat type, SessionStatus status) {
        return new SessionCreateRequest(date, days, from, to, START, END, 30L, 20L, capacity, type, status);
    }

    private SessionUpdateRequest update(int capacity) {
        return new SessionUpdateRequest(DATE, START, END, 30L, 20L, capacity);
    }

    private ClassSession existing(long id) {
        return ClassSession.builder().id(id).sportClass(sportClass).sport(sportClass.getSport())
                .coach(coach).facility(facility).sessionDate(DATE).startTime(START).endTime(END)
                .capacity(16).trainingType(TrainingFormat.COACH_LED).status(SessionStatus.PUBLISHED).build();
    }

    private void validClass() {
        when(classes.findWithDetailsById(10L)).thenReturn(Optional.of(sportClass));
    }

    private void validAssignment() {
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        when(qualifications.existsById(new CoachSportId(20L, 1L))).thenReturn(true);
        when(facilities.findForScheduleUpdate(30L)).thenReturn(Optional.of(facility));
    }

    private void saveBatch() {
        when(sessions.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<ClassSession> result = invocation.getArgument(0);
            for (int i = 0; i < result.size(); i++) {
                result.get(i).setId(100L + i);
            }
            return result;
        });
    }

    private void confirmed(long sessionId, long count) {
        BookingRepository.SessionBookingCount row = mock(BookingRepository.SessionBookingCount.class);
        when(row.getSessionId()).thenReturn(sessionId);
        when(row.getBookingCount()).thenReturn(count);
        when(bookings.countBySessionIds(List.of(sessionId), BookingStatus.CONFIRMED)).thenReturn(List.of(row));
    }

    @Test
    void singleCreate_publishesAndLocksResourcesBeforeCheckingConflictsAndAudits() {
        validClass();
        validAssignment();
        saveBatch();
        List<ManagerSessionResponse> result = service.createSessions(10L, single(), manager);
        assertEquals(1, result.size());
        assertEquals(DATE, result.getFirst().date());
        assertEquals(SessionStatus.PUBLISHED, result.getFirst().status());
        assertEquals(0, result.getFirst().bookedCount());
        var order = inOrder(coaches, facilities, sessions, audit);
        order.verify(coaches).findForScheduleUpdate(20L);
        order.verify(facilities).findForScheduleUpdate(30L);
        order.verify(sessions).findCoachConflicts(eq(20L), eq(List.of(DATE)), eq(START), eq(END),
                eq(ManagerSessionServiceImpl.OCCUPYING_STATUSES), isNull());
        order.verify(sessions).findFacilityConflicts(eq(30L), eq(List.of(DATE)), eq(START), eq(END), anySet(), isNull());
        order.verify(sessions).saveAllAndFlush(anyList());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        order.verify(audit).record(event.capture());
        assertEquals(AuditAction.SESSION_CREATED, event.getValue().getAction());
        assertEquals(manager, event.getValue().getActor());
    }

    @Test
    void weeklyCreate_generatesSelectedDaysInOrderWithInclusiveBoundsAndDraftAudits() {
        validClass();
        validAssignment();
        saveBatch();
        var request = create(null, Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), DATE, DATE.plusDays(14),
                16, TrainingFormat.COACH_LED, SessionStatus.DRAFT);
        var result = service.createSessions(10L, request, manager);
        assertEquals(List.of(DATE, DATE.plusDays(3), DATE.plusDays(7), DATE.plusDays(10), DATE.plusDays(14)),
                result.stream().map(ManagerSessionResponse::date).toList());
        assertTrue(result.stream().allMatch(row -> row.status() == SessionStatus.DRAFT));
        verify(audit, times(5)).record(any());
    }

    @Test
    void weeklyCreate_acceptsExactlyOneHundredSessions() {
        validClass();
        validAssignment();
        saveBatch();
        var request = create(null, EnumSet.allOf(DayOfWeek.class), DATE, DATE.plusDays(99), 1, TrainingFormat.COACH_LED, null);
        assertEquals(100, service.createSessions(10L, request, manager).size());
    }

    static Stream<Arguments> invalidModes() {
        return Stream.of(
                Arguments.of(DATE, Set.of(DayOfWeek.MONDAY), DATE, DATE),
                Arguments.of(null, null, null, null),
                Arguments.of(null, Set.of(), DATE, DATE),
                Arguments.of(null, Set.of(DayOfWeek.MONDAY), null, DATE),
                Arguments.of(null, Set.of(DayOfWeek.MONDAY), DATE, null),
                Arguments.of(null, Set.of(DayOfWeek.MONDAY), DATE.plusDays(1), DATE),
                Arguments.of(null, Set.of(DayOfWeek.TUESDAY), DATE, DATE),
                Arguments.of(null, EnumSet.allOf(DayOfWeek.class), DATE, DATE.plusDays(100)),
                Arguments.of(null, Set.of(DayOfWeek.MONDAY), DATE, LocalDate.of(9999, 12, 31)),
                Arguments.of(null, Set.of(DayOfWeek.MONDAY), LocalDate.now(CLOCK).minusDays(1), DATE));
    }

    @ParameterizedTest
    @MethodSource("invalidModes")
    void create_rejectsInvalidModesAndRecurrencesWithoutWrites(LocalDate date, Set<DayOfWeek> days, LocalDate from, LocalDate to) {
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L,
                create(date, days, from, to, 16, TrainingFormat.COACH_LED, null), manager));
        verifyNoInteractions(classes, sessions, audit);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"CANCELLED", "COMPLETED"})
    void create_rejectsInvalidInitialStatus(SessionStatus status) {
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L,
                create(DATE, null, null, null, 16, TrainingFormat.COACH_LED, status), manager));
        verifyNoInteractions(classes, sessions, audit);
    }

    static Stream<Arguments> invalidTimes() {
        return Stream.of(Arguments.of(END, START), Arguments.of(START, START),
                Arguments.of(LocalTime.of(23, 0), LocalTime.of(1, 0)),
                Arguments.of(START.plusNanos(1), END), Arguments.of(null, END), Arguments.of(START, null));
    }

    @ParameterizedTest
    @MethodSource("invalidTimes")
    void create_rejectsInvalidTimes(LocalTime start, LocalTime end) {
        var request = new SessionCreateRequest(DATE, null, null, null, start, end, 30L, 20L, 16, TrainingFormat.COACH_LED, null);
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, request, manager));
        verifyNoInteractions(classes, sessions, audit);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 21})
    void create_rejectsCapacityOutsideClassBounds(int capacity) {
        validClass();
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L,
                create(DATE, null, null, null, capacity, TrainingFormat.COACH_LED, null), manager));
        verifyNoInteractions(coaches, facilities, sessions, audit);
    }

    @Test
    void create_rejectsPastDateAndTodayStartAlreadyReached() {
        validClass();
        validAssignment();
        for (LocalDate date : List.of(LocalDate.now(CLOCK).minusDays(1), LocalDate.now(CLOCK))) {
            assertThrows(BusinessRuleException.class, () -> service.createSessions(10L,
                    create(date, null, null, null, 16, TrainingFormat.COACH_LED, null), manager));
        }
        verify(sessions, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(audit);
    }

    @Test
    void create_unknownClassReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.createSessions(10L, single(), manager));
    }

    @Test
    void create_inactiveClassIsRejected() {
        validClass();
        sportClass.setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, single(), manager));
        verifyNoInteractions(coaches, facilities, sessions, audit);
    }

    @Test
    void create_unknownCoachReturnsNotFound() {
        validClass();
        assertThrows(ResourceNotFoundException.class, () -> service.createSessions(10L, single(), manager));
        verifyNoInteractions(facilities, sessions, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"INACTIVE", "LOCKED"})
    void create_inactiveCoachIsRejected(String status) {
        validClass();
        coach.getUserAccount().setStatus(status);
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, single(), manager));
        verifyNoInteractions(qualifications, facilities, sessions, audit);
    }

    @Test
    void create_nonCoachAccountIsRejected() {
        validClass();
        coach.getUserAccount().setRole(Role.builder().code("MEMBER").build());
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, single(), manager));
    }

    @Test
    void create_coachMustTeachClassSport() {
        validClass();
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, single(), manager));
        verifyNoInteractions(facilities, sessions, audit);
    }

    @Test
    void create_unknownFacilityReturnsNotFound() {
        validClass();
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        when(qualifications.existsById(new CoachSportId(20L, 1L))).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> service.createSessions(10L, single(), manager));
    }

    @Test
    void create_inactiveFacilityIsRejected() {
        validClass();
        validAssignment();
        facility.setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, single(), manager));
        verifyNoInteractions(sessions, audit);
    }

    @Test
    void selfTraining_allowsMissingCoachAndOnlyChecksFacility() {
        validClass();
        when(facilities.findForScheduleUpdate(30L)).thenReturn(Optional.of(facility));
        saveBatch();
        var request = new SessionCreateRequest(DATE, null, null, null, START, END, 30L, null, 16, TrainingFormat.SELF_TRAINING, null);
        assertNull(service.createSessions(10L, request, manager).getFirst().coach());
        verifyNoInteractions(coaches, qualifications);
        verify(sessions, never()).findCoachConflicts(any(), any(), any(), any(), any(), any());
    }

    @Test
    void coachLed_requiresCoachEvenForDraft() {
        validClass();
        var request = new SessionCreateRequest(DATE, null, null, null, START, END, 30L, null, 16, TrainingFormat.COACH_LED, SessionStatus.DRAFT);
        assertThrows(BusinessRuleException.class, () -> service.createSessions(10L, request, manager));
        verifyNoInteractions(coaches, facilities, sessions, audit);
    }

    @ParameterizedTest
    @EnumSource(SessionConflictResponse.ConflictType.class)
    void conflictReportsResourceTypeAndDeduplicatesSession(SessionConflictResponse.ConflictType type) {
        validClass();
        validAssignment();
        ClassSession conflict = existing(99L);
        if (type == SessionConflictResponse.ConflictType.FACILITY) {
            conflict.setCoach(null);
        }
        if (type == SessionConflictResponse.ConflictType.COACH) {
            conflict.setFacility(Facility.builder().id(31L).name("Other facility").build());
        }
        if (type != SessionConflictResponse.ConflictType.FACILITY) {
            when(sessions.findCoachConflicts(any(), anyList(), any(), any(), anySet(), isNull())).thenReturn(List.of(conflict));
        }
        if (type != SessionConflictResponse.ConflictType.COACH) {
            when(sessions.findFacilityConflicts(any(), anyList(), any(), any(), anySet(), isNull())).thenReturn(List.of(conflict));
        }
        var ex = assertThrows(ScheduleConflictException.class, () -> service.createSessions(10L, single(), manager));
        List<?> conflicts = (List<?>) ex.getDetails().get("conflicts");
        assertEquals(1, conflicts.size());
        assertEquals(type, ((SessionConflictResponse) conflicts.getFirst()).conflictType());
        verify(sessions, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(audit);
    }

    @Test
    void weeklyConflict_returnsEveryConflictingDateBeforeAnyInsert() {
        validClass();
        validAssignment();
        ClassSession first = existing(98L);
        ClassSession second = existing(99L);
        second.setSessionDate(DATE.plusDays(7));
        when(sessions.findCoachConflicts(any(), anyList(), any(), any(), anySet(), isNull())).thenReturn(List.of(second, first));
        var request = create(null, Set.of(DayOfWeek.MONDAY), DATE, DATE.plusDays(7), 16, TrainingFormat.COACH_LED, null);
        var ex = assertThrows(ScheduleConflictException.class, () -> service.createSessions(10L, request, manager));
        List<?> conflicts = (List<?>) ex.getDetails().get("conflicts");
        assertEquals(List.of(DATE, DATE.plusDays(7)), conflicts.stream().map(row -> ((SessionConflictResponse) row).requestedDate()).toList());
        verify(sessions, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(audit);
    }

    @Test
    void update_excludesSelfRevalidatesCoachAndWritesBeforeAfterAudit() {
        ClassSession session = existing(100L);
        session.setBookedCount(1);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        validAssignment();
        confirmed(100L, 3);
        when(sessions.saveAndFlush(session)).thenReturn(session);
        assertEquals(3, service.updateSession(100L, update(3), manager).bookedCount());
        assertEquals(1, session.getBookedCount(), "Updating the schedule must not repair the cached counter");
        verify(sessions).findCoachConflicts(eq(20L), eq(List.of(DATE)), eq(START), eq(END), anySet(), eq(100L));
        verify(sessions).findFacilityConflicts(eq(30L), eq(List.of(DATE)), eq(START), eq(END), anySet(), eq(100L));
        verify(qualifications).existsById(new CoachSportId(20L, 1L));
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertEquals(AuditAction.SESSION_UPDATED, event.getValue().getAction());
        assertEquals(16, ((Map<?, ?>) event.getValue().getDetails().get("before")).get("capacity"));
        assertEquals(3, ((Map<?, ?>) event.getValue().getDetails().get("after")).get("capacity"));
        assertEquals(1, ((Map<?, ?>) event.getValue().getDetails().get("before")).get("bookedCount"));
        assertEquals(1, ((Map<?, ?>) event.getValue().getDetails().get("after")).get("bookedCount"));
    }

    @Test
    void update_capacityBelowConfirmedBookingsIsRejectedUsingRealCount() {
        ClassSession session = existing(100L);
        session.setBookedCount(0);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        confirmed(100L, 3);
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(2), manager));
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(coaches, facilities, audit);
    }

    @Test
    void update_unknownSessionReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.updateSession(100L, update(16), manager));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 21})
    void update_rejectsCapacityOutsideClassBounds(int capacity) {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(capacity), manager));
        verifyNoInteractions(coaches, facilities, audit);
    }

    @Test
    void update_inactiveClassIsRejected() {
        sportClass.setIsActive(false);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(coaches, facilities, audit);
    }

    @Test
    void update_legacyUnlinkedSessionIsRejected() {
        ClassSession session = existing(100L);
        session.setSportClass(null);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(coaches, facilities, audit);
    }

    @Test
    void update_unknownCoachReturnsNotFound() {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        assertThrows(ResourceNotFoundException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(facilities, audit);
    }

    @Test
    void update_unknownFacilityReturnsNotFound() {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        when(qualifications.existsById(new CoachSportId(20L, 1L))).thenReturn(true);
        assertThrows(ResourceNotFoundException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(audit);
    }

    @Test
    void update_rejectsMovingIntoPast() {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        validAssignment();
        var request = new SessionUpdateRequest(LocalDate.now(CLOCK).minusDays(1), START, END, 30L, 20L, 16);
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, request, manager));
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void update_conflictDoesNotMutateSessionOrAudit() {
        ClassSession session = existing(100L);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        validAssignment();
        when(sessions.findCoachConflicts(any(), anyList(), any(), any(), anySet(), eq(100L)))
                .thenReturn(List.of(existing(101L)));
        assertThrows(ScheduleConflictException.class, () -> service.updateSession(100L, update(12), manager));
        assertEquals(16, session.getCapacity());
        verify(sessions, never()).saveAndFlush(any());
        verifyNoInteractions(audit);
    }

    @Test
    void update_inactiveFacilityIsRejected() {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        validAssignment();
        facility.setIsActive(false);
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(audit);
    }

    @Test
    void update_inactiveCoachIsRejected() {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        coach.getUserAccount().setStatus("INACTIVE");
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(qualifications, facilities, audit);
    }

    @Test
    void update_rechecksQualificationForUnchangedCoach() {
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(existing(100L)));
        when(coaches.findForScheduleUpdate(20L)).thenReturn(Optional.of(coach));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        verifyNoInteractions(facilities, audit);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = {"CANCELLED", "COMPLETED"})
    void updateAndCancel_rejectTerminalSessionStates(SessionStatus status) {
        ClassSession session = existing(100L);
        session.setStatus(status);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        assertThrows(BusinessRuleException.class, () -> service.cancelSession(100L, new SessionCancelRequest("Weather"), manager));
        verifyNoInteractions(cancellations, audit);
    }

    @Test
    void updateAndCancel_rejectSessionAtExactStartTime() {
        ClassSession session = existing(100L);
        session.setSessionDate(LocalDate.now(CLOCK));
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        assertThrows(BusinessRuleException.class, () -> service.updateSession(100L, update(16), manager));
        assertThrows(BusinessRuleException.class, () -> service.cancelSession(100L, new SessionCancelRequest("Weather"), manager));
        verifyNoInteractions(cancellations, audit);
    }

    @Test
    void cancel_bulkCancelsAndRefundsBookingsAndRecordsManagerReasonAndCount() {
        ClassSession session = existing(100L);
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        when(cancellations.cancelConfirmedBookings(100L, 40L, "Unsafe weather", LocalDateTime.now(CLOCK))).thenReturn(3);
        when(sessions.saveAndFlush(session)).thenReturn(session);
        ManagerSessionResponse result = service.cancelSession(100L, new SessionCancelRequest("  Unsafe weather  "), manager);
        assertEquals(SessionStatus.CANCELLED, result.status());
        assertEquals(0, result.bookedCount());
        assertEquals("Unsafe weather", result.cancelReason());
        assertEquals(40L, result.cancelledByUserId());
        assertEquals(LocalDateTime.now(CLOCK), result.cancelledAt());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).record(event.capture());
        assertEquals(AuditAction.SESSION_CANCELLED, event.getValue().getAction());
        assertEquals(3, event.getValue().getDetails().get("bookingsCancelled"));
        verifyNoInteractions(bookings);
    }

    @Test
    void cancel_unknownSessionReturnsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.cancelSession(100L, new SessionCancelRequest("Weather"), manager));
    }

    static Stream<String> invalidCancellationReasons() {
        return Stream.of(null, "", "   ", "x".repeat(256));
    }

    @ParameterizedTest
    @MethodSource("invalidCancellationReasons")
    void cancel_rejectsInvalidReasonBeforeSessionLookupOrLock(String reason) {
        assertThrows(BusinessRuleException.class, () -> service.cancelSession(100L, new SessionCancelRequest(reason), manager));
        verifyNoInteractions(sessions, cancellations, audit);
    }

    static Stream<Arguments> invalidCalendarRanges() {
        return Stream.of(Arguments.of(null, DATE), Arguments.of(DATE, null),
                Arguments.of(DATE.plusDays(1), DATE), Arguments.of(DATE, DATE.plusDays(62)),
                Arguments.of(DATE, LocalDate.of(10000, 1, 1)));
    }

    @ParameterizedTest
    @MethodSource("invalidCalendarRanges")
    void calendar_rejectsMissingReversedOrOversizedRanges(LocalDate from, LocalDate to) {
        assertThrows(BusinessRuleException.class, () -> service.listSessions(from, to, null, null, null));
        verifyNoInteractions(sessions, bookings);
    }

    @Test
    @SuppressWarnings("unchecked")
    void calendar_usesOneGroupedBookingCountAndStableSortIncludingCancelledSessions() {
        ClassSession session = existing(100L);
        session.setStatus(SessionStatus.CANCELLED);
        session.setBookedCount(15);
        when(sessions.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(session));
        BookingRepository.SessionBookingCount count = mock(BookingRepository.SessionBookingCount.class);
        when(count.getSessionId()).thenReturn(100L);
        when(count.getBookingCount()).thenReturn(2L);
        when(bookings.countForSessionCalendar(DATE, DATE.plusDays(61), 10L, 20L, 30L, BookingStatus.CONFIRMED))
                .thenReturn(List.of(count));
        var result = service.listSessions(DATE, DATE.plusDays(61), 10L, 20L, 30L);
        assertEquals(SessionStatus.CANCELLED, result.getFirst().status());
        assertEquals(2, result.getFirst().bookedCount());
        verify(sessions).findAll(any(Specification.class), eq(Sort.by("sessionDate", "startTime", "id")));
        verify(bookings).countForSessionCalendar(DATE, DATE.plusDays(61), 10L, 20L, 30L, BookingStatus.CONFIRMED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void calendar_emptyResultDoesNotIssueAnEmptyInQuery() {
        when(sessions.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());
        assertTrue(service.listSessions(DATE, DATE, null, null, null).isEmpty());
        verifyNoInteractions(bookings);
    }

    @Test
    void create_lockFailureReturnsRetryableConflictWithoutInserts() {
        validClass();
        when(coaches.findForScheduleUpdate(20L)).thenThrow(new PessimisticLockingFailureException("Deadlock victim"));
        assertThrows(ConflictException.class, () -> service.createSessions(10L, single(), manager));
        verifyNoInteractions(sessions, audit);
    }
}
