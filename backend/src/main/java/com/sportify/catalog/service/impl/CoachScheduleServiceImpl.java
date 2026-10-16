package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.CoachRosterResponse;
import com.sportify.catalog.dto.CoachScheduleQuery;
import com.sportify.catalog.dto.CoachSessionResponse;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CoachScheduleReadRepository;
import com.sportify.catalog.service.CoachScheduleService;
import com.sportify.core.exception.FieldValidationException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('COACH', 'MANAGER')")
public class CoachScheduleServiceImpl implements CoachScheduleService {
    private final CoachScheduleReadRepository reads;
    private final BookingRepository bookings;
    private final CoachProfileRepository coaches;
    private final UserRepository users;

    @Override
    public List<CoachSessionResponse> listSessions(String from, String to, Long coachId) {
        UserAccount actor = reader();
        var query = CoachScheduleQuery.parse(from, to, coachId);
        Long owner = scope(actor, query.coachId());
        var sessions = reads.findTeachingSessions(query.from(), query.to(), owner);
        if (sessions.isEmpty()) return List.of();
        // Reuse BE-10's grouped confirmed counts without an unbounded ID parameter list.
        Map<Long, Long> counts = bookings.countForSessionCalendar(query.from(), query.to(), null, owner, null,
                BookingStatus.CONFIRMED).stream().collect(Collectors.toMap(
                        BookingRepository.SessionBookingCount::getSessionId,
                        BookingRepository.SessionBookingCount::getBookingCount));
        return sessions.stream().map(s -> s.withBookedCount(counts.getOrDefault(s.id(), 0L))).toList();
    }

    @Override
    public List<CoachRosterResponse> roster(Long sessionId) {
        UserAccount actor = reader();
        if (sessionId == null || sessionId <= 0) throw new FieldValidationException("id", "Must be positive");
        boolean manager = isManager(actor);
        Long owner = manager ? null : ownCoachId(actor);
        var session = reads.findOwnership(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
        if (!manager && !Objects.equals(session.getCoachId(), owner)) throw denied();
        // Recheck ownership in the PII query itself, including concurrent reassignment.
        return reads.findRoster(sessionId, BookingStatus.CONFIRMED, manager, owner);
    }

    private Long scope(UserAccount actor, Long requestedCoachId) {
        if (isManager(actor)) {
            if (requestedCoachId != null && !coaches.existsById(requestedCoachId)) {
                throw new ResourceNotFoundException("Coach profile not found");
            }
            return requestedCoachId;
        }
        Long ownId = ownCoachId(actor);
        if (requestedCoachId != null && !requestedCoachId.equals(ownId)) throw denied();
        return ownId;
    }

    private Long ownCoachId(UserAccount actor) {
        // CoachProfile @MapsId and class_session.coach_id both reference the account's shared key.
        if (!coaches.existsById(actor.getId())) throw denied();
        return actor.getId();
    }

    private UserAccount reader() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CustomUserDetails principal)
                || principal.getUserAccount().getId() == null) throw denied();
        UserAccount actor = users.findById(principal.getUserAccount().getId()).orElseThrow(this::denied);
        if (!"ACTIVE".equals(actor.getStatus()) || actor.getRole() == null
                || !("COACH".equals(actor.getRole().getCode()) || isManager(actor))) throw denied();
        return actor;
    }

    private boolean isManager(UserAccount actor) { return "MANAGER".equals(actor.getRole().getCode()); }
    private AccessDeniedException denied() { return new AccessDeniedException("Access Denied"); }
}
