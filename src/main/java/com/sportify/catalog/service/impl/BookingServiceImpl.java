package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.dto.BookingResponse;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.BookingService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ClassSessionRepository sessionRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request, UserAccount actor) {
        Long targetMemberId = request.getMemberId() != null ? request.getMemberId() : actor.getId();
        MemberProfile member = memberProfileRepository.findById(targetMemberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for id: " + targetMemberId));

        ClassSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Class session not found for id: " + request.getSessionId()));

        if (session.getBookedCount() >= session.getCapacity()) {
            throw new BusinessRuleException("Session is fully booked");
        }

        // Check if member already booked this session
        Optional<Booking> existing = bookingRepository.findBySessionIdAndMemberIdAndStatus(session.getId(), member.getId(), BookingStatus.CONFIRMED);
        if (existing.isPresent()) {
            throw new BusinessRuleException("Member already has a confirmed booking for this session");
        }

        LocalDate today = LocalDate.now(clock);
        SportPackageRegistration packageReg;
        if (request.getPackageRegistrationId() != null) {
            packageReg = registrationRepository.findById(request.getPackageRegistrationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + request.getPackageRegistrationId()));
        } else {
            // Find an active package covering this sport
            List<SportPackageRegistration> activePackages = registrationRepository.findActiveRegistrationsForSport(
                    member.getId(), session.getSport().getId(), PackageRegistrationStatus.ACTIVE, session.getSessionDate());
            if (activePackages.isEmpty()) {
                throw new BusinessRuleException("No active sport package found covering sport " + session.getSport().getName() + " on " + session.getSessionDate());
            }
            packageReg = activePackages.get(0);
        }

        if (packageReg.getRemainingSessions() <= 0) {
            throw new BusinessRuleException("Selected package registration has zero remaining sessions");
        }

        // Reserve 1 session from package
        packageReg.setRemainingSessions(packageReg.getRemainingSessions() - 1);
        registrationRepository.save(packageReg);

        // Atomically increment booked count
        session.setBookedCount(session.getBookedCount() + 1);
        sessionRepository.save(session);

        long seq = bookingRepository.getNextBookingCodeSequence();
        String bookingCode = codeFormatter.formatBookingCode(seq);

        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .session(session)
                .member(member)
                .packageRegistration(packageReg)
                .status(BookingStatus.CONFIRMED)
                .bookedAt(LocalDateTime.now(clock))
                .build();

        booking = bookingRepository.save(booking);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(Long bookingId, UserAccount actor) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for id: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleException("Booking is already cancelled");
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now(clock));
        booking.setCancelReason("Cancelled by user: " + actor.getEmail());

        // Restore session seat
        ClassSession session = booking.getSession();
        if (session.getBookedCount() > 0) {
            session.setBookedCount(session.getBookedCount() - 1);
            sessionRepository.save(session);
        }

        // Restore 1 session to package registration
        if (booking.getPackageRegistration() != null) {
            SportPackageRegistration pkg = booking.getPackageRegistration();
            pkg.setRemainingSessions(pkg.getRemainingSessions() + 1);
            registrationRepository.save(pkg);
        }

        booking = bookingRepository.save(booking);
        return mapToBookingResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings(UserAccount currentUser) {
        return getMemberBookings(currentUser.getId());
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
                .message("Self-training attendance recorded and verified successfully")
                .build();
    }

    private BookingResponse mapToBookingResponse(Booking b) {
        return BookingResponse.builder()
                .id(b.getId())
                .bookingCode(b.getBookingCode())
                .sessionId(b.getSession().getId())
                .sessionDate(b.getSession().getSessionDate())
                .startTime(b.getSession().getStartTime())
                .endTime(b.getSession().getEndTime())
                .sportName(b.getSession().getSport().getName())
                .trainingType(b.getSession().getTrainingType())
                .memberId(b.getMember().getId())
                .memberCode(b.getMember().getMemberCode())
                .memberFullName(b.getMember().getUserAccount().getFullName())
                .packageRegistrationId(b.getPackageRegistration() != null ? b.getPackageRegistration().getId() : null)
                .status(b.getStatus())
                .bookedAt(b.getBookedAt())
                .build();
    }
}
