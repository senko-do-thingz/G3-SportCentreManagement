package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SessionDetailResponse;
import com.sportify.catalog.dto.SessionResponse;
import com.sportify.catalog.dto.SessionSearchQuery;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.MemberScheduleService;
import com.sportify.catalog.service.PackageBookingEligibility;
import com.sportify.core.common.PagingDefaults;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.sportify.catalog.dto.SessionDetailResponse.EligibilityReason.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberScheduleServiceImpl implements MemberScheduleService {
    private static final Sort SESSION_SORT = Sort.by("sessionDate", "startTime", "id");
    private final ClassSessionRepository sessions;
    private final BookingRepository bookings;
    private final SportPackageRegistrationRepository registrations;
    private final MemberProfileRepository members;
    private final Clock clock;

    @Override
    public PageResponse<SessionResponse> listSessions(SessionSearchQuery query, UserAccount actor) {
        requireReader(actor);
        validateQuery(query);
        LocalDateTime now = LocalDateTime.now(clock);
        var page = sessions.searchVisible(query.sportId(), query.from(), query.to(), query.format(),
                query.level() == null ? null : query.level().name(), query.coachId(), now.toLocalDate(), now.toLocalTime(),
                PageRequest.of(query.page(), query.size(), SESSION_SORT));
        Map<Long, Long> counts = counts(page.getContent());
        return PageResponse.from(page.map(s -> response(s, counts.getOrDefault(s.getId(), 0L))));
    }

    @Override
    public SessionDetailResponse getSession(Long id, UserAccount actor) {
        requireReader(actor);
        requirePositiveId(id, "Session id");
        LocalDateTime now = LocalDateTime.now(clock);
        ClassSession session = sessions.findVisibleById(id, now.toLocalDate(), now.toLocalTime())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
        var common = response(session, counts(List.of(session)).getOrDefault(id, 0L));
        if (SecurityUtils.isStaff(actor)) {
            return new SessionDetailResponse(common, false, null, null, null, null, List.of());
        }
        // Profiles share the account primary key. No request parameter can change this owner.
        var member = members.findById(actor.getId())
                .orElseThrow(() -> new AccessDeniedException("A member profile is required"));
        if (!Objects.equals(member.getId(), actor.getId())) throw new AccessDeniedException("Invalid member context");
        boolean alreadyBooked = bookings.findBySessionIdAndMemberIdAndStatus(id, member.getId(), BookingStatus.CONFIRMED).isPresent();
        if (alreadyBooked) return rejected(common, true, ALREADY_BOOKED);
        if (common.full()) return rejected(common, false, FULL);
        // The existing write path checks the cache. Do not promise eligibility when it already blocks booking.
        // Real confirmed counts still determine full/freeSeats; a stale cache must not be mislabeled FULL.
        if (session.getBookedCount() >= session.getCapacity()) return rejected(common, false, CAPACITY_UNAVAILABLE);

        List<SportPackageRegistration> matching = PackageBookingEligibility.matching(
                registrations.findForSessionEligibility(member.getId(), session.getSport().getId(), session.getTrainingType()),
                session, member.getId());
        LocalDate date = session.getSessionDate();
        var usable = PackageBookingEligibility.usable(matching, date).stream()
                .map(MemberScheduleServiceImpl::registrationResponse).toList();
        if (!usable.isEmpty()) return new SessionDetailResponse(common, true, null, false, true, null, usable);
        return rejected(common, false, PackageBookingEligibility.rejection(matching, date));
    }

    private static SessionDetailResponse rejected(SessionResponse session, boolean booked,
            SessionDetailResponse.EligibilityReason reason) {
        return new SessionDetailResponse(session, true, booked ? BookingStatus.CONFIRMED : null,
                booked, false, reason, List.of());
    }

    private Map<Long, Long> counts(List<ClassSession> rows) {
        if (rows.isEmpty()) return Map.of();
        return bookings.countBySessionIds(rows.stream().map(ClassSession::getId).toList(), BookingStatus.CONFIRMED)
                .stream().collect(Collectors.toMap(BookingRepository.SessionBookingCount::getSessionId,
                        BookingRepository.SessionBookingCount::getBookingCount));
    }

    private static SessionResponse response(ClassSession s, long booked) {
        int capacity = Math.max(0, s.getCapacity());
        long free = Math.max(0L, capacity - booked);
        var c = s.getSportClass();
        var coach = s.getCoach();
        var facility = s.getFacility();
        return new SessionResponse(s.getId(), s.getSport().getId(), s.getSport().getName(),
                c == null ? null : c.getId(), c == null ? null : c.getName(), s.getTrainingType(),
                c == null ? null : c.getLevel(), coach == null ? null : coach.getId(),
                coach == null ? null : coach.getUserAccount().getFullName(),
                facility == null ? null : facility.getId(), facility == null ? null : facility.getName(),
                s.getSessionDate(), s.getStartTime(), s.getEndTime(), capacity, booked, free, free == 0, s.getStatus());
    }

    private static SessionDetailResponse.SuitableRegistration registrationResponse(SportPackageRegistration r) {
        var p = r.getSportPackage();
        return new SessionDetailResponse.SuitableRegistration(r.getId(), r.getRegistrationCode(), p.getId(), p.getName(),
                p.getSport().getId(), p.getTrainingFormat(), r.getStartDate(), r.getEndDate(), r.getRemainingSessions());
    }

    private static void validateQuery(SessionSearchQuery q) {
        if (q == null) throw new BusinessRuleException("Session search parameters are required");
        if (q.page() < 0 || q.size() < 1 || q.size() > PagingDefaults.MAX_PAGE_SIZE
                || (long) q.page() * q.size() > Integer.MAX_VALUE) {
            throw new BusinessRuleException("Page must be nonnegative, size between 1 and 100, and page offset within 2147483647");
        }
        if (q.sportId() != null) requirePositiveId(q.sportId(), "Sport id");
        if (q.coachId() != null) requirePositiveId(q.coachId(), "Coach id");
        validateDate(q.from());
        validateDate(q.to());
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to())) {
            throw new BusinessRuleException("From date must not be after to date");
        }
    }

    private static void validateDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new BusinessRuleException("Dates must be within years 1 through 9999");
        }
    }

    private static void requirePositiveId(Long id, String label) {
        if (id == null || id <= 0) throw new BusinessRuleException(label + " must be positive");
    }

    private static void requireReader(UserAccount actor) {
        if (actor == null || actor.getId() == null || !"ACTIVE".equals(actor.getStatus())
                || !(SecurityUtils.isMember(actor) || SecurityUtils.isStaff(actor))) {
            throw new AccessDeniedException("An authenticated member or staff account is required");
        }
    }
}
