package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.ManagerSessionResponse;
import com.sportify.catalog.dto.SessionCancelRequest;
import com.sportify.catalog.dto.SessionConflictResponse;
import com.sportify.catalog.dto.SessionCreateRequest;
import com.sportify.catalog.dto.SessionUpdateRequest;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.Facility;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.SportClass;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.exception.ScheduleConflictException;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.ClassSessionSpecifications;
import com.sportify.catalog.repository.FacilityRepository;
import com.sportify.catalog.repository.SessionCancellationRepository;
import com.sportify.catalog.repository.SportClassRepository;
import com.sportify.catalog.service.ManagerSessionService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSportId;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerSessionServiceImpl implements ManagerSessionService {
    static final Set<SessionStatus> OCCUPYING_STATUSES = Set.of(SessionStatus.DRAFT, SessionStatus.PUBLISHED);
    private static final int MAX_CALENDAR_DAYS = 62;
    private static final int MAX_GENERATED_SESSIONS = 100;
    private static final Sort CALENDAR_SORT = Sort.by("sessionDate", "startTime", "id");

    private final ClassSessionRepository sessionRepository;
    private final SportClassRepository classRepository;
    private final CoachProfileRepository coachRepository;
    private final CoachSportRepository coachSportRepository;
    private final FacilityRepository facilityRepository;
    private final BookingRepository bookingRepository;
    private final SessionCancellationRepository cancellationRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<ManagerSessionResponse> listSessions(LocalDate from, LocalDate to,
            Long classId, Long coachId, Long facilityId) {
        validateRange(from, to);
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_CALENDAR_DAYS) {
            throw new BusinessRuleException("Calendar range must not exceed 62 days inclusive");
        }
        List<ClassSession> sessions = sessionRepository.findAll(
                ClassSessionSpecifications.calendar(from, to, classId, coachId, facilityId), CALENDAR_SORT);
        Map<Long, Long> counts = sessions.isEmpty() ? Map.of() : toBookingCounts(bookingRepository
                .countForSessionCalendar(from, to, classId, coachId, facilityId, BookingStatus.CONFIRMED));
        return sessions.stream().map(session -> toResponse(session, counts.getOrDefault(session.getId(), 0L))).toList();
    }

    @Override
    @Transactional
    public List<ManagerSessionResponse> createSessions(Long classId, SessionCreateRequest request, UserAccount actor) {
        return withLockHandling(() -> {
            validateTimes(request.startTime(), request.endTime());
            List<LocalDate> dates = generateDates(request);
            SessionStatus status = request.status() == null ? SessionStatus.PUBLISHED : request.status();
            if (!OCCUPYING_STATUSES.contains(status)) {
                throw new BusinessRuleException("New session status must be DRAFT or PUBLISHED");
            }
            SportClass sportClass = activeClass(classId);
            validateCapacity(request.capacity(), sportClass, 0);
            if (request.trainingType() == null) {
                throw new BusinessRuleException("Training type is required");
            }
            Assignment assignment = lockAndValidateAssignment(sportClass, request.coachId(),
                    request.facilityId(), request.trainingType());
            validateFutureDates(dates, request.startTime());
            checkConflicts(dates, request.startTime(), request.endTime(), assignment, null);

            // Validate the complete batch before the first INSERT. All rows and audits share this transaction.
            List<ClassSession> sessions = dates.stream().map(date -> ClassSession.builder()
                    .sportClass(sportClass).sport(sportClass.getSport()).sessionDate(date)
                    .startTime(request.startTime()).endTime(request.endTime())
                    .coach(assignment.coach()).facility(assignment.facility()).capacity(request.capacity())
                    .trainingType(request.trainingType()).status(status).bookedCount(0)
                    .createdAt(LocalDateTime.now(clock)).build()).toList();
            List<ClassSession> saved = sessionRepository.saveAllAndFlush(sessions);
            saved.forEach(session -> audit(session, actor, AuditAction.SESSION_CREATED, snapshot(session)));
            return saved.stream().map(session -> toResponse(session, 0)).toList();
        });
    }

    @Override
    @Transactional
    public ManagerSessionResponse updateSession(Long id, SessionUpdateRequest request, UserAccount actor) {
        return withLockHandling(() -> {
            validateTimes(request.startTime(), request.endTime());
            ClassSession session = findForUpdate(id);
            ensureCanChange(session);
            SportClass sportClass = session.getSportClass();
            if (sportClass == null) {
                throw new BusinessRuleException("Only sessions linked to a class can be edited here");
            }
            ensureActiveClass(sportClass);
            long booked = bookingCounts(List.of(id)).getOrDefault(id, 0L);
            validateCapacity(request.capacity(), sportClass, booked);
            Assignment assignment = lockAndValidateAssignment(sportClass, request.coachId(),
                    request.facilityId(), session.getTrainingType());
            validateFutureDates(List.of(requireDate(request.date())), request.startTime());
            checkConflicts(List.of(request.date()), request.startTime(), request.endTime(), assignment, id);
            Map<String, Object> before = snapshot(session);
            session.setSessionDate(request.date());
            session.setStartTime(request.startTime());
            session.setEndTime(request.endTime());
            session.setFacility(assignment.facility());
            session.setCoach(assignment.coach());
            session.setCapacity(request.capacity());
            ClassSession saved = sessionRepository.saveAndFlush(session);
            audit(saved, actor, AuditAction.SESSION_UPDATED, Map.of("before", before, "after", snapshot(saved)));
            return toResponse(saved, booked);
        });
    }

    @Override
    @Transactional
    public ManagerSessionResponse cancelSession(Long id, SessionCancelRequest request, UserAccount actor) {
        return withLockHandling(() -> {
            if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 255) {
                throw new BusinessRuleException("Cancellation reason is required and must be at most 255 characters");
            }
            ClassSession session = findForUpdate(id);
            ensureCanChange(session);
            if (actor == null || actor.getId() == null) {
                throw new BusinessRuleException("An authenticated manager is required to cancel a session");
            }
            Map<String, Object> before = snapshot(session);
            LocalDateTime now = LocalDateTime.now(clock);
            int cancelled = cancellationRepository.cancelConfirmedBookings(id, actor.getId(), request.reason(), now);
            session.setStatus(SessionStatus.CANCELLED);
            session.setCancelReason(request.reason());
            session.setCancelledBy(actor);
            session.setCancelledAt(now);
            session.setBookedCount(0);
            ClassSession saved = sessionRepository.saveAndFlush(session);
            audit(saved, actor, AuditAction.SESSION_CANCELLED, Map.of("before", before,
                    "after", snapshot(saved), "reason", request.reason(), "bookingsCancelled", cancelled));
            return toResponse(saved, 0);
        });
    }

    /** Count first so even a very long recurrence range cannot cause an unbounded date-generation loop. */
    private List<LocalDate> generateDates(SessionCreateRequest request) {
        boolean hasWeeklyFields = request.daysOfWeek() != null || request.fromDate() != null || request.toDate() != null;
        if (request.date() != null) {
            if (hasWeeklyFields) {
                throw new BusinessRuleException("Choose exactly one mode: date or weekly repeat");
            }
            return List.of(requireDate(request.date()));
        }
        if (!hasWeeklyFields || request.daysOfWeek() == null || request.daysOfWeek().isEmpty()
                || request.daysOfWeek().stream().anyMatch(Objects::isNull)) {
            throw new BusinessRuleException("Weekly repeat requires daysOfWeek, fromDate and toDate");
        }
        validateRange(request.fromDate(), request.toDate());
        if (request.fromDate().isBefore(LocalDate.now(clock))) {
            throw new BusinessRuleException("Cannot schedule sessions in the past");
        }
        long span = ChronoUnit.DAYS.between(request.fromDate(), request.toDate());
        List<LocalDate> dates = new ArrayList<>();
        for (DayOfWeek day : request.daysOfWeek()) {
            int offset = Math.floorMod(day.getValue() - request.fromDate().getDayOfWeek().getValue(), 7);
            long occurrences = offset > span ? 0 : 1 + (span - offset) / 7;
            if (dates.size() + occurrences > MAX_GENERATED_SESSIONS) {
                throw new BusinessRuleException("Weekly repeat must not generate more than 100 sessions");
            }
            for (int i = 0; i < occurrences; i++) {
                dates.add(request.fromDate().plusDays(offset + 7L * i));
            }
        }
        if (dates.isEmpty()) {
            throw new BusinessRuleException("Weekly repeat must generate at least one session");
        }
        return dates.stream().sorted().toList();
    }

    private Assignment lockAndValidateAssignment(SportClass sportClass, Long coachId, Long facilityId,
            TrainingFormat trainingType) {
        // Lock order: session first on edits, then coach, then facility; creates have no session row.
        CoachProfile coach = null;
        if (coachId == null && trainingType == TrainingFormat.COACH_LED) {
            throw new BusinessRuleException("Coach is required for a coach-led session");
        }
        if (coachId != null) {
            coach = coachRepository.findForScheduleUpdate(coachId)
                    .orElseThrow(() -> new ResourceNotFoundException("Coach not found: " + coachId));
            UserAccount account = coach.getUserAccount();
            if (account == null || account.getRole() == null || !"COACH".equals(account.getRole().getCode())
                    || !UserStatus.ACTIVE.name().equals(account.getStatus())) {
                throw new BusinessRuleException("Assigned coach must be active and have the COACH role");
            }
            if (!coachSportRepository.existsById(new CoachSportId(coachId, sportClass.getSport().getId()))) {
                throw new BusinessRuleException("Coach '" + account.getFullName() + "' does not teach "
                        + sportClass.getSport().getName() + ". Choose a coach qualified for this sport.");
            }
        }
        if (facilityId == null) {
            throw new BusinessRuleException("Facility is required");
        }
        // Lock order: session first on edits, then coach, then facility.
        Facility facility = facilityRepository.findForScheduleUpdate(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found: " + facilityId));
        if (!Boolean.TRUE.equals(facility.getIsActive())) {
            throw new BusinessRuleException("Assigned facility must be active");
        }
        return new Assignment(coach, facility);
    }

    private void checkConflicts(List<LocalDate> dates, LocalTime start, LocalTime end,
            Assignment assignment, Long excludedId) {
        Map<Long, ClassSession> conflicts = new LinkedHashMap<>();
        if (assignment.coach() != null) {
            sessionRepository.findCoachConflicts(assignment.coach().getId(), dates, start, end,
                    OCCUPYING_STATUSES, excludedId).forEach(session -> conflicts.put(session.getId(), session));
        }
        sessionRepository.findFacilityConflicts(assignment.facility().getId(), dates, start, end,
                OCCUPYING_STATUSES, excludedId).forEach(session -> conflicts.put(session.getId(), session));
        if (!conflicts.isEmpty()) {
            List<SessionConflictResponse> responses = conflicts.values().stream()
                    .sorted(Comparator.comparing(ClassSession::getSessionDate).thenComparing(ClassSession::getStartTime)
                            .thenComparing(ClassSession::getId))
                    .map(session -> {
                        boolean sameCoach = assignment.coach() != null && session.getCoach() != null
                                && Objects.equals(assignment.coach().getId(), session.getCoach().getId());
                        boolean sameFacility = session.getFacility() != null
                                && Objects.equals(assignment.facility().getId(), session.getFacility().getId());
                        SessionConflictResponse.ConflictType type = sameCoach && sameFacility
                                ? SessionConflictResponse.ConflictType.BOTH
                                : sameCoach ? SessionConflictResponse.ConflictType.COACH : SessionConflictResponse.ConflictType.FACILITY;
                        return new SessionConflictResponse(session.getId(), session.getSessionDate(), session.getSessionDate(),
                                session.getStartTime(), session.getEndTime(), classSummary(session.getSportClass()),
                                coachSummary(session.getCoach()), facilitySummary(session.getFacility()), type);
                    }).toList();
            throw new ScheduleConflictException(responses);
        }
    }

    private SportClass activeClass(Long id) {
        SportClass sportClass = classRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + id));
        ensureActiveClass(sportClass);
        return sportClass;
    }

    private void ensureActiveClass(SportClass sportClass) {
        if (!Boolean.TRUE.equals(sportClass.getIsActive())) {
            throw new BusinessRuleException("Class must be active to schedule or edit sessions");
        }
    }

    private ClassSession findForUpdate(Long id) {
        // Lock order: session first, then coach/facility or booking range/package registrations.
        return sessionRepository.findForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + id));
    }

    private void ensureCanChange(ClassSession session) {
        if (!OCCUPYING_STATUSES.contains(session.getStatus())) {
            throw new BusinessRuleException("Cancelled or completed sessions cannot be edited or cancelled");
        }
        if (!LocalDateTime.of(session.getSessionDate(), session.getStartTime()).isAfter(LocalDateTime.now(clock))) {
            throw new BusinessRuleException("Sessions that have already started cannot be edited or cancelled");
        }
    }

    private void validateCapacity(Integer capacity, SportClass sportClass, long booked) {
        if (capacity == null || capacity < 1 || capacity > sportClass.getMaxMembers()) {
            throw new BusinessRuleException("Capacity must be between 1 and class maximum members ("
                    + sportClass.getMaxMembers() + ")");
        }
        if (capacity < booked) {
            throw new BusinessRuleException("Capacity cannot be below the number of confirmed bookings (" + booked + ")");
        }
    }

    private void validateTimes(LocalTime start, LocalTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessRuleException("Start time must be before end time; sessions cannot cross midnight");
        }
        if (start.getNano() != 0 || end.getNano() != 0) {
            throw new BusinessRuleException("Session times must use whole seconds");
        }
    }

    private void validateFutureDates(List<LocalDate> dates, LocalTime start) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (dates.stream().anyMatch(date -> !LocalDateTime.of(date, start).isAfter(now))) {
            throw new BusinessRuleException("Cannot schedule sessions in the past or at a start time already reached");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        requireDate(from);
        requireDate(to);
        if (from.isAfter(to)) {
            throw new BusinessRuleException("From date must not be after to date");
        }
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null || date.getYear() < 1 || date.getYear() > 9999) {
            throw new BusinessRuleException("A date between 0001-01-01 and 9999-12-31 is required");
        }
        return date;
    }

    private Map<Long, Long> bookingCounts(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return toBookingCounts(bookingRepository.countBySessionIds(ids, BookingStatus.CONFIRMED));
    }

    private Map<Long, Long> toBookingCounts(List<BookingRepository.SessionBookingCount> rows) {
        return rows.stream()
                .collect(Collectors.toMap(BookingRepository.SessionBookingCount::getSessionId,
                        BookingRepository.SessionBookingCount::getBookingCount));
    }

    private void audit(ClassSession session, UserAccount actor, String action, Map<String, Object> details) {
        auditService.record(AuditEvent.builder().actor(actor).action(action).entityType("CLASS_SESSION")
                .entityId(session.getId()).summary(action + " session " + session.getId()).details(details).build());
    }

    private static Map<String, Object> snapshot(ClassSession session) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("date", session.getSessionDate());
        values.put("startTime", session.getStartTime());
        values.put("endTime", session.getEndTime());
        values.put("classId", session.getSportClass() == null ? null : session.getSportClass().getId());
        values.put("coachId", session.getCoach() == null ? null : session.getCoach().getId());
        values.put("facilityId", session.getFacility() == null ? null : session.getFacility().getId());
        values.put("capacity", session.getCapacity());
        values.put("bookedCount", session.getBookedCount());
        values.put("trainingType", session.getTrainingType());
        values.put("status", session.getStatus());
        values.put("cancelReason", session.getCancelReason());
        values.put("cancelledAt", session.getCancelledAt());
        values.put("cancelledByUserId", session.getCancelledBy() == null ? null : session.getCancelledBy().getId());
        return values;
    }

    private static ManagerSessionResponse toResponse(ClassSession session, long booked) {
        return new ManagerSessionResponse(session.getId(), session.getSessionDate(), session.getStartTime(), session.getEndTime(),
                classSummary(session.getSportClass()), coachSummary(session.getCoach()), facilitySummary(session.getFacility()),
                session.getCapacity(), booked, session.getTrainingType(), session.getStatus(), session.getCancelReason(),
                session.getCancelledAt(), session.getCancelledBy() == null ? null : session.getCancelledBy().getId());
    }

    private static ManagerSessionResponse.ClassSummary classSummary(SportClass sportClass) {
        return sportClass == null ? null : new ManagerSessionResponse.ClassSummary(sportClass.getId(), sportClass.getCode(), sportClass.getName());
    }

    private static ManagerSessionResponse.ResourceSummary coachSummary(CoachProfile coach) {
        return coach == null ? null : new ManagerSessionResponse.ResourceSummary(coach.getId(), coach.getUserAccount().getFullName());
    }

    private static ManagerSessionResponse.ResourceSummary facilitySummary(Facility facility) {
        return facility == null ? null : new ManagerSessionResponse.ResourceSummary(facility.getId(), facility.getName());
    }

    private <T> T withLockHandling(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (PessimisticLockingFailureException ex) {
            throw new ConflictException("The schedule is being changed by another request. Reload and retry.");
        }
    }

    private record Assignment(CoachProfile coach, Facility facility) { }
}
