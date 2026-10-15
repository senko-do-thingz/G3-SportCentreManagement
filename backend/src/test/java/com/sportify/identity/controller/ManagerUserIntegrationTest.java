package com.sportify.identity.controller;

import com.jayway.jsonpath.JsonPath;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check of BE-01 against a real SQL Server (Testcontainers; skipped when Docker is not available).
 * Every row created here uses an e-mail that contains "be01it" so the clean-up never touches seeded data.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ManagerUserIntegrationTest extends AbstractIntegrationTest {

    private static final String MARKER = "be01it";
    private static final String PASSWORD = "Temp1234";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MemberProfileRepository memberProfileRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private SportRepository sportRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private List<Long> sportIds;

    @BeforeEach
    void setUp() {
        cleanUp();
        sportIds = sportRepository.findAll().stream()
                .filter(sport -> Boolean.TRUE.equals(sport.getIsActive()))
                .map(Sport::getId)
                .sorted()
                .limit(2)
                .toList();
        assertEquals(2, sportIds.size(), "The seeded sports are required");
    }

    @AfterEach
    void cleanUp() {
        String ids = "SELECT id FROM user_account WHERE email LIKE '%" + MARKER + "%'";
        jdbcTemplate.update("DELETE FROM activity_log WHERE entity_type = 'USER_ACCOUNT' AND entity_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM coach_sport WHERE coach_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM coach_profile WHERE user_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM member_profile WHERE user_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM user_account WHERE email LIKE '%" + MARKER + "%'");
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private String createBody(String name, String email, String phone, String role, List<Long> sports) {
        String sportsJson = sports == null ? "" : ", \"sportIds\": " + sports;
        return """
                {"fullName": "%s", "email": "%s", "phone": "%s", "temporaryPassword": "%s", "role": "%s"%s}
                """.formatted(name, email, phone, PASSWORD, role, sportsJson);
    }

    private long createStaff(String name, String email, String phone, String role, List<Long> sports) throws Exception {
        String json = mockMvc.perform(post("/api/v1/manager/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(name, email, phone, role, sports)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void createMember(String name, String email, String phone, String memberCode, String status) {
        Role memberRole = roleRepository.findByCode("MEMBER").orElseThrow();
        transactionTemplate.execute(tx -> {
            UserAccount user = userRepository.save(UserAccount.builder()
                    .email(email).fullName(name).phone(phone).passwordHash("hash")
                    .role(memberRole).status(status).build());
            memberProfileRepository.save(MemberProfile.builder()
                    .userAccount(user).memberCode(memberCode).currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now()).build());
            return null;
        });
    }

    private int countLogs(String action, long entityId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_log WHERE action = ? AND entity_type = 'USER_ACCOUNT' AND entity_id = ?",
                Integer.class, action, entityId);
        return count == null ? 0 : count;
    }

    private String loginBody(String email) {
        return "{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}";
    }

    // ---------------------------------------------------------------------
    // create
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void createCoach_shouldCreateCoachProfileAndCoachSportRowsAndActivityLog() throws Exception {
        long id = createStaff("Daniel Smith", "daniel." + MARKER + "@sportify.test", "0911000001", "COACH", sportIds);

        Long primarySport = jdbcTemplate.queryForObject(
                "SELECT primary_sport_id FROM coach_profile WHERE user_id = ?", Long.class, id);
        assertEquals(sportIds.get(0), primarySport);

        List<Long> linked = jdbcTemplate.queryForList(
                "SELECT sport_id FROM coach_sport WHERE coach_id = ? ORDER BY sport_id", Long.class, id);
        assertEquals(sportIds, linked);

        assertEquals(1, countLogs("USER_CREATED", id));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE id = ?", String.class, id);
        assertNotEquals(PASSWORD, storedHash);
        assertTrue(storedHash.startsWith("$2"), "The password must be stored as a BCrypt hash");
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createReceptionist_shouldNotCreateCoachRows() throws Exception {
        long id = createStaff("Jamie Tran", "jamie." + MARKER + "@sportify.test", "0911000002", "RECEPTIONIST", null);

        Integer profiles = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coach_profile WHERE user_id = ?", Integer.class, id);
        assertEquals(0, profiles);
        assertEquals(1, countLogs("USER_CREATED", id));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createStaff_withDuplicateEmail_shouldReturn409() throws Exception {
        createStaff("Jamie Tran", "dup." + MARKER + "@sportify.test", "0911000003", "RECEPTIONIST", null);

        mockMvc.perform(post("/api/v1/manager/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("Other Person", "DUP." + MARKER + "@sportify.test", "0911000004", "RECEPTIONIST", null)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createStaff_withWeakPassword_shouldReturn400WithFieldError() throws Exception {
        String body = createBody("Weak Pass", "weak." + MARKER + "@sportify.test", "0911000005", "RECEPTIONIST", null)
                .replace(PASSWORD, "weak");

        mockMvc.perform(post("/api/v1/manager/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.temporaryPassword").exists());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createStaff_withMemberRole_shouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/manager/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("A Member", "member." + MARKER + "@sportify.test", "0911000006", "MEMBER", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "RECEPTIONIST")
    void createStaff_asReceptionist_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/v1/manager/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("No Way", "no." + MARKER + "@sportify.test", "0911000007", "RECEPTIONIST", null)))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------
    // status and login
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void deactivatedStaff_cannotLogIn_andCanLogInAgainAfterReactivation() throws Exception {
        String email = "login." + MARKER + "@sportify.test";
        long id = createStaff("Login Test", email, "0911000008", "RECEPTIONIST", null);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        mockMvc.perform(patch("/api/v1/manager/users/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
        assertEquals(1, countLogs("USER_STATUS_CHANGED", id));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/manager/users/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk());
        assertEquals(2, countLogs("USER_STATUS_CHANGED", id));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody(email)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void changeStatus_toSameValue_shouldNotWriteAnotherLogRow() throws Exception {
        long id = createStaff("Same Status", "same." + MARKER + "@sportify.test", "0911000009", "RECEPTIONIST", null);

        mockMvc.perform(patch("/api/v1/manager/users/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"ACTIVE\"}"))
                .andExpect(status().isOk());

        assertEquals(0, countLogs("USER_STATUS_CHANGED", id));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void changeStatus_unknownUser_shouldReturn404() throws Exception {
        mockMvc.perform(patch("/api/v1/manager/users/999999999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------------
    // list, filter, search, paging
    // ---------------------------------------------------------------------

    @Test
    @WithMockUser(roles = "MANAGER")
    void list_shouldFilterByRoleStatusAndSearchAndPage() throws Exception {
        createStaff("Daniel Smith", "daniel." + MARKER + "@sportify.test", "0911000011", "COACH", sportIds);
        createStaff("Chris Nguyen", "chris." + MARKER + "@sportify.test", "0911000012", "COACH", List.of(sportIds.get(0)));
        long jamie = createStaff("Jamie Tran", "jamie." + MARKER + "@sportify.test", "0911000013", "RECEPTIONIST", null);
        createMember("Taylor Nguyen", "taylor." + MARKER + "@sportify.test", "0911000014", "MEM-IT01", "ACTIVE");
        createMember("Linh Pham", "linh." + MARKER + "@sportify.test", "0911000015", "MEM-IT02", "INACTIVE");

        mockMvc.perform(patch("/api/v1/manager/users/" + jamie + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"INACTIVE\"}"))
                .andExpect(status().isOk());

        // All test rows (the marker keeps seeded accounts out of the result)
        mockMvc.perform(get("/api/v1/manager/users").param("search", MARKER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));

        // Role filter
        mockMvc.perform(get("/api/v1/manager/users").param("search", MARKER).param("role", "COACH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].role").value("COACH"));
        mockMvc.perform(get("/api/v1/manager/users").param("search", MARKER).param("role", "MEMBER"))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Status filter
        mockMvc.perform(get("/api/v1/manager/users").param("search", MARKER).param("status", "INACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(2));

        // Role and status together
        mockMvc.perform(get("/api/v1/manager/users")
                        .param("search", MARKER).param("role", "MEMBER").param("status", "INACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Linh Pham"));

        // Search by name, by e-mail, by phone and by member code (case-insensitive)
        mockMvc.perform(get("/api/v1/manager/users").param("search", "DANIEL"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/manager/users").param("search", "chris." + MARKER))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/manager/users").param("search", "0911000013"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].role").value("RECEPTIONIST"));
        mockMvc.perform(get("/api/v1/manager/users").param("search", "mem-it02"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Linh Pham"));

        // A search with a LIKE wildcard is matched literally
        mockMvc.perform(get("/api/v1/manager/users").param("search", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));

        // Paging: 5 rows, 2 per page
        mockMvc.perform(get("/api/v1/manager/users").param("search", MARKER).param("size", "2").param("page", "0"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.totalElements").value(5));
        mockMvc.perform(get("/api/v1/manager/users").param("search", MARKER).param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void list_asMember_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/v1/manager/users")).andExpect(status().isForbidden());
    }
}
