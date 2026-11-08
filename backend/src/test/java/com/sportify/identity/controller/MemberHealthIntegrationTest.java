package com.sportify.identity.controller;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.support.SessionIdentityFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MW-02 questionnaire on real SQL Server: advisory outcome, validation, consent and the Coach summary scope. */
@AutoConfigureMockMvc
class MemberHealthIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();
    static final String ALL_NO = "{\"answers\":{\"injuryOrMobility\":\"NO\",\"activityRestriction\":\"NO\","
            + "\"exertionSymptoms\":\"NO\",\"coachShouldKnow\":\"NO\",\"wantsConsultation\":\"NO\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;

    private final String marker = "mha" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember, coach, otherCoach, receptionist, classId;

    @BeforeEach
    void fixture() {
        member = member();
        otherMember = member();
        coach = coach();
        otherCoach = coach();
        receptionist = account("RECEPTIONIST");
        long sport = jdbc.queryForObject("INSERT INTO sport(code, name, venue_type, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'INDOOR', 1)",
                Long.class, code(), "Sport " + marker);
        long facility = jdbc.queryForObject("INSERT INTO facility(code, name, facility_type, sport_id, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'COURT', ?, 1)",
                Long.class, code(), "Court " + marker, sport);
        classId = jdbc.queryForObject("INSERT INTO sport_class(code, name, sport_id, level, max_members, is_active, coach_id) OUTPUT INSERTED.id VALUES (?, ?, ?, 'BEGINNER', 20, 1, ?)",
                Long.class, code(), "Class " + marker, sport, coach);
        LocalDate day = LocalDate.now().plusDays(3);
        long session = jdbc.queryForObject("""
                SET NOCOUNT ON; DECLARE @created TABLE(id BIGINT);
                INSERT INTO class_session(class_id, sport_id, facility_id, coach_id, training_type, session_date, start_time, end_time,
                    capacity, booked_count, status, session_code) OUTPUT INSERTED.id INTO @created
                VALUES (?, ?, ?, ?, 'COACH_LED', ?, ?, ?, 10, 1, 'PUBLISHED', ?); SELECT id FROM @created;""", Long.class,
                classId, sport, facility, coach, day, LocalTime.of(9, 0), LocalTime.of(10, 0),
                SessionIdentityFixtures.reserve(jdbc, day, "FOOTBALL"));
        jdbc.update("INSERT INTO booking(booking_code, session_id, member_id, status, booked_at) VALUES (?, ?, ?, 'CONFIRMED', ?)",
                code(), session, member, LocalDateTime.now());
    }

    @AfterEach
    void cleanup() {
        String accounts = "SELECT id FROM user_account WHERE email LIKE ?";
        String like = marker + "%";
        jdbc.update("DELETE FROM member_health_assessment WHERE member_id IN (" + accounts + ")", like);
        jdbc.update("DELETE FROM booking WHERE member_id IN (" + accounts + ")", like);
        jdbc.update("DELETE FROM class_session WHERE class_id IN (SELECT id FROM sport_class WHERE code LIKE ?)", like);
        jdbc.update("DELETE FROM sport_class WHERE code LIKE ?", like);
        jdbc.update("DELETE FROM facility WHERE code LIKE ?", like);
        jdbc.update("DELETE FROM sport WHERE code LIKE ?", like);
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + accounts + ")", like);
        jdbc.update("DELETE FROM coach_profile WHERE user_id IN (" + accounts + ")", like);
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", like);
    }

    @Test
    void skippedQuestionnaireIsIncompleteAndAnswersPersistWithAdvisoryOutcomes() throws Exception {
        mvc.perform(get("/api/v1/member/health-assessment").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.outcome").value("INCOMPLETE"))
                .andExpect(jsonPath("$.questions.length()").value(5))
                .andExpect(jsonPath("$.version").value("HQ-2026-11"));
        save(member, "{\"answers\":{\"injuryOrMobility\":\"NO\",\"exertionSymptoms\":\"PREFER_NOT_TO_ANSWER\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("INCOMPLETE"));
        save(member, ALL_NO + ",\"shareWithCoach\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("NO_REPORTED_CONCERNS"));
        save(member, "{\"answers\":{\"injuryOrMobility\":\"YES\",\"activityRestriction\":\"NO\",\"exertionSymptoms\":\"NO\","
                + "\"coachShouldKnow\":\"NO\",\"wantsConsultation\":\"YES\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("COACH_DISCUSSION_RECOMMENDED"));
        mvc.perform(get("/api/v1/member/health-assessment").with(user(details(member))))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.answers.injuryOrMobility").value("YES"))
                .andExpect(jsonPath("$.disclaimer").value(org.hamcrest.Matchers.containsString("not a medical assessment")));
        assertThat(count("SELECT COUNT(*) FROM member_health_assessment WHERE member_id=?", member)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM member_health_assessment WHERE member_id=?", otherMember)).isZero();
    }

    @Test
    void invalidAnswersAndUnknownQuestionsAreRejectedWithoutChangingStoredAnswers() throws Exception {
        save(member, ALL_NO + "}").andExpect(status().isOk());
        save(member, "{\"answers\":{\"injuryOrMobility\":\"MAYBE\"}}").andExpect(status().isBadRequest());
        save(member, "{\"answers\":{\"bloodPressure\":\"NO\"}}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT injury_or_mobility FROM member_health_assessment WHERE member_id=?", String.class, member))
                .isEqualTo("NO");
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECEPTIONIST", "COACH"})
    void staffCannotReadOrWriteAMembersQuestionnaire(String role) throws Exception {
        long staff = "COACH".equals(role) ? coach : receptionist;
        mvc.perform(get("/api/v1/member/health-assessment").with(user(details(staff)))).andExpect(status().isForbidden());
        save(staff, ALL_NO + "}").andExpect(status().isForbidden());
    }

    @Test
    void assignedCoachSeesOnlyAConsentedOutcomeSummaryAndUnrelatedCoachIsDenied() throws Exception {
        save(member, "{\"answers\":{\"injuryOrMobility\":\"YES\",\"wantsConsultation\":\"YES\"},\"shareWithCoach\":false}")
                .andExpect(status().isOk());
        String path = "/api/v1/coach/training/classes/" + classId + "/members/" + member + "/health-summary";
        mvc.perform(get(path).with(user(details(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shared").value(false))
                .andExpect(jsonPath("$.outcome").doesNotExist())
                .andExpect(jsonPath("$.answers").doesNotExist());
        save(member, "{\"answers\":{\"injuryOrMobility\":\"YES\",\"wantsConsultation\":\"YES\"},\"shareWithCoach\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.shareWithCoach").value(true));
        mvc.perform(get(path).with(user(details(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shared").value(true))
                .andExpect(jsonPath("$.outcome").value("COACH_DISCUSSION_RECOMMENDED"))
                .andExpect(jsonPath("$.consultationRequested").value(true))
                .andExpect(jsonPath("$.answers").doesNotExist())
                .andExpect(jsonPath("$.injuryOrMobility").doesNotExist());
        mvc.perform(get(path).with(user(details(otherCoach)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/coach/training/classes/" + classId + "/members/" + otherMember + "/health-summary")
                .with(user(details(coach)))).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user(details(receptionist)))).andExpect(status().isForbidden());
        mvc.perform(get(path).with(user(details(member)))).andExpect(status().isForbidden());
    }

    @Test
    void clearingRemovesAnswersAndWithdrawsCoachConsent() throws Exception {
        save(member, ALL_NO + ",\"shareWithCoach\":true}").andExpect(status().isOk());
        mvc.perform(delete("/api/v1/member/health-assessment").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareWithCoach").value(false))
                .andExpect(jsonPath("$.outcome").value("INCOMPLETE"))
                .andExpect(jsonPath("$.answers.injuryOrMobility").doesNotExist());
        mvc.perform(get("/api/v1/coach/training/classes/" + classId + "/members/" + member + "/health-summary")
                .with(user(details(coach)))).andExpect(jsonPath("$.shared").value(false));
        assertThat(count("SELECT COUNT(*) FROM member_health_assessment WHERE member_id=? AND consent_updated_at IS NOT NULL", member))
                .isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions save(long id, String body) throws Exception {
        return mvc.perform(put("/api/v1/member/health-assessment").with(user(details(id)))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String code() { return marker + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " health fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build()).getId();
    }

    private long member() {
        long id = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                id, code(), LocalDate.now());
        return id;
    }

    private long coach() {
        long id = account("COACH");
        jdbc.update("INSERT INTO coach_profile(user_id, is_public, display_order, created_at) VALUES (?, 0, 0, ?)", id, LocalDateTime.now());
        return id;
    }

    private CustomUserDetails details(long id) {
        return new CustomUserDetails(new TransactionTemplate(transactions).execute(s -> {
            UserAccount account = users.findById(id).orElseThrow();
            org.hibernate.Hibernate.initialize(account.getRole());
            return account;
        }));
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
}
