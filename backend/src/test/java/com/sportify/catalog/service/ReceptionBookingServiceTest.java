package com.sportify.catalog.service;

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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;

import java.time.*;
import java.util.*;

import static com.sportify.catalog.test.ScheduleTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceptionBookingServiceTest {
    @Mock BookingRepository bookings;
    @Mock ClassSessionRepository sessions;
    @Mock SportPackageRegistrationRepository registrations;
    @Mock MemberProfileRepository members;
    @Mock CheckInRepository checkIns;
    @Mock CodeFormatter codes;
    @Mock EntityManager em;
    @Mock AuditService audit;
    BookingServiceImpl service;
    MemberProfile member;
    ClassSession session;
    SportPackageRegistration registration;

    @BeforeEach void setup() {
        service = new BookingServiceImpl(bookings, sessions, registrations, members, checkIns, codes, CLOCK, em, audit);
        member = MemberProfile.builder().id(2L).memberCode("MEM-2").userAccount(account("MEMBER", 2)).build();
        session = session();
        registration = registration(200); registration.setMember(member);
    }

    private BookingCreateRequest request() {
        return BookingCreateRequest.builder().memberId(2L).sessionId(100L).packageRegistrationId(200L).build();
    }
    private void target() { when(members.findById(2L)).thenReturn(Optional.of(member)); }
    private void bookable() {
        target();
        when(sessions.findForUpdate(100L)).thenReturn(Optional.of(session));
        lenient().when(sessions.findVisibleById(100L, TODAY, NOW)).thenReturn(Optional.of(session));
        lenient().when(registrations.findById(200L)).thenReturn(Optional.of(registration));
        lenient().when(sessions.reserveSeat(eq(100L), any())).thenReturn(1);
        lenient().when(registrations.consumeSession(eq(200L), eq("ACTIVE"), any(), any())).thenReturn(1);
        lenient().when(bookings.save(any())).thenAnswer(call -> {
            Booking booking = call.getArgument(0); booking.setId(300L); return booking;
        });
    }
    private Booking historyRow() {
        return Booking.builder().id(300L).bookingCode("BOOK-300").member(member).session(session)
                .packageRegistration(registration).status(BookingStatus.CONFIRMED)
                .bookedAt(LocalDateTime.of(TODAY, NOW)).build();
    }

    @ParameterizedTest @CsvSource({"RECEPTIONIST,,", "MANAGER,UPCOMING,CONFIRMED", "RECEPTIONIST,PAST,CANCELLED",
            "MANAGER,,CONFIRMED", "RECEPTIONIST,UPCOMING,", "MANAGER,PAST,"})
    void staffHistoryCombinesFiltersInRepositoryAndPreservesResponse(String role, BookingWhen when, BookingStatus status) {
        target();
        when(bookings.findMemberBookings(eq(2L), eq(when == null ? null : when.name()), eq(status), eq(TODAY), eq(NOW), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(historyRow()), call.getArgument(5), 1));
        var result = service.getMemberBookings(2L, when, status, account(role, 9));
        assertThat(result).hasSize(1);
        var row = result.getFirst();
        assertThat(row.getMemberId()).isEqualTo(2L);
        assertThat(row.getPackageRegistrationId()).isEqualTo(200L);
        assertThat(row.getPackageName()).isEqualTo("Test package");
        assertThat(row.getCoachName()).isEqualTo("Reader 20");
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findMemberBookings(eq(2L), eq(when == null ? null : when.name()), eq(status), eq(TODAY), eq(NOW), page.capture());
        assertThat(page.getValue().isUnpaged()).isTrue();
        assertThat(page.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(when == BookingWhen.UPCOMING ? Sort.Direction.ASC : Sort.Direction.DESC);
        verifyNoInteractions(audit);
    }

    @ParameterizedTest @CsvSource({"0,2,5,3", "1,2,5,3", "2,2,5,3", "7,2,0,0"})
    void staffPaginationIsDatabaseLimitedAndCarriesTotals(int page, int size, long total, int pages) {
        target();
        when(bookings.findMemberBookings(eq(2L), eq("UPCOMING"), eq(BookingStatus.CONFIRMED), eq(TODAY), eq(NOW), any()))
                .thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(5), total));
        var result = service.getMemberBookings(2L, BookingWhen.UPCOMING, BookingStatus.CONFIRMED, page, size, account("MANAGER", 9));
        assertThat(result.page()).isEqualTo(page); assertThat(result.size()).isEqualTo(size);
        assertThat(result.totalElements()).isEqualTo(total); assertThat(result.totalPages()).isEqualTo(pages);
        assertThat(result.content()).isEmpty();
        var paging = ArgumentCaptor.forClass(Pageable.class);
        verify(bookings).findMemberBookings(eq(2L), eq("UPCOMING"), eq(BookingStatus.CONFIRMED), eq(TODAY), eq(NOW), paging.capture());
        assertThat(paging.getValue().getOffset()).isEqualTo((long) page * size);
        assertThat(paging.getValue().getSort().stream().map(Sort.Order::getProperty).toList())
                .containsExactly("session.sessionDate", "session.startTime", "id");
    }

    @ParameterizedTest @CsvSource({"-1,20", "0,0", "0,101", "2147483647,100"})
    void invalidPaginationFailsBeforeTargetLookup(int page, int size) {
        assertThatThrownBy(() -> service.getMemberBookings(2L, null, null, page, size, account("RECEPTIONIST", 9)))
                .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_PAGINATION"));
        verifyNoInteractions(members, bookings);
    }

    @ParameterizedTest @ValueSource(strings = {"MEMBER", "COACH", "ADMIN"})
    void serviceRejectsUnsupportedRolesBeforeLookingUpAnyTarget(String role) {
        var actor = account(role, 9);
        assertThatThrownBy(() -> service.getMemberBookings(2L, null, null, actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getMemberBookings(2L, null, null, 0, 20, actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members, bookings);
    }

    @Test void anonymousAndInactiveStaffFailClosed() {
        var actor = account("RECEPTIONIST", 9); actor.setStatus("LOCKED");
        assertThatThrownBy(() -> service.getMemberBookings(2L, null, null, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.createBooking(request(), actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members, sessions, bookings);
    }

    @ParameterizedTest @ValueSource(longs = {0, -1})
    void nonpositiveTargetFailsBeforeQuery(long id) {
        assertThatThrownBy(() -> service.getMemberBookings(id, null, null, account("MANAGER", 9)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(members, bookings);
    }

    @Test void unknownOrNonMemberTargetIsNotAnEmptyHistoryOrStaffBeneficiary() {
        assertThatThrownBy(() -> service.getMemberBookings(2L, null, null, account("MANAGER", 9)))
                .isInstanceOf(ResourceNotFoundException.class);
        target(); member.setUserAccount(account("COACH", 2));
        assertThatThrownBy(() -> service.getMemberBookings(2L, null, null, account("MANAGER", 9)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.createBooking(request(), account("RECEPTIONIST", 9)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(sessions, bookings, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"RECEPTIONIST", "MANAGER"})
    void staffBookingUsesBeneficiarySelectedPackageAndAuthenticatedAuditActor(String role) {
        bookable(); var actor = account(role, 9);
        session.setCoach(null); session.setSportClass(null); session.setTrainingType(TrainingFormat.SELF_TRAINING);
        registration.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
        var result = service.createBooking(request(), actor);
        assertThat(result.getMemberId()).isEqualTo(2L); assertThat(result.getCoachId()).isNull();
        assertThat(result.getPackageRegistrationId()).isEqualTo(200L);
        var event = ArgumentCaptor.forClass(AuditEvent.class);
        var order = inOrder(sessions, registrations, bookings, audit);
        order.verify(sessions).findForUpdate(100L);
        order.verify(sessions).reserveSeat(eq(100L), any());
        order.verify(registrations).consumeSession(eq(200L), eq("ACTIVE"), eq(session.getSessionDate()), any());
        order.verify(bookings).save(any()); order.verify(bookings).flush(); order.verify(audit).record(event.capture());
        assertThat(event.getValue().getActor()).isSameAs(actor);
        assertThat(event.getValue().getAction()).isEqualTo(AuditAction.BOOKING_CREATED);
        assertThat(event.getValue().getDetails()).containsEntry("memberId", 2L).containsEntry("packageRegistrationId", 200L);
        assertThat(event.getValue().isRequiresNew()).isFalse();
        verify(registrations, never()).findForSessionEligibility(any(), any(), any());
    }

    @Test void memberCannotSupplyForeignBeneficiaryButMatchingAndOmittedIdRemainValid() {
        var actor = account("MEMBER", 2); var request = request(); request.setMemberId(3L);
        assertThatThrownBy(() -> service.createBooking(request, actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(members, sessions, bookings, audit);
        bookable(); request.setMemberId(null);
        assertThat(service.createBooking(request, actor).getMemberId()).isEqualTo(2L);
        request.setMemberId(2L);
        assertThat(service.createBooking(request, actor).getMemberId()).isEqualTo(2L);
    }

    @ParameterizedTest @CsvSource({"true,false", "false,true", "true,true"})
    void staffMustExplicitlySelectMemberAndPackage(boolean omitMember, boolean omitPackage) {
        var request = request(); if (omitMember) request.setMemberId(null); if (omitPackage) request.setPackageRegistrationId(null);
        assertThatThrownBy(() -> service.createBooking(request, account("RECEPTIONIST", 9)))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(members, sessions, registrations, bookings, audit);
    }

    @ParameterizedTest @ValueSource(strings = {"OWNER", "SPORT", "FORMAT", "EXPIRED", "REFUNDED", "CANCELLED", "PENDING_PAYMENT", "NOT_STARTED", "ENDED", "CREDITS"})
    void staffCannotRelaxSelectedPackageRules(String defect) {
        bookable();
        switch (defect) {
            case "OWNER" -> registration.setMember(MemberProfile.builder().id(3L).build());
            case "SPORT" -> registration.getSportPackage().setSport(Sport.builder().id(99L).build());
            case "FORMAT" -> registration.getSportPackage().setTrainingFormat(TrainingFormat.SELF_TRAINING);
            case "NOT_STARTED" -> registration.setStartDate(session.getSessionDate().plusDays(1));
            case "ENDED" -> registration.setEndDate(TODAY);
            case "CREDITS" -> registration.setRemainingSessions(0);
            default -> registration.setStatus(PackageRegistrationStatus.valueOf(defect));
        }
        assertThatThrownBy(() -> service.createBooking(request(), account("MANAGER", 9))).isInstanceOf(BusinessRuleException.class);
        verify(sessions, never()).reserveSeat(any(), any()); verifyNoInteractions(audit);
        verify(bookings, never()).save(any());
    }

    @ParameterizedTest @ValueSource(strings = {"FULL", "ALREADY_BOOKED"})
    void staffConflictsRetain409MachineCodes(String reason) {
        bookable();
        if (reason.equals("FULL")) when(bookings.countBySessionIdAndStatus(100L, BookingStatus.CONFIRMED)).thenReturn(10L);
        else when(bookings.findBySessionIdAndMemberIdAndStatus(100L, 2L, BookingStatus.CONFIRMED)).thenReturn(Optional.of(historyRow()));
        assertThatThrownBy(() -> service.createBooking(request(), account("MANAGER", 9)))
                .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.getCode()).isEqualTo(reason));
        verify(sessions, never()).reserveSeat(any(), any()); verifyNoInteractions(audit);
    }

    @Test void auditFailureEscapesForTransactionRollbackWithoutCompensation() {
        bookable(); doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).record(any());
        assertThatThrownBy(() -> service.createBooking(request(), account("MANAGER", 9)))
                .isInstanceOf(IllegalStateException.class).hasMessage("Synthetic audit failure");
        verify(registrations).consumeSession(eq(200L), eq("ACTIVE"), any(), any());
        verify(registrations, never()).restoreSession(any(), any(), any());
    }
}
