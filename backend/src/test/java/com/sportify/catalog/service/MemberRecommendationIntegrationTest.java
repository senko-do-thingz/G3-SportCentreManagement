package com.sportify.catalog.service;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences;
import com.sportify.catalog.dto.TrainingGuidanceResponse.ClassRecommendation;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.support.SessionIdentityFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 5 Member recommendations on real SQL Server: overlap exclusion, per-sport level, age groups, saved
 * recommendations and their authorization. Non-transactional; fixtures use their own sports and classes.
 */
@AutoConfigureMockMvc
@Import(MemberRecommendationIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MemberRecommendationIntegrationTest extends AbstractIntegrationTest {
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final LocalDate TODAY = LocalDate.of(2027, 3, 10);
    static final LocalDate TOMORROW = TODAY.plusDays(1);
    static final SecureRandom RANDOM = new SecureRandom();

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock memberRecommendationClock() { return Clock.fixed(TODAY.atTime(10, 0).atZone(ZONE).toInstant(), ZONE); }
    }

    @Autowired TrainingGuidanceService guidance;
    @Autowired MemberRecommendationService recommendations;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;

    private final String marker = "mrc" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long sportA, sportB, facilityA, facilityB, member, otherMember;

    @BeforeEach
    void fixture() {
        sportA = sport();
        sportB = sport();
        facilityA = facility(sportA);
        facilityB = facility(sportB);
        member = member("BEGINNER");
        otherMember = member("BEGINNER");
    }

    @Test
    void sessionsOverlappingAConfirmedBookingAreNotRecommendedButTouchingOnesAre() {
        long booked = classOf(sportA, "BEGINNER", null);
        long bookedSession = session(booked, sportA, facilityA, LocalTime.of(10, 0), LocalTime.of(11, 0));
        jdbc.update("INSERT INTO booking(booking_code, session_id, member_id, status, booked_at) VALUES (?, ?, ?, 'CONFIRMED', ?)",
                code(), bookedSession, member, LocalDateTime.of(TODAY, LocalTime.of(9, 0)));
        long overlapping = classOf(sportA, "BEGINNER", null);
        session(overlapping, sportA, facilityA, LocalTime.of(10, 30), LocalTime.of(11, 30));
        long touching = classOf(sportA, "BEGINNER", null);
        session(touching, sportA, facilityA, LocalTime.of(11, 0), LocalTime.of(12, 0));

        var classes = recommendedClasses(member, sportA);
        assertThat(classes).contains(touching).doesNotContain(overlapping, booked);
        // Another member without that booking still gets the overlapping class.
        assertThat(recommendedClasses(otherMember, sportA)).contains(overlapping);
    }

    @Test
    void perSportLevelOverridesTheGeneralLevelOnlyForThatSport() {
        long advancedA = classOf(sportA, "ADVANCED", null);
        session(advancedA, sportA, facilityA, LocalTime.of(14, 0), LocalTime.of(15, 0));
        long beginnerA = classOf(sportA, "BEGINNER", null);
        session(beginnerA, sportA, facilityA, LocalTime.of(16, 0), LocalTime.of(17, 0));
        long beginnerB = classOf(sportB, "BEGINNER", null);
        session(beginnerB, sportB, facilityB, LocalTime.of(14, 0), LocalTime.of(15, 0));

        recommendations.saveSportLevel(member, sportA, "ADVANCED");

        var a = recommend(member, sportA);
        assertThat(a).extracting(ClassRecommendation::classId).contains(advancedA).doesNotContain(beginnerA);
        assertThat(a.stream().filter(item -> item.classId() == advancedA).findFirst().orElseThrow().reasons())
                .anyMatch(reason -> reason.startsWith("Matches your saved") && reason.endsWith("level: ADVANCED"));
        assertThat(recommendedClasses(member, sportB)).contains(beginnerB);

        recommendations.clearSportLevel(member, sportA);
        assertThat(recommendedClasses(member, sportA)).contains(beginnerA).doesNotContain(advancedA);
    }

    @Test
    void ageGroupsExcludeClassesOutsideTheMembersAgeAndAskForADateOfBirthWhenMissing() {
        long ageGroup = jdbc.queryForObject("INSERT INTO age_group(code, label, min_age, max_age, display_order, is_active) OUTPUT INSERTED.id VALUES (?, '16+', 16, NULL, 0, 1)",
                Long.class, code());
        long adults = classOf(sportA, "BEGINNER", ageGroup);
        session(adults, sportA, facilityA, LocalTime.of(18, 0), LocalTime.of(19, 0));
        jdbc.update("UPDATE user_account SET date_of_birth = ? WHERE id = ?", TOMORROW.minusYears(15), member);
        assertThat(recommendedClasses(member, sportA)).doesNotContain(adults);

        jdbc.update("UPDATE user_account SET date_of_birth = ? WHERE id = ?", TOMORROW.minusYears(16), member);
        var fits = recommend(member, sportA).stream().filter(item -> item.classId() == adults).findFirst().orElseThrow();
        assertThat(fits.reasons()).contains("Fits the 16+ age group");

        var unknown = recommend(otherMember, sportA).stream().filter(item -> item.classId() == adults).findFirst().orElseThrow();
        assertThat(unknown.reasons()).contains("Age group 16+: add your date of birth to your profile to confirm it fits");
    }

    @Test
    void savedRecommendationsAreIdempotentOwnedAndShowTheNextSession() throws Exception {
        long saved = classOf(sportA, "BEGINNER", null);
        long next = session(saved, sportA, facilityA, LocalTime.of(14, 0), LocalTime.of(15, 0));
        String body = "{\"classId\":" + saved + ",\"reason\":\"Fits Thursday evenings\"}";

        mvc.perform(post("/api/v1/member/training/saved-recommendations").with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.classId").value(saved))
                .andExpect(jsonPath("$.reason").value("Fits Thursday evenings"))
                .andExpect(jsonPath("$.nextSession.sessionId").value(next));
        mvc.perform(post("/api/v1/member/training/saved-recommendations").with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM saved_recommendation WHERE member_id = ?", member)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE member_id = ?", member)).isZero();

        long id = jdbc.queryForObject("SELECT id FROM saved_recommendation WHERE member_id = ?", Long.class, member);
        mvc.perform(get("/api/v1/member/training/saved-recommendations").with(user(details(otherMember))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/v1/member/training/saved-recommendations/" + id).with(user(details(otherMember))))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/member/training/saved-recommendations/" + id).with(user(details(member))))
                .andExpect(status().isNoContent());
        assertThat(count("SELECT COUNT(*) FROM saved_recommendation WHERE member_id = ?", member)).isZero();

        jdbc.update("UPDATE sport_class SET is_active = 0 WHERE id = ?", saved);
        mvc.perform(post("/api/v1/member/training/saved-recommendations").with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void sportLevelsValidateInputAndAreMemberOnly() throws Exception {
        mvc.perform(put("/api/v1/member/training/sport-levels/" + sportA).with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"EXPERT\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/member/training/sport-levels/" + Long.MAX_VALUE).with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"ADVANCED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/member/training/sport-levels/" + sportA).with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"ADVANCED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.level").value("ADVANCED"));
        mvc.perform(put("/api/v1/member/training/sport-levels/" + sportA).with(user(details(member))).contentType(MediaType.APPLICATION_JSON).content("{\"level\":\"INTERMEDIATE\"}"))
                .andExpect(status().isOk());
        assertThat(recommendations.sportLevels(member)).filteredOn(level -> level.sportId() == sportA).singleElement()
                .satisfies(level -> assertThat(level.level()).isEqualTo("INTERMEDIATE"));
        assertThat(recommendations.sportLevels(otherMember)).filteredOn(level -> level.sportId() == sportA).singleElement()
                .satisfies(level -> assertThat(level.level()).isNull());
        long coach = account("COACH");
        mvc.perform(get("/api/v1/member/training/sport-levels").with(user(details(coach)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/member/training/saved-recommendations").with(user(details(coach)))).andExpect(status().isForbidden());
    }

    private List<ClassRecommendation> recommend(long memberId, long sportId) {
        return guidance.recommendations(memberId, new Preferences(sportId, null, null, TOMORROW, TOMORROW, null, null, 0, 20)).items();
    }

    private List<Long> recommendedClasses(long memberId, long sportId) {
        return recommend(memberId, sportId).stream().map(ClassRecommendation::classId).toList();
    }

    private String code() { return marker + ++serial; }

    private long sport() {
        return jdbc.queryForObject("INSERT INTO sport(code, name, venue_type, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'INDOOR', 1)",
                Long.class, code(), "Sport " + code());
    }

    private long facility(long sport) {
        return jdbc.queryForObject("INSERT INTO facility(code, name, facility_type, sport_id, is_active) OUTPUT INSERTED.id VALUES (?, ?, 'COURT', ?, 1)",
                Long.class, code(), "Court " + marker, sport);
    }

    private long classOf(long sport, String level, Long ageGroup) {
        return jdbc.queryForObject("INSERT INTO sport_class(code, name, sport_id, level, max_members, is_active, age_group_id) OUTPUT INSERTED.id VALUES (?, ?, ?, ?, 20, 1, ?)",
                Long.class, code(), "Class " + code(), sport, level, ageGroup);
    }

    private long session(long classId, long sport, long facility, LocalTime start, LocalTime end) {
        return jdbc.queryForObject("""
                SET NOCOUNT ON; DECLARE @created TABLE(id BIGINT);
                INSERT INTO class_session(class_id, sport_id, facility_id, training_type, session_date, start_time, end_time,
                    capacity, booked_count, status, session_code) OUTPUT INSERTED.id INTO @created
                VALUES (?, ?, ?, 'SELF_TRAINING', ?, ?, ?, 10, 0, 'PUBLISHED', ?); SELECT id FROM @created;""", Long.class,
                classId, sport, facility, TOMORROW, start, end, SessionIdentityFixtures.reserve(jdbc, TOMORROW, "FOOTBALL"));
    }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " recommendation fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build()).getId();
    }

    private long member(String level) {
        long id = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, ?, ?)", id, code(), level, TODAY);
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
