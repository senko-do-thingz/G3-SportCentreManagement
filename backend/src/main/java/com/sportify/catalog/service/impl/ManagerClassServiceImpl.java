package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.ManagerClassResponse;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SportClassRequest;
import com.sportify.catalog.entity.AgeGroup;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportClass;
import com.sportify.catalog.repository.AgeGroupRepository;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportClassRepository;
import com.sportify.catalog.repository.SportClassSpecifications;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.catalog.service.ManagerClassService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSportId;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.entity.UserStatus;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manager class management (F2-01, F2-02).
 * <p>
 * Rules:
 * <ul>
 *   <li>The class code is generated from seq_class_code and formatted by {@link CodeFormatter} (CL-0000).</li>
 *   <li>The sport, age group and coach must exist. They must also be active when they are newly chosen
 *       (on create, or when an edit changes them).</li>
 *   <li>The coach must teach the sport of the class (a row exists in coach_sport). This is checked on every
 *       save, so an edit can never leave a class with an unqualified coach.</li>
 *   <li>The sport of a class cannot change while the class has upcoming sessions, because every session
 *       stores its own sport_id.</li>
 *   <li>Changing the coach does not touch existing sessions; new sessions copy the coach of the class (V20).</li>
 *   <li>A class cannot be deactivated while it has confirmed bookings on upcoming sessions.</li>
 * </ul>
 * An upcoming session is a DRAFT or PUBLISHED session whose date is after today, or today with a start
 * time after the current time (Asia/Ho_Chi_Minh clock).
 */
@Service
@RequiredArgsConstructor
public class ManagerClassServiceImpl implements ManagerClassService {

    static final String ENTITY_TYPE = "SPORT_CLASS";
    static final Set<SessionStatus> UPCOMING_SESSION_STATUSES = Set.of(SessionStatus.DRAFT, SessionStatus.PUBLISHED);

    private static final String ROLE_COACH = "COACH";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int SUMMARY_MAX_LENGTH = 255;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final SportClassRepository sportClassRepository;
    private final SportRepository sportRepository;
    private final AgeGroupRepository ageGroupRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final CoachSportRepository coachSportRepository;
    private final ClassSessionRepository classSessionRepository;
    private final BookingRepository bookingRepository;
    private final CodeFormatter codeFormatter;
    private final AuditService auditService;
    private final Clock clock;

