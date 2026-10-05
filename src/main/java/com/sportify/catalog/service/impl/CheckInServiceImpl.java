package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.CheckInRequest;
import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckIn;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.Membership;
import com.sportify.catalog.entity.MembershipStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.MembershipRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.CheckInService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
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
public class CheckInServiceImpl implements CheckInService {

    private final CheckInRepository checkInRepository;
    private final BookingRepository bookingRepository;
    private final SportPackageRegistrationRepository packageRegistrationRepository;
    private final MembershipRepository membershipRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AuditService auditService;
    private final Clock clock;

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

        LocalDate today = LocalDate.now(clock);
        List<Booking> todayBookings = bookingRepository.findTodayBookingsForMember(profile.getId(), today, BookingStatus.CONFIRMED);

        Booking targetBooking = null;
        SportPackageRegistration targetPackage = null;
        Membership activeMembership = null;
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
                targetBooking = todayBookings.get(0);
            }

            if (targetBooking != null) {
                // Check if already checked in for this booking today
                boolean alreadyCheckedIn = checkInRepository.existsByBookingIdAndResult(targetBooking.getId(), CheckInResult.ALLOWED);
                if (alreadyCheckedIn) {
                    denialReason = "Already checked in for this session";
                } else {
                    // Check package or membership coverage
                    Long sportId = targetBooking.getSession().getSport().getId();
                    List<SportPackageRegistration> activePackages = packageRegistrationRepository.findActiveRegistrationsForSport(
                            profile.getId(), sportId, PackageRegistrationStatus.ACTIVE, today);

                    if (!activePackages.isEmpty()) {
                        targetPackage = activePackages.get(0);
                        result = CheckInResult.ALLOWED;
                    } else if (targetBooking.getPackageRegistration() != null && targetBooking.getPackageRegistration().getStatus() == PackageRegistrationStatus.ACTIVE) {
                        targetPackage = targetBooking.getPackageRegistration();
                        result = CheckInResult.ALLOWED;
                    } else {
                        // Legacy membership fallback check
                        List<Membership> allMemberships = membershipRepository.findAllByMemberIdOrderByStartDateDesc(profile.getId());
                        activeMembership = allMemberships.stream()
                                .filter(m -> m.getStatus() == MembershipStatus.ACTIVE
                                        && m.getStartDate() != null && m.getEndDate() != null
                                        && !today.isBefore(m.getStartDate())
                                        && !today.isAfter(m.getEndDate()))
                                .findFirst()
                                .orElse(null);

                        if (activeMembership != null) {
                            result = CheckInResult.ALLOWED;
                        } else {
                            denialReason = "No active sport package";
                        }
                    }
                }
            }
        }

        CheckIn checkIn = CheckIn.builder()
                .member(profile)
                .booking(targetBooking)
                .packageRegistration(targetPackage)
                .membership(activeMembership)
                .checkedInAt(LocalDateTime.now(clock))
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
                .memberName(profile.getUserAccount().getFullName())
                .membershipId(activeMembership != null ? activeMembership.getId() : null)
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
                .map(ci -> CheckInResponse.builder()
                        .id(ci.getId())
                        .memberId(ci.getMember().getId())
                        .memberName(ci.getMember().getUserAccount().getFullName())
                        .membershipId(ci.getMembership() != null ? ci.getMembership().getId() : null)
                        .bookingId(ci.getBooking() != null ? ci.getBooking().getId() : null)
                        .bookingCode(ci.getBooking() != null ? ci.getBooking().getBookingCode() : null)
                        .packageRegistrationId(ci.getPackageRegistration() != null ? ci.getPackageRegistration().getId() : null)
                        .checkedInAt(ci.getCheckedInAt())
                        .result(ci.getResult().name())
                        .denialReason(ci.getDenialReason())
                        .note(ci.getNote())
                        .build())
                .collect(Collectors.toList());
    }
}
