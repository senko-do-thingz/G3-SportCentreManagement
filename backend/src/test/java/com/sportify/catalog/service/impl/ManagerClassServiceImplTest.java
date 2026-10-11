package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.ManagerClassResponse;
import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.dto.SportClassRequest;
import com.sportify.catalog.entity.AgeGroup;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.ClassLevel;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportClass;
import com.sportify.catalog.repository.AgeGroupRepository;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.SportClassRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.CoachProfile;
import com.sportify.identity.entity.CoachSportId;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.CoachProfileRepository;
import com.sportify.identity.repository.CoachSportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagerClassServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 2026-10-11 10:00 in Asia/Ho_Chi_Minh. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T03:00:00Z"), ZONE);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);
    private static final LocalTime NOW = LocalTime.of(10, 0);

    private static final long FOOTBALL_ID = 1L;
    private static final long BADMINTON_ID = 2L;
    private static final long AGE_16_ID = 4L;
    private static final long COACH_ID = 20L;
    private static final long OTHER_COACH_ID = 21L;
    private static final long CLASS_ID = 100L;

    @Mock
    private SportClassRepository sportClassRepository;
    @Mock
    private SportRepository sportRepository;
    @Mock
    private AgeGroupRepository ageGroupRepository;
    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private CoachSportRepository coachSportRepository;
    @Mock
    private ClassSessionRepository classSessionRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private AuditService auditService;

    private ManagerClassServiceImpl service;
    private UserAccount actor;

    private Sport football;
    private Sport badminton;
    private AgeGroup ageGroup;
    private CoachProfile coach;

    @BeforeEach
    void setUp() {
        service = new ManagerClassServiceImpl(sportClassRepository, sportRepository, ageGroupRepository,
                coachProfileRepository, coachSportRepository, classSessionRepository, bookingRepository,
                new CodeFormatter(), auditService, CLOCK);

        actor = UserAccount.builder().id(1L).email("manager@sportify.com").role(role("MANAGER")).status("ACTIVE").build();
        football = Sport.builder().id(FOOTBALL_ID).code("FOOTBALL").name("Football").isActive(true).build();
        badminton = Sport.builder().id(BADMINTON_ID).code("BADMINTON").name("Badminton").isActive(true).build();
        ageGroup = AgeGroup.builder().id(AGE_16_ID).code("AGES_16_PLUS").label("Ages 16+").minAge(16).isActive(true).build();
        coach = coach(COACH_ID, "Daniel Smith", "COACH", "ACTIVE");
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private static Role role(String code) {
        return Role.builder().code(code).build();
    }

    private static CoachProfile coach(long id, String name, String roleCode, String status) {
        UserAccount account = UserAccount.builder()
                .id(id).fullName(name).email(name.replace(' ', '.') + "@sportify.com")
                .role(role(roleCode)).status(status).build();
        return CoachProfile.builder().id(id).userAccount(account).build();
    }

    private static SportClassRequest request(long sportId, long coachId) {
        return new SportClassRequest("  Football Fundamentals  ", sportId, AGE_16_ID, ClassLevel.BEGINNER, 16, coachId);
    }

    private SportClass existingClass() {
        return SportClass.builder()
                .id(CLASS_ID).code("CL-1000").name("Football Fundamentals")
                .sport(football).ageGroup(ageGroup).coach(coach)
                .level("BEGINNER").maxMembers(16).isActive(true)
                .build();
    }

    private void stubValidReferences() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.of(ageGroup));
        when(coachProfileRepository.findWithAccountById(COACH_ID)).thenReturn(Optional.of(coach));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, FOOTBALL_ID))).thenReturn(true);
    }

    private void stubUpcomingSessions(long classId, long count) {
        ClassSessionRepository.ClassSessionCount row = new ClassSessionRepository.ClassSessionCount() {
            @Override
            public Long getClassId() {
                return classId;
            }

            @Override
            public Long getSessionCount() {
                return count;
            }
        };
        when(classSessionRepository.countUpcomingByClassIds(anyCollection(), anyCollection(), eq(TODAY), eq(NOW)))
                .thenReturn(count == 0 ? List.of() : List.of(row));
    }

    // ---------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------

    @Test
    void createClass_withQualifiedCoach_shouldGenerateCodeSaveAndAudit() {
        stubValidReferences();
        when(sportClassRepository.getNextClassCodeSequence()).thenReturn(1000L);
        when(sportClassRepository.saveAndFlush(any(SportClass.class))).thenAnswer(inv -> {
            SportClass saved = inv.getArgument(0);
            saved.setId(CLASS_ID);
            return saved;
        });

        ManagerClassResponse response = service.createClass(request(FOOTBALL_ID, COACH_ID), actor);

        assertEquals(CLASS_ID, response.id());
        assertEquals("CL-1000", response.code());
        assertEquals("Football Fundamentals", response.name());
        assertEquals("Football", response.sportName());
        assertEquals("Ages 16+", response.ageGroupLabel());
        assertEquals("BEGINNER", response.level());
        assertEquals(COACH_ID, response.coachId());
        assertEquals("Daniel Smith", response.coachName());
        assertEquals(16, response.maxMembers());
        assertEquals(0L, response.upcomingSessions());
        assertTrue(response.active());

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals(AuditAction.CLASS_CREATED, event.getValue().getAction());
        assertEquals("SPORT_CLASS", event.getValue().getEntityType());
        assertEquals("CL-1000", event.getValue().getEntityCode());
        assertEquals(actor, event.getValue().getActor());
    }

    @Test
    void createClass_withCoachWhoDoesNotTeachSport_shouldThrowBusinessRule() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.of(ageGroup));
        when(coachProfileRepository.findWithAccountById(COACH_ID)).thenReturn(Optional.of(coach));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, FOOTBALL_ID))).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createClass(request(FOOTBALL_ID, COACH_ID), actor));

        assertTrue(ex.getMessage().contains("does not teach Football"));
        verify(sportClassRepository, never()).getNextClassCodeSequence();
        verify(sportClassRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void createClass_withUnknownCoach_shouldThrowBusinessRule() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.of(ageGroup));
        when(coachProfileRepository.findWithAccountById(99L)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> service.createClass(request(FOOTBALL_ID, 99L), actor));
        verify(sportClassRepository, never()).saveAndFlush(any());
    }

    @Test
    void createClass_withInactiveCoach_shouldThrowBusinessRule() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.of(ageGroup));
        when(coachProfileRepository.findWithAccountById(OTHER_COACH_ID))
                .thenReturn(Optional.of(coach(OTHER_COACH_ID, "Old Coach", "COACH", "INACTIVE")));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.createClass(request(FOOTBALL_ID, OTHER_COACH_ID), actor));
        assertTrue(ex.getMessage().contains("not active"));
    }

    @Test
    void createClass_withProfileWhoseAccountIsNotACoach_shouldThrowBusinessRule() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.of(ageGroup));
        when(coachProfileRepository.findWithAccountById(OTHER_COACH_ID))
                .thenReturn(Optional.of(coach(OTHER_COACH_ID, "Front Desk", "RECEPTIONIST", "ACTIVE")));

        assertThrows(BusinessRuleException.class, () -> service.createClass(request(FOOTBALL_ID, OTHER_COACH_ID), actor));
    }

    @Test
    void createClass_withInactiveSport_shouldThrowBusinessRule() {
        football.setIsActive(false);
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));

        assertThrows(BusinessRuleException.class, () -> service.createClass(request(FOOTBALL_ID, COACH_ID), actor));
        verifyNoInteractions(coachProfileRepository);
    }

    @Test
    void createClass_withUnknownSport_shouldThrowBusinessRule() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> service.createClass(request(FOOTBALL_ID, COACH_ID), actor));
    }

    @Test
    void createClass_withUnknownOrInactiveAgeGroup_shouldThrowBusinessRule() {
        when(sportRepository.findById(FOOTBALL_ID)).thenReturn(Optional.of(football));
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> service.createClass(request(FOOTBALL_ID, COACH_ID), actor));

        ageGroup.setIsActive(false);
        when(ageGroupRepository.findById(AGE_16_ID)).thenReturn(Optional.of(ageGroup));
        assertThrows(BusinessRuleException.class, () -> service.createClass(request(FOOTBALL_ID, COACH_ID), actor));
    }

    // ---------------------------------------------------------------------
    // read
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void listClasses_shouldMapRowsWithUpcomingSessionCount() {
        SportClass first = existingClass();
        SportClass second = existingClass();
        second.setId(101L);
        second.setCode("CL-1001");
        second.setCoach(null);
        second.setAgeGroup(null);
        when(sportClassRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), PageRequest.of(0, 20), 2));
        stubUpcomingSessions(CLASS_ID, 3);

        PageResponse<ManagerClassResponse> page = service.listClasses(null, null, null, null, null, 0, 20);

        assertEquals(2, page.totalElements());
        assertEquals(3L, page.content().get(0).upcomingSessions());
        assertEquals(0L, page.content().get(1).upcomingSessions());
        assertNull(page.content().get(1).coachName());
        assertNull(page.content().get(1).ageGroupLabel());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listClasses_shouldClampPageSize() {
        when(sportClassRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listClasses(null, ClassLevel.ADVANCED, null, true, "x", -3, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(sportClassRepository).findAll(any(Specification.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        verifyNoInteractions(classSessionRepository);
    }

    @Test
    void getClass_unknownId_shouldThrowNotFound() {
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getClass(CLASS_ID));
    }

    @Test
    void getClass_success_shouldReturnClassWithUpcomingCount() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        stubUpcomingSessions(CLASS_ID, 2);

        ManagerClassResponse response = service.getClass(CLASS_ID);

        assertEquals(CLASS_ID, response.id());
        assertEquals(2L, response.upcomingSessions());
    }

    // ---------------------------------------------------------------------
    // update
    // ---------------------------------------------------------------------

    @Test
    void updateClass_changingNameAndMaxMembers_shouldSaveAndAuditBeforeAndAfter() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, FOOTBALL_ID))).thenReturn(true);
        when(sportClassRepository.saveAndFlush(sportClass)).thenReturn(sportClass);
        stubUpcomingSessions(CLASS_ID, 2);

        SportClassRequest edit = new SportClassRequest("Football Skills", FOOTBALL_ID, AGE_16_ID,
                ClassLevel.INTERMEDIATE, 20, COACH_ID);
        ManagerClassResponse response = service.updateClass(CLASS_ID, edit, actor);

        assertEquals("Football Skills", response.name());
        assertEquals("INTERMEDIATE", response.level());
        assertEquals(20, response.maxMembers());
        assertEquals(2L, response.upcomingSessions());
        assertEquals("CL-1000", response.code(), "The code never changes on edit");

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals(AuditAction.CLASS_UPDATED, event.getValue().getAction());
        Map<String, Object> before = (Map<String, Object>) event.getValue().getDetails().get("before");
        Map<String, Object> after = (Map<String, Object>) event.getValue().getDetails().get("after");
        assertEquals("Football Fundamentals", before.get("name"));
        assertEquals("Football Skills", after.get("name"));
        // Unchanged references are not reloaded.
        verifyNoInteractions(sportRepository, ageGroupRepository, coachProfileRepository);
    }

    @Test
    void updateClass_withoutAnyChange_shouldNotWriteAuditRow() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, FOOTBALL_ID))).thenReturn(true);
        when(sportClassRepository.saveAndFlush(sportClass)).thenReturn(sportClass);
        stubUpcomingSessions(CLASS_ID, 0);

        service.updateClass(CLASS_ID, new SportClassRequest("Football Fundamentals", FOOTBALL_ID, AGE_16_ID,
                ClassLevel.BEGINNER, 16, COACH_ID), actor);

        verifyNoInteractions(auditService);
    }

    @Test
    void updateClass_toCoachWhoDoesNotTeachSport_shouldThrowBusinessRule() {
        SportClass sportClass = existingClass();
        CoachProfile swimmingCoach = coach(OTHER_COACH_ID, "Emma Wilson", "COACH", "ACTIVE");
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(coachProfileRepository.findWithAccountById(OTHER_COACH_ID)).thenReturn(Optional.of(swimmingCoach));
        when(coachSportRepository.existsById(new CoachSportId(OTHER_COACH_ID, FOOTBALL_ID))).thenReturn(false);

        assertThrows(BusinessRuleException.class,
                () -> service.updateClass(CLASS_ID, request(FOOTBALL_ID, OTHER_COACH_ID), actor));
        verify(sportClassRepository, never()).saveAndFlush(any());
        assertEquals(COACH_ID, sportClass.getCoach().getId(), "The entity must not be modified on failure");
    }

    @Test
    void updateClass_whenCoachUnchangedButNoLongerQualified_shouldThrowBusinessRule() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, FOOTBALL_ID))).thenReturn(false);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateClass(CLASS_ID, request(FOOTBALL_ID, COACH_ID), actor));

        assertTrue(ex.getMessage().contains("does not teach Football"));
        verify(sportClassRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateClass_changingSportWhileUpcomingSessionsExist_shouldThrowBusinessRule() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(sportRepository.findById(BADMINTON_ID)).thenReturn(Optional.of(badminton));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, BADMINTON_ID))).thenReturn(true);
        stubUpcomingSessions(CLASS_ID, 1);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.updateClass(CLASS_ID, request(BADMINTON_ID, COACH_ID), actor));
        assertTrue(ex.getMessage().contains("upcoming sessions"));
        verify(sportClassRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateClass_changingSportWithoutUpcomingSessions_shouldSucceed() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(sportRepository.findById(BADMINTON_ID)).thenReturn(Optional.of(badminton));
        when(coachSportRepository.existsById(new CoachSportId(COACH_ID, BADMINTON_ID))).thenReturn(true);
        when(sportClassRepository.saveAndFlush(sportClass)).thenReturn(sportClass);
        stubUpcomingSessions(CLASS_ID, 0);

        ManagerClassResponse response = service.updateClass(CLASS_ID, request(BADMINTON_ID, COACH_ID), actor);

        assertEquals(BADMINTON_ID, response.sportId());
    }

    @Test
    void updateClass_unknownId_shouldThrowNotFound() {
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateClass(CLASS_ID, request(FOOTBALL_ID, COACH_ID), actor));
    }

    // ---------------------------------------------------------------------
    // status
    // ---------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void changeStatus_deactivateWithConfirmedUpcomingBookings_shouldThrowBusinessRule() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(bookingRepository.countUpcomingByClass(eq(CLASS_ID), eq(BookingStatus.CONFIRMED),
                any(Collection.class), eq(TODAY), eq(NOW))).thenReturn(4L);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(CLASS_ID, false, actor));

        assertTrue(ex.getMessage().contains("4 confirmed booking(s)"));
        assertTrue(sportClass.getIsActive());
        verify(sportClassRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changeStatus_deactivateWithoutBookings_shouldSaveAndAudit() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(bookingRepository.countUpcomingByClass(eq(CLASS_ID), eq(BookingStatus.CONFIRMED),
                any(Collection.class), eq(TODAY), eq(NOW))).thenReturn(0L);
        when(sportClassRepository.saveAndFlush(sportClass)).thenReturn(sportClass);
        stubUpcomingSessions(CLASS_ID, 0);

        ManagerClassResponse response = service.changeStatus(CLASS_ID, false, actor);

        assertFalse(response.active());
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertEquals(AuditAction.CLASS_STATUS_CHANGED, event.getValue().getAction());
    }

    @Test
    void changeStatus_toSameValue_shouldBeIdempotent() {
        SportClass sportClass = existingClass();
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        stubUpcomingSessions(CLASS_ID, 0);

        ManagerClassResponse response = service.changeStatus(CLASS_ID, true, actor);

        assertTrue(response.active());
        verify(sportClassRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditService, bookingRepository);
    }

    @Test
    void changeStatus_activateWhenSportInactive_shouldThrowBusinessRule() {
        SportClass sportClass = existingClass();
        sportClass.setIsActive(false);
        football.setIsActive(false);
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(CLASS_ID, true, actor));
        verify(sportClassRepository, never()).saveAndFlush(any());
    }

    @Test
    void changeStatus_activateInactiveClass_shouldSucceedWithoutBookingCheck() {
        SportClass sportClass = existingClass();
        sportClass.setIsActive(false);
        when(sportClassRepository.findWithDetailsById(CLASS_ID)).thenReturn(Optional.of(sportClass));
        when(sportClassRepository.saveAndFlush(sportClass)).thenReturn(sportClass);
        stubUpcomingSessions(CLASS_ID, 0);

        ManagerClassResponse response = service.changeStatus(CLASS_ID, true, actor);

        assertTrue(response.active());
        verifyNoInteractions(bookingRepository);
    }
}
