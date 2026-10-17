package com.sportify.catalog.controller;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.Facility;
import com.sportify.catalog.entity.FacilityType;
import com.sportify.catalog.entity.SessionStatus;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.FacilityRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.identity.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Migrated SQL Server and real JWT coverage; all fixture changes roll back in the disposable container. */
@AutoConfigureMockMvc
@Transactional
class FacilityLookupIntegrationTest extends AbstractIntegrationTest {
    private static final String URL = "/api/v1/facilities";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FacilityRepository facilities;
    @Autowired private ClassSessionRepository sessions;
    @Autowired private SportRepository sports;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private JwtService jwt;

    @BeforeEach
    void isolateDirectoryInsideRolledBackTransaction() {
        // The inherited datasource is exclusively a disposable Testcontainers SQL Server.
        // Nullable historical assignments are restored along with seed facilities by test rollback.
        jdbc.update("UPDATE class_session SET facility_id = NULL WHERE facility_id IS NOT NULL");
        jdbc.update("DELETE FROM facility");
    }

    @Test
    void manager_readsPersistedFacilitiesInStableOrderIncludingHistoricalInactiveAssignmentWithoutWrites()
            throws Exception {
        Facility last = facility("Z pool", true);
        Facility first = facility("A court", false);
        Facility second = facility("A court", true);
        ClassSession historical = sessions.saveAndFlush(ClassSession.builder()
                .sport(sports.findAll().getFirst()).facility(first).sessionDate(LocalDate.of(2020, 1, 1))
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0))
                .trainingType(TrainingFormat.SELF_TRAINING).status(SessionStatus.COMPLETED).build());
        String token = token("MANAGER");
        List<Map<String, Object>> beforeFacilities = jdbc.queryForList("SELECT * FROM facility ORDER BY id");
        List<Map<String, Object>> beforeSessions = jdbc.queryForList("SELECT * FROM class_session ORDER BY id");
        Long auditCount = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class);

        String body = mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[0].name").value("A court"))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[1].id").value(second.getId()))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[2].id").value(last.getId()))
                .andExpect(jsonPath("$[2].name").value("Z pool"))
                .andExpect(jsonPath("$[0].code").doesNotExist())
                .andExpect(jsonPath("$[0].sport").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get(URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(content().string(body));

        assertEquals(beforeFacilities, jdbc.queryForList("SELECT * FROM facility ORDER BY id"));
        assertEquals(beforeSessions, jdbc.queryForList("SELECT * FROM class_session ORDER BY id"));
        assertEquals(first.getId(), sessions.findById(historical.getId()).orElseThrow().getFacility().getId());
        assertEquals(auditCount, jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class));
    }

    @Test
    void emptyFacilityTable_returns200AndEmptyArrayWithRealManagerToken() throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token("MANAGER")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(0, facilities.count());
    }

    @Test
    void onePersistedFacility_returnsRealIdentifierAndName() throws Exception {
        Facility facility = facility("Single court", true);
        mvc.perform(get(URL).header("Authorization", "Bearer " + token("MANAGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(facility.getId()))
                .andExpect(jsonPath("$[0].name").value(facility.getName()))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MEMBER", "COACH", "RECEPTIONIST"})
    void realNonManagerToken_returns403(String role) throws Exception {
        mvc.perform(get(URL).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noToken_returns401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    private Facility facility(String name, boolean active) {
        return facilities.saveAndFlush(Facility.builder()
                .code("be10fl" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                .name(name).facilityType(FacilityType.COURT).isActive(active).build());
    }

    private String token(String role) {
        UserAccount account = users.saveAndFlush(UserAccount.builder().fullName("Facility lookup test")
                .email(UUID.randomUUID() + "@sportify.test").passwordHash("unused-test-hash")
                .status("ACTIVE").role(roles.findByCode(role).orElseThrow()).build());
        return jwt.generateToken(new CustomUserDetails(account));
    }
}
