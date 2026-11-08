package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import jakarta.persistence.EntityManager;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.service.CheckInService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CheckInServiceImpl implements CheckInService {

    private final CheckInRepository checkInRepository;
    private final BookingRepository bookingRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AuditService auditService;
    private final Clock clock;
    private final ClassSessionRepository sessionRepository;
    private final EntityManager entityManager;
    private final com.sportify.catalog.service.OperationalMemberPolicy operationalMemberPolicy;

    private static final Comparator<Booking> BY_SESSION_START = Comparator
            .comparing((Booking b) -> b.getSession().getStartTime())
            .thenComparing(Booking::getId);

    static final String SESSION_ENDED_REASON = "The booked session has already ended";
    private static final java.time.format.DateTimeFormatter HH_MM = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Arrival window (E2E-D6 decision, replacing the former same-day rule): check-in opens this many minutes before the
     * session start (inclusive) and closes at the session end (exclusive). A late arrival during the session is still
     * accepted; the Coach records LATE. After the end the booking counts as a no-show.
     */
    @Value("${app.check-in.opens-minutes-before:60}")
    private int opensMinutesBefore = 60;

    private static boolean hasEnded(Booking booking, LocalDateTime now) {
        return !now.isBefore(LocalDateTime.of(booking.getSession().getSessionDate(), booking.getSession().getEndTime()));
    }

    private LocalDateTime opensAt(Booking booking) {
        return LocalDateTime.of(booking.getSession().getSessionDate(), booking.getSession().getStartTime())
                .minusMinutes(Math.max(0, opensMinutesBefore));
    }

    @Override
    @Transactional
    public CheckInResponse recordCheckIn(CheckInRequest request, UserAccount receptionist) {
        String identifier = request.getIdentifier();
        Optional<MemberProfile> profileOpt = memberProfileRepository.findByMemberCode(identifier);
        if (profileOpt.isEmpty()) {
            try {
                Long id = Long.parseLong(identifier);
                profileOpt = memberProfileRepository.findById(id);
            } catch (NumberFormatException ignored) {}
        }

        MemberProfile profile = profileOpt.orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        UserAccount member = profile.getUserAccount();
        if (member == null || !"ACTIVE".equals(member.getStatus()) || member.getRole() == null
                || !"MEMBER".equals(member.getRole().getCode())) {
            throw new BusinessRuleException("An enabled Member account is required", "CHECK_IN_MEMBER_UNAVAILABLE");
        }
        operationalMemberPolicy.requireRealMember(profile.getId());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        List<Booking> todayBookings = bookingRepository.findTodayBookingsForMember(profile.getId(), today, BookingStatus.CONFIRMED);

        Booking targetBooking = null;
        SportPackageRegistration targetPackage = null;
        CheckInResult result = CheckInResult.DENIED;
        String denialReason = null;

        // Front desk check-in strictly requires an existing confirmed session booking for the current day (Decision 3 / Screen F1-15)
        if (todayBookings.isEmpty()) {
            denialReason = "No confirmed booking for today";
        } else {
            if (request.getBookingId() != null) {
                targetBooking = todayBookings.stream()
                        .filter(b -> b.getId().equals(request.getBookingId()))
                        .findFirst()
                        .orElse(null);
                if (targetBooking == null) {
                    denialReason = "Specified booking is not scheduled for today or is not confirmed";
                }
            } else {
                // Deterministic choice: the earliest not-checked-in session that has not ended yet, so a missed
                // morning session never absorbs the arrival for a later one. When every remaining session has
                // ended, the earliest one is chosen and the arrival window below denies it as ended.
                List<Booking> notCheckedIn = todayBookings.stream()
                        .filter(b -> !checkInRepository.existsByBookingIdAndResult(b.getId(), CheckInResult.ALLOWED))
                        .sorted(BY_SESSION_START)
                        .toList();
                targetBooking = notCheckedIn.stream()
                        .filter(b -> !hasEnded(b, now))
                        .findFirst()
                        .orElse(notCheckedIn.isEmpty() ? null : notCheckedIn.getFirst());
                if (targetBooking == null) {
                    denialReason = "All confirmed bookings for today have already been checked in";
                }
            }

            if (targetBooking != null) {
                operationalMemberPolicy.requireRealBooking(targetBooking.getId());
                // Join the booking/cancellation lock order before checking mutable state or inserting ALLOWED.
                var lockedSession = sessionRepository.findForUpdate(targetBooking.getSession().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Class session not found"));
                entityManager.refresh(lockedSession);
                entityManager.refresh(targetBooking);
                // Check if already checked in for this booking today
                boolean alreadyCheckedIn = checkInRepository.existsByBookingIdAndResult(targetBooking.getId(), CheckInResult.ALLOWED);
                if (targetBooking.getStatus() != BookingStatus.CONFIRMED || !targetBooking.getSession().getSessionDate().equals(today)) {
                    denialReason = "Specified booking is not scheduled for today or is not confirmed";
                } else if (alreadyCheckedIn) {
                    denialReason = "Already checked in for this session";
                } else if (hasEnded(targetBooking, now)) {
                    denialReason = SESSION_ENDED_REASON;
                } else if (now.isBefore(opensAt(targetBooking))) {
                    denialReason = "Check-in opens at " + opensAt(targetBooking).toLocalTime().format(HH_MM)
                            + ", " + opensMinutesBefore + " minutes before the session starts";
                } else {
                    // Check package coverage directly from booking
                    SportPackageRegistration pkg = targetBooking.getPackageRegistration();
                    if (pkg != null) entityManager.refresh(pkg);
                    if (pkg == null) {
                        denialReason = "Booking is not linked to a sport package registration";
                    } else if (pkg.getStatus() != PackageRegistrationStatus.ACTIVE) {
                        denialReason = "No active sport package";
                    } else if (today.isBefore(pkg.getStartDate()) || today.isAfter(pkg.getEndDate())) {
                        denialReason = "Sport package registration is expired or not yet valid";
                    } else {
                        targetPackage = pkg;
                        result = CheckInResult.ALLOWED;
                    }
                }
            }
        }

        CheckIn checkIn = CheckIn.builder()
                .member(profile)
                .booking(targetBooking)
                .packageRegistration(targetPackage)
                .checkedInAt(now)
                .result(result)
                .denialReason(denialReason)
                .recordedBy(receptionist)
                .note(request.getNote())
                .build();

        checkIn = checkInRepository.save(checkIn);

        auditService.record(AuditEvent.builder()
                .actor(receptionist)
                .action(AuditAction.MEMBER_CHECKED_IN)
                .entityType("CHECK_IN")
                .entityId(checkIn.getId())
                .entityCode(profile.getMemberCode())
                .summary("Check-in " + result.name() + " for member " + profile.getMemberCode())
                .build());

        return CheckInResponse.builder()
                .id(checkIn.getId())
                .memberId(profile.getId())
                .memberCode(profile.getMemberCode())
                .memberName(profile.getUserAccount().getFullName())
                .bookingId(targetBooking != null ? targetBooking.getId() : null)
                .bookingCode(targetBooking != null ? targetBooking.getBookingCode() : null)
                .packageRegistrationId(targetPackage != null ? targetPackage.getId() : null)
                .checkedInAt(checkIn.getCheckedInAt())
                .result(checkIn.getResult().name())
                .denialReason(checkIn.getDenialReason())
                .note(checkIn.getNote())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckInResponse> getCheckInHistory(Long memberId) {
        return checkInRepository.findByMemberIdOrderByCheckedInAtDesc(memberId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckInResponse> getCheckInsOn(LocalDate date, Long memberId) {
        // checkedInAt is stored as a centre-local LocalDateTime, using the configured Clock.
        if (date == null || date.getYear() < 1 || date.getYear() > 9999) {
            throw new BusinessRuleException("Date must be between 0001-01-01 and 9999-12-31");
        }
        LocalDateTime end = date.equals(LocalDate.of(9999, 12, 31)) ? null : date.plusDays(1).atStartOfDay();
        return checkInRepository.findForDay(date.atStartOfDay(), end, memberId)
                .stream().map(this::toResponse).toList();
    }

    private CheckInResponse toResponse(CheckIn ci) {
        return CheckInResponse.builder()
                .id(ci.getId())
                .memberId(ci.getMember().getId())
                .memberCode(ci.getMember().getMemberCode())
                .memberName(ci.getMember().getUserAccount().getFullName())
                .membershipId(ci.getMembership() != null ? ci.getMembership().getId() : null)
                .bookingId(ci.getBooking() != null ? ci.getBooking().getId() : null)
                .bookingCode(ci.getBooking() != null ? ci.getBooking().getBookingCode() : null)
                .packageRegistrationId(ci.getPackageRegistration() != null ? ci.getPackageRegistration().getId() : null)
                .checkedInAt(ci.getCheckedInAt())
                .result(ci.getResult().name())
                .denialReason(ci.getDenialReason())
                .note(ci.getNote())
                .build();
    }
}
