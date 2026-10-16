package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.dto.BookingWhen;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.BookingService;
import com.sportify.catalog.service.PackageBookingEligibility;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.PagingDefaults;
import com.sportify.core.common.SecurityUtils;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final String DUPLICATE_BOOKING_MESSAGE = "Member already has a confirmed booking for this session";

    private final BookingRepository bookingRepository;
    private final ClassSessionRepository sessionRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final CheckInRepository checkInRepository;
    private final CodeFormatter codeFormatter;
    private final Clock clock;
    private final EntityManager entityManager;
    private final AuditService auditService;

    public static final int MEMBER_CANCELLATION_CUTOFF_MINUTES = 120;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request, UserAccount actor) {
        requireActor(actor, false);
        if (request == null) throw rule("Booking request is required", "INVALID_REQUEST");
        requirePositiveId(request.getSessionId(), "Session ID");
        if (request.getPackageRegistrationId() != null) requirePositiveId(request.getPackageRegistrationId(), "Package registration ID");
        Long targetMemberId = SecurityUtils.isMember(actor) ? actor.getId()
                : request.getMemberId() != null ? request.getMemberId() : actor.getId();
        MemberProfile member = memberProfileRepository.findById(targetMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for id: " + targetMemberId));

        // Preserve BE-10/BE-21: session first, booking checks next, then registration mutation.
        ClassSession session = sessionRepository.findForUpdate(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Class session not found for id: " + request.getSessionId()));
        entityManager.refresh(session);
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.getStatus() != SessionStatus.PUBLISHED) {
            throw rule("Session is not published for booking", "SESSION_UNAVAILABLE");
        }
        if (bookingRepository.findBySessionIdAndMemberIdAndStatus(session.getId(), member.getId(), BookingStatus.CONFIRMED).isPresent()) {
            throw new ConflictException(DUPLICATE_BOOKING_MESSAGE, "ALREADY_BOOKED");
        }
        if (session.getSessionDate().isBefore(now.toLocalDate())) {
            throw rule("Cannot book a session in the past", "SESSION_STARTED");
        }
        if (!LocalDateTime.of(session.getSessionDate(), session.getStartTime()).isAfter(now)) {
            throw rule("Cannot book a session that has already started", "SESSION_STARTED");
        }
        // Reuse the BE-11 visibility predicate; a read-time eligibility snapshot is never authority.
        if (sessionRepository.findVisibleById(session.getId(), now.toLocalDate(), now.toLocalTime()).isEmpty()) {
            throw rule("Session assignments are not available for booking", "SESSION_UNAVAILABLE");
        }
        if (bookingRepository.countBySessionIdAndStatus(session.getId(), BookingStatus.CONFIRMED) >= session.getCapacity()) {
            throw new ConflictException("Session is fully booked", "FULL");
        }
        if (session.getBookedCount() >= session.getCapacity()) {
            // Same distinction as BE-11: do not repair or mislabel an inconsistent cached counter.
            throw rule("Session is fully booked", "CAPACITY_UNAVAILABLE");
        }

        SportPackageRegistration packageReg;
        if (request.getPackageRegistrationId() != null) {
            packageReg = registrationRepository.findById(request.getPackageRegistrationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + request.getPackageRegistrationId()));
            validateSelected(packageReg, session, member, actor);
        } else {
            var matching = PackageBookingEligibility.matching(registrationRepository.findForSessionEligibility(
                    member.getId(), session.getSport().getId(), session.getTrainingType()), session, member.getId());
            var usable = PackageBookingEligibility.usable(matching, session.getSessionDate());
            if (usable.isEmpty()) {
                throw rule("No active sport package found covering sport " + session.getSport().getName()
                        + " on " + session.getSessionDate(), PackageBookingEligibility.rejection(matching, session.getSessionDate()).name());
            }
            packageReg = usable.getFirst();
        }
        if (packageReg.getRemainingSessions() <= 0) {
            throw rule("Selected package registration has zero remaining sessions", "NO_SESSIONS_LEFT");
        }
        if (sessionRepository.reserveSeat(session.getId(), now) != 1) {
            throw new ConflictException("Session is fully booked", "FULL");
        }
        entityManager.refresh(session);
        if (registrationRepository.consumeSession(packageReg.getId(), PackageRegistrationStatus.ACTIVE.name(),
                session.getSessionDate(), now) != 1) {
            var state = registrationRepository.findBookingStateById(packageReg.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + packageReg.getId()));
            if (state.getStatus() != PackageRegistrationStatus.ACTIVE) {
                throw rule("Package registration is not active: " + state.getStatus(),
                        state.getStatus() == PackageRegistrationStatus.EXPIRED ? "PACKAGE_EXPIRED_BY_THEN" : "NO_PACKAGE");
            }
            validateDates(session.getSessionDate(), state.getStartDate(), state.getEndDate());
            throw rule("Selected package registration has zero remaining sessions", "NO_SESSIONS_LEFT");
        }

        String bookingCode = codeFormatter.formatBookingCode(bookingRepository.getNextBookingCodeSequence());
        Booking booking = Booking.builder().bookingCode(bookingCode).session(session).member(member)
                .packageRegistration(packageReg).status(BookingStatus.CONFIRMED).bookedAt(now).createdAt(now).build();
        try {
            booking = bookingRepository.save(booking);
            bookingRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (isUxBookingActiveViolation(ex)) throw new ConflictException(DUPLICATE_BOOKING_MESSAGE, "ALREADY_BOOKED");
            throw ex;
        }
        audit(booking, actor, AuditAction.BOOKING_CREATED);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, UserAccount actor) {
        requireActor(actor, false);
        requirePositiveId(bookingId, "Booking ID");
        Long sessionId = bookingRepository.findSessionIdByBookingId(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for id: " + bookingId));
        ClassSession session = sessionRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Class session not found for id: " + sessionId));
        entityManager.refresh(session);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for id: " + bookingId));
        entityManager.refresh(booking);
        if (SecurityUtils.isMember(actor) && !booking.getMember().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Cannot cancel another member's booking");
        }
        if (booking.getStatus() == BookingStatus.CANCELLED) throw rule("Booking is already cancelled", "ALREADY_CANCELLED");
        LocalDateTime now = LocalDateTime.now(clock);
        if (checkInRepository.existsByBookingIdAndResult(bookingId, CheckInResult.ALLOWED)) {
            throw rule("Cannot cancel booking that has already been checked in", "ALREADY_CHECKED_IN");
        }
        if (session.getSessionDate().isBefore(now.toLocalDate())) {
            throw rule("Cannot cancel a past session booking", "SESSION_STARTED");
        }
        if (session.getStatus() == SessionStatus.CANCELLED || session.getStatus() == SessionStatus.COMPLETED) {
            throw rule("Cannot cancel a booking in a cancelled or completed session", "SESSION_UNAVAILABLE");
        }
        LocalDateTime start = LocalDateTime.of(session.getSessionDate(), session.getStartTime());
        if (SecurityUtils.isMember(actor)) {
            if (!now.isBefore(start)) throw rule("Cannot cancel a session that has already started", "SESSION_STARTED");
            if (now.isAfter(start.minusMinutes(MEMBER_CANCELLATION_CUTOFF_MINUTES))) {
                throw rule("Member cancellation requires at least 120 minutes before session start", "CANCELLATION_CUTOFF");
            }
        } else if (!now.isBefore(LocalDateTime.of(session.getSessionDate(), session.getEndTime()))) {
            throw rule("Cannot cancel a session that has already ended", "SESSION_ENDED");
        }
        String reason = "Cancelled by user";
        int changed = bookingRepository.cancelConfirmedSessionBookings(List.of(bookingId), sessionId, actor.getId(), reason, now);
        if (changed != 1) {
            if (checkInRepository.existsByBookingIdAndResult(bookingId, CheckInResult.ALLOWED)) {
                throw rule("Cannot cancel booking that has already been checked in", "ALREADY_CHECKED_IN");
            }
            throw rule("Booking is already cancelled", "ALREADY_CANCELLED");
        }
        entityManager.refresh(booking);
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(now);
        booking.setCancelReason(reason);
        booking.setCancelledBy(actor);
        if (sessionRepository.releaseSeats(sessionId, 1, now) != 1) {
            throw new ConflictException("Booking changed while cancelling; reload and retry", "BOOKING_CHANGED");
        }
        entityManager.refresh(session);
        if (booking.getPackageRegistration() != null) {
            registrationRepository.restoreSession(booking.getPackageRegistration().getId(), PackageRegistrationStatus.ACTIVE.name(), now);
        }
        booking = bookingRepository.save(booking);
        bookingRepository.flush();
        audit(booking, actor, AuditAction.BOOKING_CANCELLED);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(UserAccount currentUser) {
        requireActor(currentUser, true);
        return getMemberBookings(currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(UserAccount actor, BookingWhen when) {
        requireActor(actor, true);
        if (when == null) return getMyBookings(actor);
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.findMyBookings(actor.getId(), when.name(), now.toLocalDate(), now.toLocalTime(),
                Pageable.unpaged(historySort(when))).map(this::mapToBookingResponse).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMyBookings(UserAccount actor, BookingWhen when, int page, int size) {
        requireActor(actor, true);
        if (page < 0 || size < 1 || size > PagingDefaults.MAX_PAGE_SIZE || (long) page * size > Integer.MAX_VALUE) {
            throw rule("Page must be nonnegative, size between 1 and 100, and page offset within 2147483647", "INVALID_PAGINATION");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return PageResponse.from(bookingRepository.findMyBookings(actor.getId(), when == null ? null : when.name(),
                now.toLocalDate(), now.toLocalTime(), PageRequest.of(page, size, historySort(when))).map(this::mapToBookingResponse));
    }

    private static Sort historySort(BookingWhen when) {
        if (when == null) return Sort.by(Sort.Direction.DESC, "bookedAt", "id");
        return Sort.by(when == BookingWhen.UPCOMING ? Sort.Direction.ASC : Sort.Direction.DESC,
                "session.sessionDate", "session.startTime", "id");
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMemberBookings(Long memberId) {
        return bookingRepository.findByMemberIdOrderByBookedAtDesc(memberId).stream()
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getTodayBookings(Long memberId) {
        LocalDate today = LocalDate.now(clock);
        return bookingRepository.findTodayBookingsForMember(memberId, today, BookingStatus.CONFIRMED).stream()
                .map(this::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SelfTrainingAttendanceResponse recordSelfTrainingAttendance(SelfTrainingAttendanceRequest request, UserAccount receptionist) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for id: " + request.getBookingId()));

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new BusinessRuleException("Cannot mark attendance for a non-confirmed booking");
        }

        SportPackageRegistration pkg = booking.getPackageRegistration();
        int remaining = pkg != null ? pkg.getRemainingSessions() : 0;

        return SelfTrainingAttendanceResponse.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .memberId(booking.getMember().getId())
                .memberCode(booking.getMember().getMemberCode())
                .memberFullName(booking.getMember().getUserAccount().getFullName())
                .sportName(booking.getSession().getSport().getName())
                .remainingSessions(remaining)
                .confirmedAt(LocalDateTime.now(clock))
                .recordedByName(receptionist.getFullName())
                .message("Self-training attendance acknowledged (persistence pending Flow 4 implementation)")
                .build();
    }

    private static void requireActor(UserAccount actor, boolean memberOnly) {
        if (actor == null || actor.getId() == null || !"ACTIVE".equals(actor.getStatus())
                || !(SecurityUtils.isMember(actor) || (!memberOnly && SecurityUtils.isStaff(actor)))) {
            throw new AccessDeniedException("An authenticated authorized account is required");
        }
    }

    private static void requirePositiveId(Long id, String label) {
        if (id == null || id <= 0) throw rule(label + " must be positive", "INVALID_REQUEST");
    }

    private static BusinessRuleException rule(String message, String code) {
        return new BusinessRuleException(message, code);
    }

    private static void validateDates(LocalDate date, LocalDate start, LocalDate end) {
        if (date.isBefore(start) || date.isAfter(end)) {
            throw rule("Session date " + date + " is outside package validity period (" + start + " to " + end + ")",
                    date.isBefore(start) ? "PACKAGE_NOT_STARTED_BY_THEN" : "PACKAGE_EXPIRED_BY_THEN");
        }
    }

    private static void validateSelected(SportPackageRegistration registration, ClassSession session,
            MemberProfile member, UserAccount actor) {
        if (!registration.getMember().getId().equals(member.getId())) {
            if (SecurityUtils.isMember(actor)) throw new AccessDeniedException("Cannot use another member's package registration");
            throw rule("Selected package registration does not belong to the member", "NO_PACKAGE");
        }
        if (registration.getStatus() != PackageRegistrationStatus.ACTIVE) {
            throw rule("Package registration is not active: " + registration.getStatus(),
                    registration.getStatus() == PackageRegistrationStatus.EXPIRED ? "PACKAGE_EXPIRED_BY_THEN" : "NO_PACKAGE");
        }
        validateDates(session.getSessionDate(), registration.getStartDate(), registration.getEndDate());
        if (registration.getSportPackage() == null || registration.getSportPackage().getSport() == null
                || !registration.getSportPackage().getSport().getId().equals(session.getSport().getId())) {
            throw rule("Package sport does not match session sport", "NO_PACKAGE");
        }
        if (registration.getSportPackage().getTrainingFormat() != session.getTrainingType()) {
            throw rule("Package training format does not match session training type", "NO_PACKAGE");
        }
    }

    private void audit(Booking booking, UserAccount actor, String action) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sessionId", booking.getSession().getId());
        details.put("memberId", booking.getMember().getId());
        details.put("packageRegistrationId", booking.getPackageRegistration() == null ? null : booking.getPackageRegistration().getId());
        details.put("status", booking.getStatus());
        details.put("cancelledAt", booking.getCancelledAt());
        auditService.record(AuditEvent.builder().actor(actor).action(action).entityType("BOOKING")
                .entityId(booking.getId()).entityCode(booking.getBookingCode())
                .summary(action + " booking " + booking.getBookingCode()).details(details).build());
    }

    private BookingResponse mapToBookingResponse(Booking b) {
        String cancelledByName = null;
        String cancelledByRole = null;
        if (b.getCancelledBy() != null) {
            cancelledByName = b.getCancelledBy().getFullName();
            if (b.getCancelledBy().getRole() != null) {
                cancelledByRole = b.getCancelledBy().getRole().getCode();
            }
        }

        return BookingResponse.builder()
                .id(b.getId())
                .bookingCode(b.getBookingCode())
                .sessionId(b.getSession().getId())
                .sessionDate(b.getSession().getSessionDate())
                .startTime(b.getSession().getStartTime())
                .endTime(b.getSession().getEndTime())
                .sportName(b.getSession().getSport().getName())
                .sportId(b.getSession().getSport().getId())
                .classId(b.getSession().getSportClass() == null ? null : b.getSession().getSportClass().getId())
                .className(b.getSession().getSportClass() == null ? null : b.getSession().getSportClass().getName())
                .coachId(b.getSession().getCoach() == null ? null : b.getSession().getCoach().getId())
                .coachName(b.getSession().getCoach() == null ? null : b.getSession().getCoach().getUserAccount().getFullName())
                .facilityId(b.getSession().getFacility() == null ? null : b.getSession().getFacility().getId())
                .facilityName(b.getSession().getFacility() == null ? null : b.getSession().getFacility().getName())
                .trainingType(b.getSession().getTrainingType())
                .memberId(b.getMember().getId())
                .memberCode(b.getMember().getMemberCode())
                .memberFullName(b.getMember().getUserAccount().getFullName())
                .packageRegistrationId(b.getPackageRegistration() != null ? b.getPackageRegistration().getId() : null)
                .packageId(b.getPackageRegistration() == null || b.getPackageRegistration().getSportPackage() == null
                        ? null : b.getPackageRegistration().getSportPackage().getId())
                .packageName(b.getPackageRegistration() == null || b.getPackageRegistration().getSportPackage() == null
                        ? null : b.getPackageRegistration().getSportPackage().getName())
                .status(b.getStatus())
                .bookedAt(b.getBookedAt())
                .cancelledAt(b.getCancelledAt())
                .cancelReason(b.getCancelReason() != null && b.getCancelReason().startsWith("Cancelled by user:")
                        ? "Cancelled by user" : b.getCancelReason())
                .cancelledById(b.getCancelledBy() == null ? null : b.getCancelledBy().getId())
                .cancelledByName(cancelledByName)
                .cancelledByRole(cancelledByRole)
                .build();
    }

    private boolean isUxBookingActiveViolation(DataIntegrityViolationException ex) {
        Throwable root = ex.getMostSpecificCause();
        String message = root != null && root.getMessage() != null ? root.getMessage() : ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("ux_booking_active");
    }
}