    // =====================================================================
    // Read
    // =====================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ManagerClassResponse> listClasses(Long sportId, ClassLevel level, Long coachId, Boolean active,
                                                          String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), DEFAULT_SORT);
        Page<SportClass> classes = sportClassRepository.findAll(
                SportClassSpecifications.withFilters(sportId, level, coachId, active, search), pageable);

        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Long> upcoming = countUpcomingSessions(
                classes.getContent().stream().map(SportClass::getId).toList(),
                now.toLocalDate(),
                now.toLocalTime());
        return PageResponse.from(classes.map(sportClass ->
                toResponse(sportClass, upcoming.getOrDefault(sportClass.getId(), 0L))));
    }

    @Override
    @Transactional(readOnly = true)
    public ManagerClassResponse getClass(Long id) {
        SportClass sportClass = findClass(id);
        LocalDateTime now = LocalDateTime.now(clock);
        long upcoming = countUpcomingSessions(List.of(id), now.toLocalDate(), now.toLocalTime()).getOrDefault(id, 0L);
        return toResponse(sportClass, upcoming);
    }

    // =====================================================================
    // Write
    // =====================================================================

    @Override
    @Transactional
    public ManagerClassResponse createClass(SportClassRequest request, UserAccount actor) {
        Sport sport = resolveSport(request.sportId());
        AgeGroup ageGroup = resolveAgeGroup(request.ageGroupId());
        CoachProfile coach = resolveCoach(request.coachId());
        ensureCoachTeachesSport(coach, sport);

        String code = codeFormatter.formatClassCode(sportClassRepository.getNextClassCodeSequence());

        SportClass sportClass = SportClass.builder()
                .code(code)
                .name(request.name().trim())
                .sport(sport)
                .ageGroup(ageGroup)
                .coach(coach)
                .level(request.level().name())
                .maxMembers(request.maxMembers())
                .isActive(true)
                .build();
        // saveAndFlush: a duplicate code (uq_sport_class_code) surfaces here as DataIntegrityViolationException (409).
        SportClass saved = sportClassRepository.saveAndFlush(sportClass);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.CLASS_CREATED)
                .entityType(ENTITY_TYPE)
                .entityId(saved.getId())
                .entityCode(saved.getCode())
                .summary(truncate("Class '" + saved.getName() + "' created"))
                .details(snapshot(saved))
                .build());

        return toResponse(saved, 0L);
    }

    @Override
    @Transactional
    public ManagerClassResponse updateClass(Long id, SportClassRequest request, UserAccount actor) {
        SportClass sportClass = findClass(id);
        Map<String, Object> before = snapshot(sportClass);

        boolean sportChanged = !Objects.equals(sportClass.getSport().getId(), request.sportId());
        boolean ageGroupChanged = sportClass.getAgeGroup() == null
                || !Objects.equals(sportClass.getAgeGroup().getId(), request.ageGroupId());
        boolean coachChanged = sportClass.getCoach() == null
                || !Objects.equals(sportClass.getCoach().getId(), request.coachId());

        Sport sport = sportChanged ? resolveSport(request.sportId()) : sportClass.getSport();
        AgeGroup ageGroup = ageGroupChanged ? resolveAgeGroup(request.ageGroupId()) : sportClass.getAgeGroup();
        CoachProfile coach = coachChanged ? resolveCoach(request.coachId()) : sportClass.getCoach();
        ensureCoachTeachesSport(coach, sport);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime timeNow = now.toLocalTime();
        long upcoming = countUpcomingSessions(List.of(id), today, timeNow).getOrDefault(id, 0L);

        if (sportChanged && upcoming > 0) {
            throw new BusinessRuleException("Cannot change the sport of a class that has upcoming sessions");
        }

        sportClass.setName(request.name().trim());
        sportClass.setSport(sport);
        sportClass.setAgeGroup(ageGroup);
        sportClass.setCoach(coach);
        sportClass.setLevel(request.level().name());
        sportClass.setMaxMembers(request.maxMembers());

        SportClass saved = sportClassRepository.saveAndFlush(sportClass);
        Map<String, Object> after = snapshot(saved);

        if (!before.equals(after)) {
            // Only a real change writes an activity log row.
            auditService.record(AuditEvent.builder()
                    .actor(actor)
                    .action(AuditAction.CLASS_UPDATED)
                    .entityType(ENTITY_TYPE)
                    .entityId(saved.getId())
                    .entityCode(saved.getCode())
                    .summary(truncate("Class '" + saved.getName() + "' updated"))
                    .details(Map.of("before", before, "after", after))
                    .build());
        }
        return toResponse(saved, upcoming);
    }

    @Override
    @Transactional
    public ManagerClassResponse changeStatus(Long id, boolean active, UserAccount actor) {
        SportClass sportClass = findClass(id);
        boolean current = Boolean.TRUE.equals(sportClass.getIsActive());

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        LocalTime timeNow = now.toLocalTime();
        long upcoming = countUpcomingSessions(List.of(id), today, timeNow).getOrDefault(id, 0L);

        if (current == active) {
            // Idempotent: nothing changes, nothing is logged.
            return toResponse(sportClass, upcoming);
        }

        if (active) {
            if (!Boolean.TRUE.equals(sportClass.getSport().getIsActive())) {
                throw new BusinessRuleException("Cannot activate a class whose sport is inactive");
            }
        } else {
            long bookings = bookingRepository.countUpcomingByClass(
                    id, BookingStatus.CONFIRMED, UPCOMING_SESSION_STATUSES, today, timeNow);
            if (bookings > 0) {
                throw new BusinessRuleException("Cannot deactivate a class that has " + bookings
                        + " confirmed booking(s) on upcoming sessions. Cancel or complete those sessions first.");
            }
        }

        sportClass.setIsActive(active);
        SportClass saved = sportClassRepository.saveAndFlush(sportClass);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.CLASS_STATUS_CHANGED)
                .entityType(ENTITY_TYPE)
                .entityId(saved.getId())
                .entityCode(saved.getCode())
                .summary(truncate("Class '" + saved.getName() + "' " + (active ? "activated" : "deactivated")))
                .details(Map.of("before", Map.of("active", current), "after", Map.of("active", active)))
                .build());

        return toResponse(saved, upcoming);
    }

    // =====================================================================
    // Validation helpers
    // =====================================================================

    private SportClass findClass(Long id) {
        return sportClassRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + id));
    }

    private Sport resolveSport(Long sportId) {
        Sport sport = sportRepository.findById(sportId)
                .orElseThrow(() -> new BusinessRuleException("Unknown sport id: " + sportId));
        if (!Boolean.TRUE.equals(sport.getIsActive())) {
            throw new BusinessRuleException("Sport '" + sport.getName() + "' is inactive");
        }
        return sport;
    }

    private AgeGroup resolveAgeGroup(Long ageGroupId) {
        AgeGroup ageGroup = ageGroupRepository.findById(ageGroupId)
                .orElseThrow(() -> new BusinessRuleException("Unknown age group id: " + ageGroupId));
        if (!Boolean.TRUE.equals(ageGroup.getIsActive())) {
            throw new BusinessRuleException("Age group '" + ageGroup.getLabel() + "' is inactive");
        }
        return ageGroup;
    }

    private CoachProfile resolveCoach(Long coachId) {
        CoachProfile coach = coachProfileRepository.findWithAccountById(coachId)
                .orElseThrow(() -> new BusinessRuleException("Unknown coach id: " + coachId));
        UserAccount account = coach.getUserAccount();
        if (account == null || account.getRole() == null || !ROLE_COACH.equals(account.getRole().getCode())) {
            throw new BusinessRuleException("User " + coachId + " is not a coach");
        }
        if (!UserStatus.ACTIVE.name().equals(account.getStatus())) {
            throw new BusinessRuleException("Coach '" + account.getFullName() + "' is not active");
        }
        return coach;
    }

    private void ensureCoachTeachesSport(CoachProfile coach, Sport sport) {
        if (!coachSportRepository.existsById(new CoachSportId(coach.getId(), sport.getId()))) {
            throw new BusinessRuleException("Coach '" + coachName(coach) + "' does not teach "
                    + sport.getName() + ". Choose a coach qualified for this sport.");
        }
    }

    // =====================================================================
    // Mapping helpers
    // =====================================================================

    private Map<Long, Long> countUpcomingSessions(Collection<Long> classIds, LocalDate today, LocalTime now) {
        if (classIds.isEmpty()) {
            return Map.of();
        }
        return classSessionRepository.countUpcomingByClassIds(classIds, UPCOMING_SESSION_STATUSES, today, now)
                .stream()
                .collect(Collectors.toMap(
                        ClassSessionRepository.ClassSessionCount::getClassId,
                        ClassSessionRepository.ClassSessionCount::getSessionCount));
    }

    private static ManagerClassResponse toResponse(SportClass sportClass, long upcomingSessions) {
        Sport sport = sportClass.getSport();
        AgeGroup ageGroup = sportClass.getAgeGroup();
        CoachProfile coach = sportClass.getCoach();
        return new ManagerClassResponse(
                sportClass.getId(),
                sportClass.getCode(),
                sportClass.getName(),
                sport == null ? null : sport.getId(),
                sport == null ? null : sport.getName(),
                ageGroup == null ? null : ageGroup.getId(),
                ageGroup == null ? null : ageGroup.getLabel(),
                sportClass.getLevel(),
                coach == null ? null : coach.getId(),
                coach == null ? null : coachName(coach),
                sportClass.getMaxMembers(),
                upcomingSessions,
                Boolean.TRUE.equals(sportClass.getIsActive()),
                sportClass.getCreatedAt(),
                sportClass.getUpdatedAt());
    }

    /** Values written to the activity log. A LinkedHashMap is used because some values may be null. */
    private static Map<String, Object> snapshot(SportClass sportClass) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", sportClass.getName());
        values.put("sportId", sportClass.getSport() == null ? null : sportClass.getSport().getId());
        values.put("ageGroupId", sportClass.getAgeGroup() == null ? null : sportClass.getAgeGroup().getId());
        values.put("level", sportClass.getLevel());
        values.put("maxMembers", sportClass.getMaxMembers());
        values.put("coachId", sportClass.getCoach() == null ? null : sportClass.getCoach().getId());
        return values;
    }

    private static String coachName(CoachProfile coach) {
        return coach.getUserAccount() == null ? null : coach.getUserAccount().getFullName();
    }

    private static String truncate(String value) {
        return value.length() <= SUMMARY_MAX_LENGTH ? value : value.substring(0, SUMMARY_MAX_LENGTH);
    }
}
