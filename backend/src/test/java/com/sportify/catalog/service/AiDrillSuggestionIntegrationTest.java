package com.sportify.catalog.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.support.SessionIdentityFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 5 AI drill drafting against real SQL Server. The generator is a Mockito bean, so no provider is called.
 * Verifies the de-identified context, the AI_GENERATED response, rule-catalog fallback and that authorization runs
 * before any generator call. Non-transactional: fixtures are committed in the disposable test database.
 */
@AutoConfigureMockMvc
@Import(AiDrillSuggestionIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AiDrillSuggestionIntegrationTest extends AbstractIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    static final SecureRandom RANDOM = new SecureRandom();
    static final DrillDraft AI_DRAFT = new DrillDraft("Six-corner shadow footwork", "Footwork", 10,
            "Shadow the six corners at match pace, then reset.", "Footwork scored 4.5/10 and is the coach's focus.", "ADVANCED");

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock aiDrillClock() { return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE); }
    }

    @MockitoBean DrillGenerator generator;
    @Autowired TrainingWriteService training;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private final String marker = "aid" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long coach, otherCoach, member, classId, futureBooking;

    @BeforeEach
    void fixture() {
        long sport = jdbc.queryForObject("SELECT id FROM sport WHERE code = 'BADMINTON'", Long.class);
        long facility = jdbc.queryForObject("INSERT INTO facility(code, name, facility_type, sport_id, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'COURT', ?, 1)",
                Long.class, code(), "AI drill court", sport);
        coach = coach(sport);
        otherCoach = coach(sport);
        classId = jdbc.queryForObject("INSERT INTO sport_class(code, name, sport_id, level, max_members, is_active, coach_id) OUTPUT INSERTED.id VALUES (?, ?, ?, 'BEGINNER', 20, 1, ?)",
                Long.class, code(), "AI drill class", sport, coach);
        member = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, main_goal, joined_on) VALUES (?, ?, 'ADVANCED', 'COMPETITION', ?)",
                member, code(), TODAY.minusMonths(3));
        long metric = jdbc.queryForObject("INSERT INTO skill_metric(sport_id, name, max_score) OUTPUT INSERTED.id VALUES (?, ?, 10)", Long.class, sport, "Footwork " + marker);

        long ended = session(sport, facility, TODAY, LocalTime.of(7, 0), LocalTime.of(8, 30));
        long pastBooking = booking(ended);
        training.coachAttendance(coach, new TrainingWriteRequest.Attendance(pastBooking, TrainingWriteRequest.AttendanceStatus.PRESENT, null));
        training.createResult(coach, new TrainingWriteRequest.Result(ended, member, "Slow recovery to the base",
                List.of(new TrainingWriteRequest.Score(metric, new BigDecimal("4.50")))));

        long upcoming = session(sport, facility, TODAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(10, 30));
        futureBooking = booking(upcoming);
        long plan = training.createPlan(coach, new TrainingWriteRequest.Plan(classId, member, futureBooking, "Base plan " + marker, null,
                List.of(new TrainingWriteRequest.Step("Warm-up", 10, "Easy movement")))).id();
        training.feedback(coach, plan, new TrainingWriteRequest.Feedback(4, "Good effort", "Strong serve", "Footwork recovery"));
        reset(generator);
        when(generator.version()).thenReturn("test-model");
    }

    @Test
    void aiDraftsUseTheDeidentifiedTrainingContextAndAreNotSaved() {
        when(generator.generate(any())).thenReturn(List.of(AI_DRAFT));
        int plans = count("SELECT COUNT(*) FROM session_plan WHERE booking_id = ?", futureBooking);

        var result = training.drillSuggestions(coach, futureBooking);

        assertThat(result.mode()).isEqualTo("AI_GENERATED");
        assertThat(result.ruleVersion()).isEqualTo("test-model");
        assertThat(result.persisted()).isFalse();
        assertThat(result.requiresCoachReview()).isTrue();
        assertThat(result.level()).isEqualTo("ADVANCED");
        assertThat(result.drafts()).containsExactly(AI_DRAFT);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE booking_id = ?", futureBooking)).isEqualTo(plans);

        ArgumentCaptor<DrillGenerator.Input> sent = ArgumentCaptor.forClass(DrillGenerator.Input.class);
        verify(generator).generate(sent.capture());
        var input = sent.getValue();
        assertThat(input.sport()).isEqualTo("Badminton");
        assertThat(input.memberLevel()).isEqualTo("ADVANCED");
        assertThat(input.classLevel()).isEqualTo("BEGINNER");
        assertThat(input.goal()).isEqualTo("COMPETITION");
        assertThat(input.attendedSessions()).isEqualTo(1);
        assertThat(input.recentScores()).singleElement().satisfies(score -> {
            assertThat(score.metric()).isEqualTo("Footwork " + marker);
            assertThat(score.score()).isEqualByComparingTo("4.50");
            assertThat(score.maxScore()).isEqualTo(10);
            assertThat(score.sessionDate()).isEqualTo(TODAY);
        });
        assertThat(input.latestStrengths()).isEqualTo("Strong serve");
        assertThat(input.latestAreasForImprovement()).isEqualTo("Footwork recovery");
        String email = jdbc.queryForObject("SELECT email FROM user_account WHERE id = ?", String.class, member);
        assertThat(input.toString()).doesNotContain(email, "AI member");
        // The context type itself carries no identifier field (no member id, code, name, e-mail or phone).
        assertThat(java.util.Arrays.stream(DrillGenerator.Input.class.getRecordComponents()).map(c -> c.getName()))
                .containsExactly("scope", "sport", "classLevel", "memberLevel", "goal", "attendedSessions", "recentScores",
                        "latestStrengths", "latestAreasForImprovement", "focus", "sessionMinutes", "group");
        assertThat(input.scope()).isEqualTo(DrillGenerator.SCOPE_INDIVIDUAL);
        assertThat(input.group()).isNull();
    }

    @Test
    void coachFocusAndSessionDurationReachTheGeneratorAndLimitRuleDrafts() {
        when(generator.generate(any())).thenReturn(List.of(AI_DRAFT));
        training.drillSuggestions(coach, futureBooking, DrillGenerator.Options.of("Net kills", 45));
        ArgumentCaptor<DrillGenerator.Input> sent = ArgumentCaptor.forClass(DrillGenerator.Input.class);
        verify(generator).generate(sent.capture());
        assertThat(sent.getValue().focus()).isEqualTo("Net kills");
        assertThat(sent.getValue().sessionMinutes()).isEqualTo(45);

        when(generator.generate(any())).thenReturn(List.of());
        var rules = training.drillSuggestions(coach, futureBooking, DrillGenerator.Options.of(null, 15));
        assertThat(rules.mode()).isEqualTo("RULE_BASED");
        assertThat(rules.drafts().stream().mapToInt(d -> d.durationMinutes()).sum()).isLessThanOrEqualTo(15);
    }

    @Test
    void classScopeUsesOnlyCountsAndClassAveragesAndSavesNothing() {
        long second = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, main_goal, joined_on) VALUES (?, ?, 'BEGINNER', 'IMPROVE_FITNESS', ?)",
                second, code(), TODAY);
        long upcoming = jdbc.queryForObject("SELECT session_id FROM booking WHERE id = ?", Long.class, futureBooking);
        jdbc.update("INSERT INTO booking(booking_code, session_id, member_id, status, booked_at) VALUES (?, ?, ?, 'CONFIRMED', ?)",
                code(), upcoming, second, NOW.minusDays(1));
        long third = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, main_goal, joined_on) VALUES (?, ?, 'BEGINNER', 'BUILD_SKILLS', ?)",
                third, code(), TODAY);
        jdbc.update("INSERT INTO booking(booking_code, session_id, member_id, status, booked_at) VALUES (?, ?, ?, 'CONFIRMED', ?)",
                code(), upcoming, third, NOW.minusDays(1));
        when(generator.generate(any())).thenReturn(List.of(AI_DRAFT));
        int plans = count("SELECT COUNT(*) FROM session_plan WHERE class_id = ?", classId);

        var result = training.classDrillSuggestions(coach, classId, DrillGenerator.Options.of("Footwork", 60));

        assertThat(result.mode()).isEqualTo("AI_GENERATED");
        assertThat(result.persisted()).isFalse();
        assertThat(result.memberCount()).isEqualTo(3);
        assertThat(result.level()).isEqualTo("BEGINNER");
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE class_id = ?", classId)).isEqualTo(plans);
        ArgumentCaptor<DrillGenerator.Input> sent = ArgumentCaptor.forClass(DrillGenerator.Input.class);
        verify(generator).generate(sent.capture());
        var input = sent.getValue();
        assertThat(input.scope()).isEqualTo(DrillGenerator.SCOPE_CLASS);
        assertThat(input.memberLevel()).isNull();
        assertThat(input.goal()).isNull();
        assertThat(input.recentScores()).isEmpty();
        assertThat(input.focus()).isEqualTo("Footwork");
        assertThat(input.sessionMinutes()).isEqualTo(60);
        assertThat(input.group().members()).isEqualTo(3);
        assertThat(input.group().levels()).containsEntry("ADVANCED", 1).containsEntry("BEGINNER", 2);
        assertThat(input.group().goals()).containsEntry("COMPETITION", 1).containsEntry("IMPROVE_FITNESS", 1).containsEntry("BUILD_SKILLS", 1);
        assertThat(input.group().averages()).isEmpty(); // One recorded score cannot be reported as a class average.
    }

    @Test
    void classScopeIsLimitedToTheAssignedCoachAndFallsBackToRules() {
        assertThatThrownBy(() -> training.classDrillSuggestions(otherCoach, classId, DrillGenerator.Options.NONE))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(generator, never()).generate(any());
        when(generator.generate(any())).thenThrow(new IllegalStateException("provider down"));
        var rules = training.classDrillSuggestions(coach, classId, DrillGenerator.Options.NONE);
        assertThat(rules.mode()).isEqualTo("RULE_BASED");
        assertThat(rules.drafts()).isNotEmpty();
    }

    @Test
    void invalidCoachInputsAreRejectedOverHttp() throws Exception {
        mvc.perform(get("/api/v1/coach/training/bookings/" + futureBooking + "/drill-suggestions").param("sessionMinutes", "5").with(user(details(coach))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/coach/training/classes/" + classId + "/drill-suggestions").param("focus", "x".repeat(201)).with(user(details(coach))))
                .andExpect(status().isBadRequest());
        verify(generator, never()).generate(any());
    }

    @Test
    void providerFailureFallsBackToTheRuleCatalog() {
        when(generator.generate(any())).thenThrow(new IllegalStateException("provider down"));

        var result = training.drillSuggestions(coach, futureBooking);

        assertThat(result.mode()).isEqualTo("RULE_BASED");
        assertThat(result.ruleVersion()).isEqualTo("curated-drills-v2");
        assertThat(result.drafts()).isNotEmpty();
    }

    @Test
    void emptyAiResultFallsBackToTheRuleCatalog() {
        when(generator.generate(any())).thenReturn(List.of());

        assertThat(training.drillSuggestions(coach, futureBooking).mode()).isEqualTo("RULE_BASED");
    }

    @Test
    void anotherCoachIsRejectedBeforeAnyProviderCall() {
        assertThatThrownBy(() -> training.drillSuggestions(otherCoach, futureBooking)).isInstanceOf(ResourceNotFoundException.class);
        verify(generator, never()).generate(any());
    }

    @Test
    void httpEndpointReturnsTheAiDraftForTheAssignedCoach() throws Exception {
        when(generator.generate(any())).thenReturn(List.of(AI_DRAFT));
        mvc.perform(get("/api/v1/coach/training/bookings/" + futureBooking + "/drill-suggestions").with(user(details(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AI_GENERATED"))
                .andExpect(jsonPath("$.persisted").value(false))
                .andExpect(jsonPath("$.drafts[0].title").value(AI_DRAFT.title()));
        mvc.perform(get("/api/v1/coach/training/bookings/" + futureBooking + "/drill-suggestions").with(user(details(member))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/coach/training/classes/" + classId + "/drill-suggestions").param("sessionMinutes", "60").with(user(details(coach))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("RULE_BASED"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.persisted").value(false));
        mvc.perform(get("/api/v1/coach/training/classes/" + classId + "/drill-suggestions").with(user(details(member))))
                .andExpect(status().isForbidden());
    }

    private String code() { return marker + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role.equals("MEMBER") ? "AI member" : role + " ai fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build()).getId();
    }

    private long coach(long sport) {
        long id = account("COACH");
        jdbc.update("INSERT INTO coach_profile(user_id, primary_sport_id, is_public, display_order) VALUES (?, ?, 1, 0)", id, sport);
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", id, sport);
        return id;
    }

    private CustomUserDetails details(long id) {
        return new CustomUserDetails(new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s -> {
            UserAccount account = users.findById(id).orElseThrow();
            org.hibernate.Hibernate.initialize(account.getRole());
            return account;
        }));
    }

    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    private long session(long sport, long facility, LocalDate date, LocalTime start, LocalTime end) {
        return jdbc.queryForObject("""
                SET NOCOUNT ON; DECLARE @created TABLE(id BIGINT);
                INSERT INTO class_session(class_id, sport_id, facility_id, coach_id, training_type, session_date, start_time, end_time,
                    capacity, booked_count, status, session_code) OUTPUT INSERTED.id INTO @created
                VALUES (?, ?, ?, ?, 'COACH_LED', ?, ?, ?, 10, 1, 'PUBLISHED', ?); SELECT id FROM @created;""", Long.class,
                classId, sport, facility, coach, date, start, end, SessionIdentityFixtures.reserve(jdbc, date, "BADMINTON"));
    }

    private long booking(long session) {
        return jdbc.queryForObject("""
                INSERT INTO booking(booking_code, session_id, member_id, status, booked_at)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'CONFIRMED', ?)""", Long.class, code(), session, member, NOW.minusDays(1));
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
}
