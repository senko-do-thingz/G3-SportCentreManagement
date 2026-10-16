package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachScheduleServiceImplTest {
    @Mock CoachScheduleReadRepository reads;
    @Mock BookingRepository bookings;
    @Mock CoachProfileRepository coaches;
    @Mock UserRepository users;
    @InjectMocks CoachScheduleServiceImpl service;
    static final String DAY = "2026-10-16";
    static final LocalDate DATE = LocalDate.parse(DAY);

    UserAccount authenticate(String role) {
        var actor = UserAccount.builder().id(17L).fullName("Reader").email("be14@example.test")
                .role(Role.builder().code(role).build()).status("ACTIVE").build();
        var principal = new CustomUserDetails(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()));
        when(users.findById(17L)).thenReturn(Optional.of(actor));
        return actor;
    }
    void coach() { authenticate("COACH"); when(coaches.existsById(17L)).thenReturn(true); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    CoachScheduleReadRepository.SessionOwnership ownership(Long coachId) {
        var row = mock(CoachScheduleReadRepository.SessionOwnership.class);
        when(row.getCoachId()).thenReturn(coachId);
        return row;
    }
    CoachSessionResponse session(long id, SessionStatus status) {
        return new CoachSessionResponse(id, 10L, "Class", 20L, "Sport", 17L, "Reader", 30L, "Court",
                DATE, LocalTime.NOON, LocalTime.of(13, 0), 10, status);
    }

    @ParameterizedTest @EnumSource(SessionStatus.class)
    void ownSessionsKeepEveryStatusAndUseConfirmedCounts(SessionStatus status) {
        coach(); var first = session(91, status); var second = session(92, status);
        when(reads.findTeachingSessions(DATE, DATE, 17L)).thenReturn(List.of(first, second));
        var count = mock(BookingRepository.SessionBookingCount.class);
        when(count.getSessionId()).thenReturn(91L); when(count.getBookingCount()).thenReturn(3L);
        when(bookings.countForSessionCalendar(DATE, DATE, null, 17L, null, BookingStatus.CONFIRMED)).thenReturn(List.of(count));
        var result = service.listSessions(DAY, DAY, null);
        assertThat(result).containsExactly(first.withBookedCount(3), second.withBookedCount(0));
        assertThat(result.getFirst().status()).isEqualTo(status);
        assertThat(result.getFirst().capacity()).isEqualTo(10);
    }

    @Test void coachCanSupplyOwnId() {
        coach(); when(reads.findTeachingSessions(DATE, DATE, 17L)).thenReturn(List.of());
        assertThat(service.listSessions(DAY, DAY, 17L)).isEmpty();
        verifyNoInteractions(bookings);
    }

    @Test void crossCoachOverrideFailsBeforeScheduleQuery() {
        coach(); assertThatThrownBy(() -> service.listSessions(DAY, DAY, 19L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, bookings);
    }

    @Test void managerWithoutCoachProfileGetsAllAssignedCalendar() {
        authenticate("MANAGER"); when(reads.findTeachingSessions(DATE, DATE, null)).thenReturn(List.of());
        assertThat(service.listSessions(DAY, DAY, null)).isEmpty(); verifyNoInteractions(coaches, bookings);
    }

    @Test void managerCanFilterExistingHistoricalCoach() {
        authenticate("MANAGER"); when(coaches.existsById(19L)).thenReturn(true);
        when(reads.findTeachingSessions(DATE, DATE, 19L)).thenReturn(List.of());
        assertThat(service.listSessions(DAY, DAY, 19L)).isEmpty();
    }

    @Test void unknownManagerCoachIs404() {
        authenticate("MANAGER");
        assertThatThrownBy(() -> service.listSessions(DAY, DAY, 19L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(reads, bookings);
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void missingCoachProfileNeverBecomesUnrestricted(boolean roster) {
        authenticate("COACH");
        assertThatThrownBy(() -> { if (roster) service.roster(91L); else service.listSessions(DAY, DAY, null); })
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, bookings);
    }

    @Test void ownRosterPreservesSelectedPackageAndActualBookingTime() {
        coach(); var assignment = ownership(17L);
        when(reads.findOwnership(91L)).thenReturn(Optional.of(assignment));
        var row = new CoachRosterResponse(81L, "MEM-EXAMPLE", "Member", null, 71L, 61L, "Original package", DATE.minusDays(2).atTime(9, 0));
        when(reads.findRoster(91L, BookingStatus.CONFIRMED, false, 17L)).thenReturn(List.of(row));
        assertThat(service.roster(91L)).containsExactly(row);
        assertThat(row.bookedAt()).isEqualTo("2026-10-14T09:00:00");
        verifyNoInteractions(bookings);
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {19L})
    void foreignAndUnassignedRosterForbiddenBeforePiiQuery(Long assigned) {
        coach(); var assignment = ownership(assigned);
        when(reads.findOwnership(91L)).thenReturn(Optional.of(assignment));
        assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(AccessDeniedException.class);
        verify(reads, never()).findRoster(anyLong(), any(), anyBoolean(), any());
    }

    @Test void managerCanReadUnassignedRosterWithoutProfile() {
        authenticate("MANAGER"); when(reads.findOwnership(91L)).thenReturn(Optional.of(mock(CoachScheduleReadRepository.SessionOwnership.class)));
        when(reads.findRoster(91L, BookingStatus.CONFIRMED, true, null)).thenReturn(List.of());
        assertThat(service.roster(91L)).isEmpty(); verifyNoInteractions(coaches);
    }

    @Test void missingSession404WithoutRosterQuery() {
        coach(); when(reads.findOwnership(91L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(ResourceNotFoundException.class);
        verify(reads, never()).findRoster(anyLong(), any(), anyBoolean(), any());
    }

    @Test void concurrentReassignmentQueryCanReturnNoPii() {
        coach(); var assignment = ownership(17L);
        when(reads.findOwnership(91L)).thenReturn(Optional.of(assignment));
        when(reads.findRoster(91L, BookingStatus.CONFIRMED, false, 17L)).thenReturn(List.of());
        assertThat(service.roster(91L)).isEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "RECEPTIONIST", "ADMIN"})
    void directUnproxiedServiceRejectsUnsupportedCurrentDatabaseRole(String role) {
        authenticate(role);
        assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listSessions(DAY, DAY, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, bookings, coaches);
    }

    @Test void staleManagerAuthorityCannotOverrideCurrentMemberRole() {
        var actor = authenticate("MANAGER"); actor.setRole(Role.builder().code("MEMBER").build());
        assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, bookings, coaches);
    }

    @Test void inactiveDatabaseAccountRejected() {
        var actor = authenticate("COACH"); actor.setStatus("INACTIVE");
        assertThatThrownBy(() -> service.listSessions(DAY, DAY, null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, bookings, coaches);
    }

    @Test void deletedAccountRejected() {
        authenticate("COACH"); when(users.findById(17L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(reads, bookings, coaches);
    }

    @Test void anonymousDirectCallCannotRead() {
        assertThatThrownBy(() -> service.roster(91L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(users, reads, bookings, coaches);
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void invalidRosterId(Long id) {
        authenticate("MANAGER");
        assertThatThrownBy(() -> service.roster(id)).isInstanceOf(FieldValidationException.class);
        verifyNoInteractions(reads, bookings, coaches);
    }
}
