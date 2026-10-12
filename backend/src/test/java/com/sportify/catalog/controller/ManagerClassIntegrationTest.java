package com.sportify.catalog.controller;

import com.jayway.jsonpath.JsonPath;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.AgeGroup;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.repository.AgeGroupRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test of BE-09 against a real SQL Server (Testcontainers; skipped when Docker is not available).
 * Every row created here uses an email, class name, or code containing "be09it" so cleanup never touches seeded data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ManagerClassIntegrationTest extends AbstractIntegrationTest {

    private static final String MARKER = "be09it";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private SportRepository sportRepository;
    @Autowired
    private AgeGroupRepository ageGroupRepository;
    @Autowired
    private Clock clock;

    private Long sport1Id;
    private Long sport2Id;
    private Long ageGroupId;

    @BeforeEach
    void setUp() {
        cleanUp();
        List<Sport> sports = sportRepository.findAll().stream()
                .filter(sport -> Boolean.TRUE.equals(sport.getIsActive()))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .limit(2)
                .toList();
        assertEquals(2, sports.size(), "Two active seeded sports are required");
        sport1Id = sports.get(0).getId();
        sport2Id = sports.get(1).getId();

        AgeGroup ageGroup = ageGroupRepository.findByIsActiveTrueOrderByDisplayOrderAscIdAsc().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("An active seeded age group is required"));
        ageGroupId = ageGroup.getId();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        String userIds = "SELECT id FROM user_account WHERE email LIKE '%" + MARKER + "%'";
        String classIds = "SELECT id FROM sport_class WHERE name LIKE '%" + MARKER + "%' OR code LIKE '%" + MARKER + "%'";

        jdbcTemplate.update("DELETE FROM activity_log WHERE (entity_type = 'SPORT_CLASS' AND entity_id IN (" + classIds + ")) OR actor_user_id IN (" + userIds + ")");
        jdbcTemplate.update("DELETE FROM booking WHERE session_id IN (SELECT id FROM class_session WHERE class_id IN (" + classIds + ")) OR member_id IN (" + userIds + ")");
        jdbcTemplate.update("DELETE FROM class_session WHERE class_id IN (" + classIds + ")");
        jdbcTemplate.update("DELETE FROM sport_class WHERE id IN (" + classIds + ")");
        jdbcTemplate.update("DELETE FROM coach_sport WHERE coach_id IN (" + userIds + ")");
        jdbcTemplate.update("DELETE FROM coach_profile WHERE user_id IN (" + userIds + ")");
        jdbcTemplate.update("DELETE FROM member_profile WHERE user_id IN (" + userIds + ")");
        jdbcTemplate.update("DELETE FROM user_account WHERE id IN (" + userIds + ")");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private long createCoach(String name, String email, Long sportId) {
        Role role = roleRepository.findByCode("COACH").orElseThrow();
        String randomSuffix = UUID.randomUUID().toString().replaceAll("\\D", "") + "12345678";
        String phone = "09" + randomSuffix.substring(0, 8);
        UserAccount account = userRepository.save(UserAccount.builder()
                .fullName(name)
                .email(email)
                .phone(phone)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .role(role)
                .status("ACTIVE")
                .build());
        jdbcTemplate.update("INSERT INTO coach_profile (user_id, primary_sport_id, is_public, display_order) VALUES (?, ?, 1, 0)",
                account.getId(), sportId);
        if (sportId != null) {
            jdbcTemplate.update("INSERT INTO coach_sport (coach_id, sport_id) VALUES (?, ?)",
                    account.getId(), sportId);
        }
        return account.getId();
    }

    private long createMember(String name, String email) {
        Role role = roleRepository.findByCode("MEMBER").orElseThrow();
        String randomSuffix = UUID.randomUUID().toString().replaceAll("\\D", "") + "12345678";
        String phone = "08" + randomSuffix.substring(0, 8);
        UserAccount account = userRepository.save(UserAccount.builder()
                .fullName(name)
                .email(email)
                .phone(phone)
                .passwordHash("$2a$10$abcdefghijklmnopqrstuv")
                .role(role)
                .status("ACTIVE")
                .build());
        jdbcTemplate.update("INSERT INTO member_profile (user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                account.getId(), "MEM-" + UUID.randomUUID().toString().substring(0, 6), LocalDate.now());
        return account.getId();
    }

    private String createBody(String name, Long sportId, Long coachId, String level, int maxMembers) {
        return """
                {
                  "name": "%s",
                  "sportId": %d,
                  "ageGroupId": %d,
                  "level": "%s",
                  "maxMembers": %d,
                  "coachId": %d
                }
                """.formatted(name, sportId, ageGroupId, level, maxMembers, coachId);
    }

    private long insertSession(long classId, long sportId, long coachId, LocalDate date, LocalTime start, LocalTime end) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO class_session (class_id, sport_id, coach_id, session_date, start_time, end_time, capacity, booked_count, status, created_at) "
                        + "OUTPUT INSERTED.id "
                        + "VALUES (?, ?, ?, ?, ?, ?, 20, 0, 'PUBLISHED', SYSDATETIME())",
                Long.class,
                classId, sportId, coachId, date, start, end);
    }

    private long insertBooking(long sessionId, long memberId, String status) {
        String bookingCode = "BK-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbcTemplate.queryForObject(
                "INSERT INTO booking (booking_code, session_id, member_id, status, booked_at, created_at) "
                        + "OUTPUT INSERTED.id "
                        + "VALUES (?, ?, ?, ?, SYSDATETIME(), SYSDATETIME())",
                Long.class,
                bookingCode, sessionId, memberId, status);
    }

    // ---------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void createClass_shouldGenerateClassCodeAndReturn201() throws Exception {
        long coachId = createCoach("Coach One", "coach1." + MARKER + "@sportify.test", sport1Id);
        String body = createBody("Class Alpha " + MARKER, sport1Id, coachId, "BEGINNER", 16);

        String response = mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("^CL-\\d{4}$")))
                .andExpect(jsonPath("$.name").value("Class Alpha " + MARKER))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.upcomingSessions").value(0))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) JsonPath.read(response, "$.id")).longValue();
        assertNotNull(id);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createClass_whenCoachDoesNotTeachSport_shouldReturn400() throws Exception {
        // Coach only teaches sport 1
        long coachId = createCoach("Coach One", "coach1." + MARKER + "@sportify.test", sport1Id);
        // Attempt to create class in sport 2
        String body = createBody("Class Beta " + MARKER, sport2Id, coachId, "BEGINNER", 16);

        mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not teach")));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void deactivateClass_withConfirmedFutureBooking_shouldReturn400_andSucceedWhenCancelled() throws Exception {
        long coachId = createCoach("Coach Deact", "coach.deact." + MARKER + "@sportify.test", sport1Id);
        String createJson = mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Class Guard " + MARKER, sport1Id, coachId, "BEGINNER", 16)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long classId = ((Number) JsonPath.read(createJson, "$.id")).longValue();

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate futureDate = now.toLocalDate().plusDays(3);
        long sessionId = insertSession(classId, sport1Id, coachId, futureDate, LocalTime.of(10, 0), LocalTime.of(11, 0));

        long memberId = createMember("Member Deact", "member.deact." + MARKER + "@sportify.test");
        long bookingId = insertBooking(sessionId, memberId, "CONFIRMED");

        // Attempt deactivation -> should return 400
        mockMvc.perform(patch("/api/v1/manager/classes/" + classId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("confirmed booking(s)")));

        // Cancel the booking
        jdbcTemplate.update("UPDATE booking SET status = 'CANCELLED' WHERE id = ?", bookingId);

        // Deactivation should now succeed
        mockMvc.perform(patch("/api/v1/manager/classes/" + classId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void sessionCounting_pastSessionAndTodayBeforeNowSessionAreNotCounted() throws Exception {
        long coachId = createCoach("Coach Schedule", "coach.sched." + MARKER + "@sportify.test", sport1Id);
        String createJson = mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Class Schedule " + MARKER, sport1Id, coachId, "INTERMEDIATE", 16)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long classId = ((Number) JsonPath.read(createJson, "$.id")).longValue();

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        // 1. Past day session (yesterday)
        insertSession(classId, sport1Id, coachId, today.minusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0));

        // 2. Today session with start time before now
        LocalTime pastTime = now.toLocalTime().isAfter(LocalTime.of(0, 5))
                ? now.toLocalTime().minusMinutes(5)
                : LocalTime.MIN;
        insertSession(classId, sport1Id, coachId, today, pastTime, pastTime.plusMinutes(1));

        // 3. Future day session (tomorrow) -> Upcoming #1
        insertSession(classId, sport1Id, coachId, today.plusDays(2), LocalTime.of(14, 0), LocalTime.of(15, 0));

        // 4. Today session with start time after now -> Upcoming #2
        LocalTime futureTime = now.toLocalTime().isBefore(LocalTime.of(23, 50))
                ? now.toLocalTime().plusMinutes(5)
                : LocalTime.of(23, 59);
        insertSession(classId, sport1Id, coachId, today, futureTime, futureTime.plusMinutes(1));

        // Query class: upcomingSessions should be exactly 2
        mockMvc.perform(get("/api/v1/manager/classes/" + classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcomingSessions").value(2));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void listClasses_withFiltersAndPaging() throws Exception {
        long coach1 = createCoach("Coach List1", "coach.list1." + MARKER + "@sportify.test", sport1Id);
        long coach2 = createCoach("Coach List2", "coach.list2." + MARKER + "@sportify.test", sport2Id);

        String classA = mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Alpha Filter " + MARKER, sport1Id, coach1, "BEGINNER", 12)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long idA = ((Number) JsonPath.read(classA, "$.id")).longValue();

        String classB = mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Beta Filter " + MARKER, sport2Id, coach2, "ADVANCED", 20)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long idB = ((Number) JsonPath.read(classB, "$.id")).longValue();

        // Deactivate Class B
        mockMvc.perform(patch("/api/v1/manager/classes/" + idB + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isOk());

        // 1. Filter by search=MARKER
        mockMvc.perform(get("/api/v1/manager/classes").param("search", MARKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        // 2. Filter by sportId
        mockMvc.perform(get("/api/v1/manager/classes").param("search", MARKER).param("sportId", sport1Id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));

        // 3. Filter by level
        mockMvc.perform(get("/api/v1/manager/classes").param("search", MARKER).param("level", "ADVANCED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idB));

        // 4. Filter by coachId
        mockMvc.perform(get("/api/v1/manager/classes").param("search", MARKER).param("coachId", String.valueOf(coach1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idA));

        // 5. Filter by active=false
        mockMvc.perform(get("/api/v1/manager/classes").param("search", MARKER).param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(idB));

        // 6. Pagination: page 0, size 1
        mockMvc.perform(get("/api/v1/manager/classes").param("search", MARKER).param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void nonManagerRoles_shouldReturn403() throws Exception {
        long coachId = createCoach("Coach NonMgr", "coach.nonmgr." + MARKER + "@sportify.test", sport1Id);
        String validBody = createBody("Class NonMgr " + MARKER, sport1Id, coachId, "BEGINNER", 16);

        mockMvc.perform(get("/api/v1/manager/classes"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/manager/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/manager/classes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/manager/classes/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": false}"))
                .andExpect(status().isForbidden());
    }
}
