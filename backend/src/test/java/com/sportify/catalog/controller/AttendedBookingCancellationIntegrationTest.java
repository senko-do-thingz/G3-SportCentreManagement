package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.BookingCreateRequest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.support.SessionIdentityFixtures;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * E2E-BUG-02 (Flow 4 + Flow 2): recorded PRESENT/LATE attendance must never be cancelled for a restored credit, by
 * ordinary member/reception cancellation, by manager session cancellation, or by either race order.
 * PROVISIONAL: the expected results follow the working recommendations of E2E-D3 and E2E-D5 Option A, which are
 * PENDING CODEOWNER approval. Real SQL Server transactions and HTTP security.
 */
@AutoConfigureMockMvc
@Import(AttendedBookingCancellationIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AttendedBookingCancellationIntegrationTest extends AbstractIntegrationTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    static final SecureRandom RANDOM = new SecureRandom();

    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock attendedCancellationClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }

    @Autowired ClassSessionRepository sessions;
    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired FacilityRepository facilities;
    @Autowired SportClassRepository classes;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private final String marker = "b02" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private final List<ExecutorService> workerPools = new ArrayList<>();
    private Sport sport;
    private SportClass sportClass;
    private long facilityId;
    private long packageId;
    private UserAccount coach;
    private UserAccount memberA;
    private UserAccount memberB;
    private UserAccount manager;
    private UserAccount receptionist;

    @BeforeEach void fixture() {
        sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        manager = account("MANAGER");
        receptionist = account("RECEPTIONIST");
        coach = account("COACH");
        memberA = member();
        memberB = member();
        jdbc.update("INSERT INTO coach_profile(user_id, primary_sport_id, is_public, display_order) VALUES (?, ?, 1, 0)", coach.getId(), sport.getId());
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", coach.getId(), sport.getId());
        facilityId = facilities.saveAndFlush(Facility.builder().code(code()).name("B02 court").sport(sport)
                .facilityType(FacilityType.COURT).isActive(true).build()).getId();
        sportClass = classes.saveAndFlush(SportClass.builder().code(code()).name("B02 class").sport(sport).maxMembers(40).isActive(true).build());
        packageId = packages.saveAndFlush(SportPackage.builder().code(code()).name("B02 package").sport(sport)
                .trainingFormat(TrainingFormat.COACH_LED).durationDays(60).sessionCount(10).sessionMinutes(90)
                .priceAmount(new BigDecimal("100000")).isActive(true).build()).getId();
    }

    @AfterEach void cleanup() {
        assertThat(workerPools).allMatch(ExecutorService::isTerminated, "all concurrent writers stopped before cleanup");
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        String ownSessions = "SELECT id FROM class_session WHERE sport_id = ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM notification WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM session_result_score WHERE result_id IN (SELECT id FROM session_result WHERE session_id IN (" + ownSessions + "))", sport.getId());
        jdbc.update("DELETE FROM session_result WHERE session_id IN (" + ownSessions + ")", sport.getId());
        jdbc.update("DELETE FROM attendance_correction WHERE attendance_record_id IN (SELECT id FROM attendance_record WHERE session_id IN (" + ownSessions + "))", sport.getId());
        jdbc.update("DELETE FROM attendance_record WHERE session_id IN (" + ownSessions + ")", sport.getId());
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

    // ---------------------------------------------------------------- ordinary cancellation (B02-01..04)

    @ParameterizedTest(name = "B02-01/02 reception cannot cancel an in-progress {0} booking")
    @ValueSource(strings = {"PRESENT", "LATE"})
    void receptionCannotCancelParticipatedBookingDuringSession(String status) {
        long session = session(LocalTime.of(9, 30));
        long reg = registration(memberA);
        long booking = bookedFixture(session, memberA, reg);
        assertThat(markAttendance(booking, status).status()).isEqualTo(201);
        State before = state(session, booking, reg);

        Outcome cancel = cancel(receptionist, booking);

        assertThat(cancel.status()).isEqualTo(400);
        assertThat(cancel.code()).isEqualTo("ALREADY_ATTENDED");
        assertThat(state(session, booking, reg)).isEqualTo(before);
        assertThat(before.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(before.attendance()).isEqualTo(status);
        assertThat(auditCount("BOOKING_CANCELLED")).isZero();
    }

    @ParameterizedTest(name = "B02-03/04 member cannot cancel an early-marked {0} booking before the 120-minute cutoff")
    @ValueSource(strings = {"PRESENT", "LATE"})
    void memberCannotCancelEarlyMarkedParticipationBeforeCutoff(String status) {
        long session = session(LocalTime.of(14, 0));
        long reg = registration(memberA);
        long booking = bookViaApi(memberA, session, reg);
        // BUG-07 / E2E-D9 preserved: the coach may mark attendance before the start on the session date.
        assertThat(markAttendance(booking, status).status()).isEqualTo(201);
        State before = state(session, booking, reg);

        Outcome cancel = cancel(memberA, booking);

        assertThat(cancel.status()).isEqualTo(400);
        assertThat(cancel.code()).isEqualTo("ALREADY_ATTENDED");
        assertThat(state(session, booking, reg)).isEqualTo(before);
        assertThat(before.remaining()).isEqualTo(2);
        assertThat(auditCount("BOOKING_CANCELLED")).isZero();
    }

    @Test
    void absentAttendanceStillAllowsCancellationWithExactlyOneCreditRestoration() {
        long session = session(LocalTime.of(14, 0));
        long reg = registration(memberA);
        long booking = bookViaApi(memberA, session, reg);
        assertThat(markAttendance(booking, "ABSENT").status()).isEqualTo(201);
        assertThat(state(session, booking, reg).remaining()).isEqualTo(2);

        assertThat(cancel(receptionist, booking).status()).isEqualTo(200);
        Outcome again = cancel(receptionist, booking);

        assertThat(again.status()).isEqualTo(400);
        assertThat(again.code()).isEqualTo("ALREADY_CANCELLED");
        State after = state(session, booking, reg);
        assertThat(after.bookingStatus()).isEqualTo("CANCELLED");
        assertThat(after.remaining()).as("B02-10: restored once").isEqualTo(3);
        assertThat(after.seats()).isZero();
        assertThat(auditCount("BOOKING_CANCELLED")).isEqualTo(1);
    }

    @Test
    void correctedPresentWithoutResultAllowsOneCancellationAndKeepsTheCorrectionHistory() {
        long session = session(LocalTime.of(14, 0));
        long reg = registration(memberA);
        long booking = bookViaApi(memberA, session, reg);
        long attendance = markAttendance(booking, "PRESENT").body().path("id").asLong();
        assertThat(cancel(memberA, booking).code()).isEqualTo("ALREADY_ATTENDED");

        Outcome correction = correctAttendance(attendance, "ABSENT", "Marked the wrong member by mistake");
        assertThat(correction.status()).isEqualTo(200);
        assertThat(cancel(memberA, booking).status()).isEqualTo(200);

        State after = state(session, booking, reg);
        assertThat(after.bookingStatus()).isEqualTo("CANCELLED");
        assertThat(after.remaining()).as("B02-05: exactly one restoration").isEqualTo(3);
        assertThat(after.attendance()).isEqualTo("ABSENT");
        assertThat(count("SELECT COUNT(*) FROM attendance_correction WHERE attendance_record_id = ? AND previous_status = 'PRESENT' AND new_status = 'ABSENT'", attendance)).isEqualTo(1);
    }

    @Test
    void savedResultKeepsTheExistingCorrectionGuardAndCancellationStaysRejected() {
        long session = session(LocalTime.of(8, 0)); // ended at 09:30, now 10:00
        long reg = registration(memberA);
        long booking = bookedFixture(session, memberA, reg);
        long attendance = markAttendance(booking, "PRESENT").body().path("id").asLong();
        assertThat(saveResult(session, memberA).status()).isEqualTo(201);
        State before = state(session, booking, reg);

        Outcome correction = correctAttendance(attendance, "ABSENT", "Trying to void after a saved result");
        Outcome reception = cancel(receptionist, booking);

        assertThat(correction.status()).as("B02-06 existing result-integrity rule").isEqualTo(409);
        assertThat(reception.status()).isEqualTo(400);
        assertThat(reception.code()).isEqualTo("ALREADY_ATTENDED");
        assertThat(state(session, booking, reg)).isEqualTo(before);
        assertThat(count("SELECT COUNT(*) FROM session_result WHERE session_id = ? AND member_id = ?", session, memberA.getId())).isEqualTo(1);
    }

    // ---------------------------------------------------------------- manager session cancellation (B02-07)

    @ParameterizedTest(name = "B02-07 manager cannot cancel a session with {0} evidence")
    @ValueSource(strings = {"PRESENT", "LATE", "CHECK_IN"})
    void managerCannotCancelSessionWithParticipationEvidence(String evidence) {
        long session = session(LocalTime.of(14, 0));
        long regA = registration(memberA), regB = registration(memberB);
        long attended = bookViaApi(memberA, session, regA);
        long ordinary = bookViaApi(memberB, session, regB);
        if (evidence.equals("CHECK_IN")) {
            Outcome checkIn = checkIn(memberA, attended);
            assertThat(checkIn.body().path("result").asText()).isEqualTo("ALLOWED");
        } else {
            assertThat(markAttendance(attended, evidence).status()).isEqualTo(201);
        }
        State attendedBefore = state(session, attended, regA), ordinaryBefore = state(session, ordinary, regB);

        Outcome cancel = cancelSession(session);

        assertThat(cancel.status()).isEqualTo(409);
        assertThat(cancel.code()).isEqualTo("SESSION_HAS_PARTICIPATION");
        assertThat(jdbc.queryForObject("SELECT status FROM class_session WHERE id = ?", String.class, session)).isEqualTo("PUBLISHED");
        assertThat(state(session, attended, regA)).isEqualTo(attendedBefore);
        assertThat(state(session, ordinary, regB)).isEqualTo(ordinaryBefore);
        assertThat(attendedBefore.seats()).isEqualTo(2);
        assertThat(auditCount("SESSION_CANCELLED")).isZero();
    }

    @Test
    void managerCancellationWithoutParticipationStillRefundsOrdinaryAndAbsentBookings() {
        long session = session(LocalTime.of(14, 0));
        long regA = registration(memberA), regB = registration(memberB);
        long absent = bookViaApi(memberA, session, regA);
        long ordinary = bookViaApi(memberB, session, regB);
        assertThat(markAttendance(absent, "ABSENT").status()).isEqualTo(201);

        assertThat(cancelSession(session).status()).isEqualTo(200);

        assertThat(state(session, absent, regA)).extracting(State::bookingStatus, State::remaining, State::seats)
                .containsExactly("CANCELLED", 3, 0);
        assertThat(state(session, ordinary, regB)).extracting(State::bookingStatus, State::remaining)
                .containsExactly("CANCELLED", 3);
        assertThat(auditCount("SESSION_CANCELLED")).isEqualTo(1);
    }

    // ---------------------------------------------------------------- forced race orders (B02-08, B02-09)

    @Test @Timeout(100)
    void attendanceCommitsFirst_cancellationWaitsThenIsRejectedWithoutRestoringCredit() throws Exception {
        long session = session(LocalTime.of(9, 30));
        long reg = registration(memberA);
        long booking = bookedFixture(session, memberA, reg);
        State before = state(session, booking, reg);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Outcome> attendance = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID"));
                sessions.findForUpdate(session).orElseThrow();
                held.countDown(); await(release);
                return markAttendance(booking, "PRESENT");
            }));
            await(held);
            Future<Outcome> cancellation = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID"));
                return cancel(receptionist, booking);
            }));
            awaitBlocked(secondSpid, firstSpid, "class_session");
            release.countDown();

            assertThat(attendance.get(60, TimeUnit.SECONDS).status()).isEqualTo(201);
            Outcome rejected = cancellation.get(60, TimeUnit.SECONDS);
            assertThat(rejected.status()).isEqualTo(400);
            assertThat(rejected.code()).isEqualTo("ALREADY_ATTENDED");
        } finally { release.countDown(); stop(pool); }

        State after = state(session, booking, reg);
        assertThat(after.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(after.attendance()).isEqualTo("PRESENT");
        assertThat(after.remaining()).isEqualTo(before.remaining());
        assertThat(after.seats()).isEqualTo(before.seats());
        assertThat(auditCount("BOOKING_CANCELLED")).isZero();
    }

    @Test @Timeout(100)
    void cancellationCommitsFirst_attendanceWaitsThenIsRejectedAndNoParticipationIsStored() throws Exception {
        long session = session(LocalTime.of(9, 30));
        long reg = registration(memberA);
        long booking = bookedFixture(session, memberA, reg);
        int remainingBefore = balance(reg);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Outcome> cancellation = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID"));
                sessions.findForUpdate(session).orElseThrow();
                held.countDown(); await(release);
                return cancel(receptionist, booking);
            }));
            await(held);
            Future<Outcome> attendance = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID"));
                return markAttendance(booking, "PRESENT");
            }));
            awaitBlocked(secondSpid, firstSpid, "class_session");
            release.countDown();

            assertThat(cancellation.get(60, TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(attendance.get(60, TimeUnit.SECONDS).status()).isEqualTo(400);
        } finally { release.countDown(); stop(pool); }

        State after = state(session, booking, reg);
        assertThat(after.bookingStatus()).isEqualTo("CANCELLED");
        assertThat(after.attendance()).as("no PRESENT/LATE on a cancelled booking").isNull();
        assertThat(after.remaining()).isEqualTo(remainingBefore + 1);
        assertThat(after.seats()).isZero();
    }

    // ---------------------------------------------------------------- preserved policies (E2E-D6, E2E-D9)

    @Test
    void d6_frontDeskCheckInAfterTheSessionEndOnTheSameDateIsStillAllowed() {
        long session = session(LocalTime.of(8, 0)); // ended at 09:30, now 10:00 on the same date
        long reg = registration(memberA);
        long booking = bookedFixture(session, memberA, reg);

        Outcome checkIn = checkIn(memberA, booking);

        assertThat(checkIn.status()).isEqualTo(201);
        assertThat(checkIn.body().path("result").asText()).isEqualTo("ALLOWED");
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private record State(String bookingStatus, int remaining, int seats, String attendance) {}
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
    private String memberCode(UserAccount member) {
        return jdbc.queryForObject("SELECT member_code FROM member_profile WHERE user_id = ?", String.class, member.getId());
    }
    private long registration(UserAccount member) {
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel, original_price,
                    paid_amount, total_sessions, remaining_sessions, start_date, end_date, status, created_by_user_id)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'RECEPTION', 100000, 100000, 3, 3, ?, ?, 'ACTIVE', ?)
                """, Long.class, code(), member.getId(), packageId, TODAY.minusDays(2), TODAY.plusDays(40), manager.getId());
    }
    /** A coach-led session today; the end is 90 minutes after the start. */
    private long session(LocalTime start) {
        return jdbc.queryForObject("""
                SET NOCOUNT ON; DECLARE @created TABLE(id BIGINT);
                INSERT INTO class_session(class_id, sport_id, facility_id, coach_id, training_type, session_date,
                    start_time, end_time, capacity, booked_count, status, session_code) OUTPUT INSERTED.id INTO @created
                VALUES (?, ?, ?, ?, 'COACH_LED', ?, ?, ?, 10, 0, 'PUBLISHED', ?); SELECT id FROM @created;
                """, Long.class, sportClass.getId(), sport.getId(), facilityId, coach.getId(), TODAY, start, start.plusMinutes(90),
                SessionIdentityFixtures.reserve(jdbc, TODAY, "FOOTBALL"));
    }
    /**
     * A consistent CONFIRMED booking for a session that has already started or ended, which the booking API refuses
     * to create: one seat taken and one credit consumed, exactly like a booking made before the start.
     */
    private long bookedFixture(long session, UserAccount member, long registration) {
        long id = jdbc.queryForObject("""
                INSERT INTO booking(booking_code, session_id, member_id, package_registration_id, status, booked_at)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, 'CONFIRMED', ?)
                """, Long.class, code(), session, member.getId(), registration, NOW.minusDays(1));
        jdbc.update("UPDATE class_session SET booked_count = booked_count + 1 WHERE id = ?", session);
        jdbc.update("UPDATE sport_package_registration SET remaining_sessions = remaining_sessions - 1 WHERE id = ?", registration);
        return id;
    }
    private long bookViaApi(UserAccount member, long session, long registration) {
        Outcome created = http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(write(BookingCreateRequest.builder().sessionId(session).packageRegistrationId(registration).build())), member);
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        return created.body().path("id").asLong();
    }
    private Outcome markAttendance(long booking, String status) {
        return http(post("/api/v1/coach/training/attendance").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("bookingId", booking, "status", status))), coach);
    }
    private Outcome correctAttendance(long attendance, String status, String reason) {
        return http(put("/api/v1/coach/training/attendance/" + attendance).contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("status", status, "reason", reason))), coach);
    }
    private Outcome saveResult(long session, UserAccount member) {
        return http(post("/api/v1/coach/training/results").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("sessionId", session, "memberId", member.getId(), "notes", "Completed drills", "scores", List.of()))), coach);
    }
    private Outcome cancel(UserAccount actor, long booking) { return http(delete("/api/v1/bookings/" + booking), actor); }
    private Outcome cancelSession(long session) {
        return http(put("/api/v1/manager/sessions/" + session + "/cancel").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("reason", "Coach unavailable"))), manager);
    }
    private Outcome checkIn(UserAccount member, long booking) {
        return http(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("identifier", memberCode(member), "bookingId", booking))), receptionist);
    }
    private State state(long session, long booking, long registration) {
        var attendance = jdbc.queryForList("""
                SELECT a.status FROM attendance_record a JOIN booking b ON b.session_id = a.session_id AND b.member_id = a.member_id
                WHERE b.id = ?""", String.class, booking);
        return new State(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking),
                balance(registration), count("SELECT booked_count FROM class_session WHERE id = ?", session),
                attendance.isEmpty() ? null : attendance.getFirst());
    }
    private int balance(long registration) { return count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration); }
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
    /** Proves the race order: the second SQL Server connection really waits on the first one's session lock. */
    private void awaitBlocked(AtomicInteger waiting, AtomicInteger blocking, String sqlFragment) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (waiting.get() > 0 && blocking.get() > 0 && count("""
                    SELECT COUNT(*) FROM sys.dm_exec_requests r CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
                    WHERE r.session_id = ? AND r.blocking_session_id = ? AND t.text LIKE ?
                    """, waiting.get(), blocking.get(), "%" + sqlFragment + "%") > 0) return;
        }
        fail("Expected the second SQL Server connection to wait for the first; observed "
                + jdbc.queryForList("SELECT session_id, blocking_session_id, wait_type FROM sys.dm_exec_requests WHERE session_id = ?", waiting.get()));
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
