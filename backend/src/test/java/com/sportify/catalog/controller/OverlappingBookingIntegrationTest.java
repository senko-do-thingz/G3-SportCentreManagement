package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.MemberBookingLock;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.support.SessionIdentityFixtures;
import org.junit.jupiter.api.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * E2E-BUG-01 (Flow 2): one member cannot hold two CONFIRMED bookings that intersect in time on the same date.
 * PROVISIONAL: the expected results follow E2E-D1 Option A (coach-led and SELF_TRAINING alike, staff bookings
 * included, touching boundaries allowed), PENDING CODEOWNER approval. Real SQL Server transactions and HTTP security.
 */
@AutoConfigureMockMvc
@Import(OverlappingBookingIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OverlappingBookingIntegrationTest extends AbstractIntegrationTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDate TOMORROW = TODAY.plusDays(1);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    static final SecureRandom RANDOM = new SecureRandom();

    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock overlappingBookingClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired FacilityRepository facilities;
    @Autowired SportClassRepository classes;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired MemberBookingLock memberBookingLock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private final String marker = "b01" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private final List<ExecutorService> workerPools = new ArrayList<>();
    private Sport sport;
    private SportClass sportClass;
    private long facilityOne;
    private long facilityTwo;
    private long coachId;
    private long coachedPackageId;
    private long selfPackageId;
    private UserAccount memberA;
    private UserAccount memberB;
    private UserAccount manager;
    private UserAccount receptionist;

    @BeforeEach void fixture() {
        sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        manager = account("MANAGER");
        receptionist = account("RECEPTIONIST");
        memberA = member();
        memberB = member();
        coachId = account("COACH").getId();
        jdbc.update("INSERT INTO coach_profile(user_id, primary_sport_id, is_public, display_order) VALUES (?, ?, 1, 0)", coachId, sport.getId());
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", coachId, sport.getId());
        facilityOne = facility();
        facilityTwo = facility();
        sportClass = classes.saveAndFlush(SportClass.builder().code(code()).name("B01 class").sport(sport).maxMembers(40).isActive(true).build());
        coachedPackageId = newPackage(TrainingFormat.COACH_LED);
        selfPackageId = newPackage(TrainingFormat.SELF_TRAINING);
    }

    @AfterEach void cleanup() {
        assertThat(workerPools).allMatch(ExecutorService::isTerminated, "all concurrent writers stopped before cleanup");
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM notification WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM check_in WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM booking WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM class_session WHERE sport_id = ?", sport.getId());
        jdbc.update("DELETE FROM sport_class WHERE sport_id = ?", sport.getId());
        jdbc.update("DELETE FROM sport_package_registration WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package WHERE sport_id = ?", sport.getId());
        jdbc.update("DELETE FROM coach_sport WHERE coach_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM coach_profile WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM facility WHERE sport_id = ?", sport.getId());
        jdbc.update("DELETE FROM sport WHERE id = ?", sport.getId());
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
    }

    // ---------------------------------------------------------------- sequential rules (B01-01..05)

    @Test
    void b01_01_secondCoachLedBookingThatOverlapsIsRejectedWithoutConsumingCreditOrSeat() {
        long first = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long second = coached(TOMORROW, LocalTime.of(13, 0), facilityTwo);
        long reg = registration(memberA, TrainingFormat.COACH_LED);

        assertThat(book(memberA, first, reg).status()).isEqualTo(201);
        Outcome conflict = book(memberA, second, reg);

        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.code()).isEqualTo("TIME_CONFLICT");
        assertThat(balance(reg)).isEqualTo(2);
        assertThat(seats(first)).isEqualTo(1);
        assertThat(seats(second)).isZero();
        assertThat(confirmed(memberA)).isEqualTo(1);
        assertThat(auditCount("BOOKING_CREATED")).isEqualTo(1);
    }

    @Test
    void b01_02_selfTrainingSessionOverlappingACoachLedBookingIsAlsoRejected() {
        long coachLed = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long self = selfTraining(TOMORROW, LocalTime.of(12, 30), facilityTwo);
        long coachedReg = registration(memberA, TrainingFormat.COACH_LED);
        long selfReg = registration(memberA, TrainingFormat.SELF_TRAINING);

        assertThat(book(memberA, coachLed, coachedReg).status()).isEqualTo(201);
        Outcome conflict = book(memberA, self, selfReg);

        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.code()).isEqualTo("TIME_CONFLICT");
        assertThat(balance(selfReg)).isEqualTo(3);
        assertThat(seats(self)).isZero();
    }

    @Test
    void b01_03_sessionsThatOnlyTouchAtTheBoundaryAreBothAllowed() {
        long first = coached(TOMORROW, LocalTime.of(12, 0), facilityOne); // 12:00-13:30
        long second = coached(TOMORROW, LocalTime.of(13, 30), facilityTwo); // 13:30-15:00
        long reg = registration(memberA, TrainingFormat.COACH_LED);

        assertThat(book(memberA, first, reg).status()).isEqualTo(201);
        assertThat(book(memberA, second, reg).status()).isEqualTo(201);
        assertThat(balance(reg)).isEqualTo(1);
    }

    @Test
    void b01_04_cancelledBookingsAndOtherDatesDoNotConflict() {
        long first = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long overlapping = coached(TOMORROW, LocalTime.of(12, 30), facilityTwo);
        long nextDay = coached(TOMORROW.plusDays(1), LocalTime.of(12, 0), facilityTwo);
        long reg = registration(memberA, TrainingFormat.COACH_LED);
        long booking = book(memberA, first, reg).body().path("id").asLong();
        assertThat(http(delete("/api/v1/bookings/" + booking), memberA).status()).isEqualTo(200);

        assertThat(book(memberA, overlapping, reg).status()).as("cancelled booking ignored").isEqualTo(201);
        assertThat(book(memberA, nextDay, reg).status()).as("different date").isEqualTo(201);
        assertThat(balance(reg)).isEqualTo(1);
    }

    @Test
    void b01_05_staffBookingOnBehalfOfTheMemberObeysTheSameRule() {
        long first = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long second = coached(TOMORROW, LocalTime.of(12, 45), facilityTwo);
        long reg = registration(memberA, TrainingFormat.COACH_LED);
        assertThat(book(memberA, first, reg).status()).isEqualTo(201);

        Outcome staff = http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "sessionId", second, "packageRegistrationId", reg, "memberId", memberA.getId()))), receptionist);

        assertThat(staff.status()).isEqualTo(409);
        assertThat(staff.code()).isEqualTo("TIME_CONFLICT");
        assertThat(balance(reg)).isEqualTo(2);
        assertThat(seats(second)).isZero();
    }

    // ---------------------------------------------------------------- concurrency (B01-06, B01-07, B01-08)

    @Test @Timeout(100)
    void b01_06_forcedOrder_secondRequestWaitsOnTheMemberLockThenGetsTimeConflict() throws Exception {
        long first = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long second = coached(TOMORROW, LocalTime.of(12, 30), facilityTwo);
        long reg = registration(memberA, TrainingFormat.COACH_LED);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Outcome> winner = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID"));
                memberBookingLock.acquire(memberA.getId());
                held.countDown(); await(release);
                return book(memberA, first, reg);
            }));
            await(held);
            Future<Outcome> loser = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID"));
                return book(memberA, second, reg);
            }));
            // SQL Server reports the waiting application lock request as its internal procedure xp_userlock.
            awaitBlocked(secondSpid, firstSpid, "xp_userlock");
            release.countDown();

            assertThat(winner.get(60, TimeUnit.SECONDS).status()).isEqualTo(201);
            Outcome conflict = loser.get(60, TimeUnit.SECONDS);
            assertThat(conflict.status()).isEqualTo(409);
            assertThat(conflict.code()).isEqualTo("TIME_CONFLICT");
        } finally { release.countDown(); stop(pool); }

        assertThat(confirmed(memberA)).isEqualTo(1);
        assertThat(balance(reg)).isEqualTo(2);
        assertThat(seats(first) + seats(second)).isEqualTo(1);
    }

    @RepeatedTest(5) @Timeout(100)
    void b01_06_simultaneousRequestsForDifferentOverlappingSessions_exactlyOneSucceeds() throws Exception {
        long first = selfTraining(TOMORROW, LocalTime.of(16, 0), facilityOne);
        long second = selfTraining(TOMORROW, LocalTime.of(16, 45), facilityTwo);
        long reg = registration(memberA, TrainingFormat.SELF_TRAINING);
        var start = new CyclicBarrier(2);
        var pool = newWorkerPool();
        List<Outcome> outcomes;
        try {
            Future<Outcome> a = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return inWriter(() -> book(memberA, first, reg)); });
            Future<Outcome> b = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return inWriter(() -> book(memberA, second, reg)); });
            outcomes = List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));
        } finally { stop(pool); }

        assertThat(outcomes).extracting(Outcome::status).containsExactlyInAnyOrder(201, 409);
        assertThat(outcomes).filteredOn(o -> o.status() == 409).extracting(Outcome::code).containsExactly("TIME_CONFLICT");
        assertThat(confirmed(memberA)).isEqualTo(1);
        assertThat(balance(reg)).as("8 -> 6 in the original report; one credit only now").isEqualTo(2);
        assertThat(seats(first) + seats(second)).isEqualTo(1);
    }

    @Test @Timeout(100)
    void b01_07_aDifferentMemberIsNotSerializedBehindAnotherMembersLock() throws Exception {
        long first = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long second = coached(TOMORROW, LocalTime.of(12, 30), facilityTwo);
        long regA = registration(memberA, TrainingFormat.COACH_LED);
        long regB = registration(memberB, TrainingFormat.COACH_LED);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = newWorkerPool();
        try {
            Future<Outcome> holderA = pool.submit(() -> inWriter(() -> {
                memberBookingLock.acquire(memberA.getId());
                held.countDown(); await(release);
                return book(memberA, first, regA);
            }));
            await(held);
            Future<Outcome> memberBBooking = pool.submit(() -> inWriter(() -> book(memberB, second, regB)));

            assertThat(memberBBooking.get(20, TimeUnit.SECONDS).status())
                    .as("member B completes while member A's lock is still held").isEqualTo(201);
            release.countDown();
            assertThat(holderA.get(60, TimeUnit.SECONDS).status()).isEqualTo(201);
        } finally { release.countDown(); stop(pool); }

        assertThat(confirmed(memberA)).isEqualTo(1);
        assertThat(confirmed(memberB)).isEqualTo(1);
    }

    @RepeatedTest(3) @Timeout(120)
    void b01_08_mixedCreateCancelAndManagerCancellationKeepCountersConsistentWithoutDeadlock() throws Exception {
        long s1 = coached(TOMORROW, LocalTime.of(12, 0), facilityOne);
        long s2 = coached(TOMORROW, LocalTime.of(12, 30), facilityTwo);
        long s3 = coached(TOMORROW, LocalTime.of(18, 0), facilityOne);
        long regA = registration(memberA, TrainingFormat.COACH_LED);
        long regB = registration(memberB, TrainingFormat.COACH_LED);
        long bookingA = book(memberA, s1, regA).body().path("id").asLong();
        assertThat(book(memberB, s3, regB).status()).isEqualTo(201);
        assertThat(book(memberA, s3, regA).status()).isEqualTo(201);

        var start = new CyclicBarrier(3);
        var pool = Executors.newFixedThreadPool(3);
        workerPools.add(pool);
        List<Outcome> outcomes;
        try {
            Future<Outcome> cancel = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return inWriter(() -> http(delete("/api/v1/bookings/" + bookingA), memberA)); });
            Future<Outcome> create = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return inWriter(() -> book(memberA, s2, regA)); });
            Future<Outcome> managerCancel = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return inWriter(() -> http(put("/api/v1/manager/sessions/" + s3 + "/cancel")
                    .contentType(MediaType.APPLICATION_JSON).content(write(Map.of("reason", "Facility maintenance"))), manager)); });
            outcomes = List.of(cancel.get(60, TimeUnit.SECONDS), create.get(60, TimeUnit.SECONDS), managerCancel.get(60, TimeUnit.SECONDS));
        } finally { stop(pool); }

        assertThat(outcomes).as("no deadlock victim (1205) or server error").allMatch(o -> o.status() < 500);
        assertThat(outcomes.get(0).status()).isEqualTo(200);
        assertThat(outcomes.get(2).status()).isEqualTo(200);
        assertThat(outcomes.get(1).status()).isIn(201, 409);
        for (long session : List.of(s1, s2, s3)) {
            assertThat(seats(session)).as("booked_count matches confirmed bookings for " + session)
                    .isEqualTo(count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CONFIRMED'", session));
        }
        for (long reg : List.of(regA, regB)) {
            assertThat(balance(reg)).as("credits match confirmed bookings for " + reg)
                    .isEqualTo(3 - count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ? AND status = 'CONFIRMED'", reg));
        }
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private record Outcome(int status, JsonNode body) {
        String code() { return body.path("code").asText(null); }
    }

    private String code() { return marker + ++serial; }
    private UserAccount account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build());
    }
    private UserAccount member() {
        var account = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                account.getId(), code(), TODAY);
        return account;
    }
    private long facility() {
        return facilities.saveAndFlush(Facility.builder().code(code()).name("B01 court").sport(sport)
                .facilityType(FacilityType.COURT).isActive(true).build()).getId();
    }
    private long newPackage(TrainingFormat format) {
        return packages.saveAndFlush(SportPackage.builder().code(code()).name("B01 " + format).sport(sport)
                .trainingFormat(format).durationDays(60).sessionCount(10).sessionMinutes(90)
                .priceAmount(new BigDecimal("100000")).isActive(true).build()).getId();
    }
    private long registration(UserAccount member, TrainingFormat format) {
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel, original_price,
                    paid_amount, total_sessions, remaining_sessions, start_date, end_date, status, created_by_user_id)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'RECEPTION', 100000, 100000, 3, 3, ?, ?, 'ACTIVE', ?)
                """, Long.class, code(), member.getId(), format == TrainingFormat.COACH_LED ? coachedPackageId : selfPackageId,
                TODAY.minusDays(2), TODAY.plusDays(40), manager.getId());
    }
    private long coached(LocalDate date, LocalTime start, long facility) { return session(date, start, facility, TrainingFormat.COACH_LED); }
    private long selfTraining(LocalDate date, LocalTime start, long facility) { return session(date, start, facility, TrainingFormat.SELF_TRAINING); }
    /** A published session lasting 90 minutes. */
    private long session(LocalDate date, LocalTime start, long facility, TrainingFormat format) {
        boolean coachLed = format == TrainingFormat.COACH_LED;
        return jdbc.queryForObject("""
                SET NOCOUNT ON; DECLARE @created TABLE(id BIGINT);
                INSERT INTO class_session(class_id, sport_id, facility_id, coach_id, training_type, session_date,
                    start_time, end_time, capacity, booked_count, status, session_code) OUTPUT INSERTED.id INTO @created
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 10, 0, 'PUBLISHED', ?); SELECT id FROM @created;
                """, Long.class, coachLed ? sportClass.getId() : null, sport.getId(), facility, coachLed ? coachId : null,
                format.name(), date, start, start.plusMinutes(90), SessionIdentityFixtures.reserve(jdbc, date, "FOOTBALL"));
    }
    private Outcome book(UserAccount member, long session, long registration) {
        return http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("sessionId", session, "packageRegistrationId", registration))), member);
    }
    private int confirmed(UserAccount member) {
        return count("SELECT COUNT(*) FROM booking WHERE member_id = ? AND status = 'CONFIRMED'", member.getId());
    }
    private int balance(long registration) { return count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration); }
    private int seats(long session) { return count("SELECT booked_count FROM class_session WHERE id = ?", session); }
    private int count(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args)); }
    private long auditCount(String action) {
        return count("SELECT COUNT(*) FROM activity_log WHERE action = ? AND actor_user_id IN (SELECT id FROM user_account WHERE email LIKE ?)", action, marker + "%");
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception error) { throw new AssertionError(error); }
    }
    private Outcome http(MockHttpServletRequestBuilder request, UserAccount actor) {
        try {
            var response = mvc.perform(request.with(user(new CustomUserDetails(actor)))).andReturn().getResponse();
            String body = response.getContentAsString();
            return new Outcome(response.getStatus(), body.isBlank() ? json.createObjectNode() : json.readTree(body));
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private Outcome inWriter(java.util.function.Supplier<Outcome> work) {
        var template = new TransactionTemplate(transactions);
        template.setTimeout(60);
        return template.execute(status -> {
            Outcome outcome = work.get();
            if (outcome.status() >= 400) status.setRollbackOnly();
            return outcome;
        });
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(30, TimeUnit.SECONDS)).as("bounded race coordination").isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private void awaitBlocked(AtomicInteger waiting, AtomicInteger blocking, String sqlFragment) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (waiting.get() > 0 && blocking.get() > 0 && count("""
                    SELECT COUNT(*) FROM sys.dm_exec_requests r CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
                    WHERE r.session_id = ? AND r.blocking_session_id = ? AND t.text LIKE ?
                    """, waiting.get(), blocking.get(), "%" + sqlFragment + "%") > 0) return;
        }
        fail("Expected the second SQL Server connection (" + waiting.get() + ") to wait for the first (" + blocking.get() + "); requests "
                + jdbc.queryForList("SELECT session_id, blocking_session_id, wait_type, command FROM sys.dm_exec_requests WHERE session_id IN (?, ?)", waiting.get(), blocking.get())
                + ", application locks " + jdbc.queryForList("SELECT request_session_id, resource_description, request_mode, request_status, request_owner_type FROM sys.dm_tran_locks WHERE resource_type = 'APPLICATION'"));
    }
    private ExecutorService newWorkerPool() {
        var pool = Executors.newFixedThreadPool(2);
        workerPools.add(pool);
        return pool;
    }
    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(35, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
