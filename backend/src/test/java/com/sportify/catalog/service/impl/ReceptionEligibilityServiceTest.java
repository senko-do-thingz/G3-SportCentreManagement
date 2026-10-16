package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.SessionDetailResponse;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.exception.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.MemberProfileRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceptionEligibilityServiceTest {
    @Mock ClassSessionRepository sessions;
    @Mock BookingRepository bookings;
    @Mock SportPackageRegistrationRepository registrations;
    @Mock MemberProfileRepository members;
    MemberScheduleServiceImpl service;
    MemberProfile member;
    ClassSession session;

    @BeforeEach void setup() {
        service = new MemberScheduleServiceImpl(sessions, bookings, registrations, members, CLOCK);
        member = MemberProfile.builder().id(2L).userAccount(account("MEMBER", 2)).build();
        session = session();
    }
    private void visible() {
        when(members.findById(2L)).thenReturn(Optional.of(member));
        when(sessions.findVisibleById(100L, TODAY, NOW)).thenReturn(Optional.of(session));
    }
    @ParameterizedTest @CsvSource({"VALID,", "OWNER,NO_PACKAGE", "SPORT,NO_PACKAGE", "FORMAT,NO_PACKAGE",
            "CREDITS,NO_SESSIONS_LEFT", "EXPIRED,PACKAGE_EXPIRED_BY_THEN", "ENDED,PACKAGE_EXPIRED_BY_THEN",
            "NOT_STARTED,PACKAGE_NOT_STARTED_BY_THEN", "REFUNDED,NO_PACKAGE", "CANCELLED,NO_PACKAGE",
            "PENDING_PAYMENT,NO_PACKAGE"})
    void staffDecisionsAreExactlyTheMemberFacingCalculation(String scenario, SessionDetailResponse.EligibilityReason reason) {
        visible(); var registration = registration(200);
        switch (scenario) {
            case "OWNER" -> registration.setMember(MemberProfile.builder().id(3L).build());
            case "SPORT" -> registration.getSportPackage().setSport(Sport.builder().id(99L).build());
            case "FORMAT" -> registration.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
            case "CREDITS" -> registration.setRemainingSessions(0);
            case "ENDED" -> registration.setEndDate(TODAY);
            case "NOT_STARTED" -> registration.setStartDate(TODAY.plusDays(2));
            case "VALID" -> { }
            default -> registration.setStatus(PackageRegistrationStatus.valueOf(scenario));
        }
        when(registrations.findForSessionEligibility(2L, 10L, TrainingFormat.COACH_LED)).thenReturn(List.of(registration));
        var direct = service.getSession(100L, member.getUserAccount());
        for (String role : List.of("RECEPTIONIST", "MANAGER")) {
            var assisted = service.getMemberEligibility(100L, 2L, account(role, 9));
            assertThat(assisted).isEqualTo(direct);
            assertThat(assisted.reason()).isEqualTo(reason);
            assertThat(assisted.eligible()).isEqualTo(reason == null);
            assertThat(assisted.memberContext()).isTrue();
        }
        verify(registrations, never()).consumeSession(any(), any(), any(), any());
        verify(sessions, never()).reserveSeat(any(), any());
        verify(bookings, never()).save(any()); verify(members, never()).save(any());
    }
    @ParameterizedTest @CsvSource({"FULL,FULL", "CACHED,CAPACITY_UNAVAILABLE", "DUPLICATE,ALREADY_BOOKED"})
    void capacityAndDuplicatePrecedenceIsShared(String scenario, SessionDetailResponse.EligibilityReason expected) {
        visible();
        if (scenario.equals("FULL")) when(bookings.countBySessionIds(List.of(100L), BookingStatus.CONFIRMED)).thenReturn(List.of(count(100, 10)));
        if (scenario.equals("CACHED")) session.setBookedCount(10);
        if (scenario.equals("DUPLICATE")) when(bookings.findBySessionIdAndMemberIdAndStatus(100L, 2L, BookingStatus.CONFIRMED))
                .thenReturn(Optional.of(Booking.builder().id(300L).build()));
        var assisted = service.getMemberEligibility(100L, 2L, account("MANAGER", 9));
        assertThat(assisted).isEqualTo(service.getSession(100L, member.getUserAccount()));
        assertThat(assisted.reason()).isEqualTo(expected); verifyNoInteractions(registrations);
    }
    @Test void allSuitablePackagesAreSortedAndSelfTrainingHasNoInventedCoach() {
        visible(); session.setCoach(null); session.setSportClass(null); session.setTrainingType(TrainingFormat.SELF_TRAINING);
        var first = registration(201); var second = registration(200);
        first.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
        second.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
        when(registrations.findForSessionEligibility(2L, 10L, TrainingFormat.SELF_TRAINING)).thenReturn(List.of(first, second));
        var result = service.getMemberEligibility(100L, 2L, account("RECEPTIONIST", 9));
        assertThat(result.suitableRegistrations()).extracting(SessionDetailResponse.SuitableRegistration::registrationId).containsExactly(200L, 201L);
        assertThat(result.session().coachId()).isNull(); assertThat(result.session().classId()).isNull();
    }
    @ParameterizedTest @ValueSource(strings = {"MEMBER", "COACH", "ADMIN"})
    void unauthorizedServiceCallerNeverLooksUpSelectedMember(String role) {
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, account(role, 9))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members, sessions, bookings, registrations);
    }
    @Test void anonymousAndInactiveStaffAreDeniedBeforeQueries() {
        var actor = account("MANAGER", 9); actor.setStatus("LOCKED");
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members, sessions, bookings, registrations);
    }
    @ParameterizedTest @CsvSource({"0,2", "-1,2", "100,0", "100,-1"})
    void invalidIdsFailBeforeQueries(long session, long member) {
        assertThatThrownBy(() -> service.getMemberEligibility(session, member, account("MANAGER", 9))).isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(members, sessions);
    }
    @Test void absentNonMemberAndInactiveBeneficiariesCannotBeUsedForEligibility() {
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, account("MANAGER", 9))).isInstanceOf(ResourceNotFoundException.class);
        when(members.findById(2L)).thenReturn(Optional.of(member)); member.setUserAccount(account("COACH", 2));
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, account("MANAGER", 9))).isInstanceOf(ResourceNotFoundException.class);
        member.setUserAccount(account("MEMBER", 2)); member.getUserAccount().setStatus("LOCKED");
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, account("MANAGER", 9))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessions, registrations);
    }
    @Test void hiddenOrMissingSessionKeepsBE11NotFoundContract() {
        when(members.findById(2L)).thenReturn(Optional.of(member));
        assertThatThrownBy(() -> service.getMemberEligibility(100L, 2L, account("MANAGER", 9))).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(bookings, registrations);
    }
}
