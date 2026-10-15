package com.sportify.identity.service.impl;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.service.FrontDeskMemberService;
import com.sportify.testsupport.FrontDeskFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SQL Server coverage; skipped locally when Docker is unavailable. */
@Transactional
@AutoConfigureMockMvc
class MemberCreationIntegrationTest extends AbstractIntegrationTest {
    @Autowired private FrontDeskMemberService members;
    @Autowired private PasswordEncoder encoder;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock clock;
    private FrontDeskFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new FrontDeskFixtures(jdbc, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "phone"})
    void create_whenExistingIdentityIsUsed_returns409(String field) throws Exception {
        String email = field.equals("email") ? fixtures.marker.toUpperCase(Locale.ROOT) + "@EXAMPLE.TEST" : fixtures.marker + "new@example.test";
        String requestPhone = field.equals("phone") ? fixtures.phone : fixtures.randomPhone();
        mvc.perform(post("/api/v1/members").with(user("desk").roles("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"fullName":"New Member","phone":" %s ","email":" %s "}
                                """.formatted(requestPhone, email)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @ParameterizedTest
    @ValueSource(strings = {"email", "phone"})
    void uniqueConstraints_whenPreCheckIsBypassed_rejectDuplicateIdentity(String field) {
        String email = field.equals("email") ? fixtures.marker + "@example.test" : fixtures.marker + "race@example.test";
        String duplicatePhone = field.equals("phone") ? fixtures.phone : fixtures.randomPhone();
        assertThrows(DataIntegrityViolationException.class,
                () -> fixtures.account(email, duplicatePhone, "Concurrent identity"));
    }

    @Test
    void create_whenSuccessful_persistsOnlyHashAndGeneratedMemberCode() {
        String newPhone = fixtures.randomPhone();
        var response = members.create(new MemberCreateRequest(" New Member ", newPhone, fixtures.marker + "new@example.test"));
        assertTrue(response.memberCode().matches("MEM-\\d{4,}"));
        String stored = jdbc.queryForObject("SELECT password_hash FROM user_account WHERE id = ?", String.class, response.userId());
        assertNotEquals(response.temporaryPassword(), stored);
        assertTrue(encoder.matches(response.temporaryPassword(), stored));
        assertEquals("New Member", jdbc.queryForObject("SELECT full_name FROM user_account WHERE id = ?", String.class, response.userId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void create_whenConcurrentRequestsUseDifferentIdentities_generatesDistinctSequenceCodes() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> members.create(new MemberCreateRequest("Concurrent First",
                    fixtures.randomPhone(), fixtures.marker + "first@example.test")));
            var second = executor.submit(() -> members.create(new MemberCreateRequest("Concurrent Second",
                    fixtures.randomPhone(), fixtures.marker + "second@example.test")));
            assertNotEquals(first.get(30, TimeUnit.SECONDS).memberCode(), second.get(30, TimeUnit.SECONDS).memberCode());
        } finally {
            executor.shutdown();
            try {
                assertTrue(executor.awaitTermination(40, TimeUnit.SECONDS));
            } finally {
                executor.shutdownNow();
                fixtures.deleteCreatedMembers();
            }
        }
    }
}
