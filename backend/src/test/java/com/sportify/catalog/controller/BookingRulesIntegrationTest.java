package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.*;
import com.sportify.core.audit.*;
import com.sportify.core.audit.impl.AuditServiceImpl;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.*;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real SQL Server transactions and HTTP security; compiled locally, executed only by Docker-capable CI. */
@AutoConfigureMockMvc
@Import(BookingRulesIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BookingRulesIntegrationTest extends AbstractIntegrationTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    static final SecureRandom RANDOM = new SecureRandom();
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock bookingRulesClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired BookingService service;
    @Autowired MemberScheduleService schedule;
    @Autowired ManagerSessionService managerSessions;
    @Autowired BookingRepository bookings;
    @Autowired ClassSessionRepository sessions;
    @Autowired SportPackageRegistrationRepository registrations;
    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired FacilityRepository facilities;
    @Autowired SportClassRepository classes;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoSpyBean AuditServiceImpl audit;
    @MockitoSpyBean CodeFormatter codes;
    private final String marker = "be12" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private final List<ExecutorService> workerPools = new ArrayList<>();
    private Sport sport;
    private SportClass sportClass;
    private long facilityId;
    private long coachId;
    private long coachedPackageId;
    private long selfPackageId;
    private UserAccount memberA;
    private UserAccount memberB;
    private UserAccount manager;
    private UserAccount receptionist;

    @BeforeEach void fixture() {
        sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        manager = account("MANAGER"); receptionist = account("RECEPTIONIST");
        memberA = member(); memberB = member();
        coachId = account("COACH").getId();
        jdbc.update("INSERT INTO coach_profile(user_id, primary_sport_id, is_public, display_order) VALUES (?, ?, 1, 0)", coachId, sport.getId());
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", coachId, sport.getId());
        facilityId = facilities.saveAndFlush(Facility.builder().code(code()).name("BE-12 court").sport(sport)
                .facilityType(FacilityType.COURT).isActive(true).build()).getId();
        sportClass = classes.saveAndFlush(SportClass.builder().code(code()).name("BE-12 class").sport(sport).maxMembers(40).isActive(true).build());
        coachedPackageId = newPackage(TrainingFormat.COACH_LED);
        selfPackageId = newPackage(TrainingFormat.SELF_TRAINING);
    }

    @AfterEach void cleanup() {
        // Fail closed before deleting fixtures if a writer did not terminate.
        assertThat(workerPools).allMatch(ExecutorService::isTerminated, "all concurrent writers stopped before cleanup");
        // UUID-scoped rows only. Every concurrent writer is stopped in its test's finally block first.
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ")", marker + "%");
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
    private long newPackage(TrainingFormat format) {
        return packages.saveAndFlush(SportPackage.builder().code(code()).name("BE-12 " + format).sport(sport)
                .trainingFormat(format).durationDays(60).sessionCount(10).sessionMinutes(90)
                .priceAmount(new BigDecimal("100000")).isActive(true).build()).getId();
    }
    private long registration(UserAccount member, int total, int remaining, String status, TrainingFormat format) {
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel, original_price,
                    paid_amount, total_sessions, remaining_sessions, start_date, end_date, status, created_by_user_id)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'RECEPTION', 100000, 100000, ?, ?, ?, ?, ?, ?)
                """, Long.class, code(), member.getId(), format == TrainingFormat.COACH_LED ? coachedPackageId : selfPackageId,
                total, remaining, TODAY.minusDays(2), TODAY.plusDays(40), status, manager.getId());
    }
    private long registration(UserAccount member) { return registration(member, 3, 3, "ACTIVE", TrainingFormat.COACH_LED); }
    private long session(LocalDate date, LocalTime start, int capacity, TrainingFormat format) {
        return jdbc.queryForObject("""
                INSERT INTO class_session(class_id, sport_id, facility_id, coach_id, training_type, session_date,
                    start_time, end_time, capacity, booked_count, status) OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'PUBLISHED')
                """, Long.class, format == TrainingFormat.COACH_LED ? sportClass.getId() : null, sport.getId(), facilityId,
                format == TrainingFormat.COACH_LED ? coachId : null, format.name(), date, start, start.plusMinutes(90), capacity);
    }
    private long session() { return session(TODAY.plusDays(1), LocalTime.NOON, 10, TrainingFormat.COACH_LED); }
    private long legacyBooking(long session, UserAccount member, long registration, String status) {
        return jdbc.queryForObject("""
                INSERT INTO booking(booking_code, session_id, member_id, package_registration_id, status, booked_at)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?, ?)
                """, Long.class, code(), session, member.getId(), registration, status, NOW);
    }
    private BookingCreateRequest request(long session, long registration) {
        return BookingCreateRequest.builder().sessionId(session).packageRegistrationId(registration).build();
    }
    private int count(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args)); }
    private int balance(long registration) { return count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registration); }
    private int seats(long session) { return count("SELECT booked_count FROM class_session WHERE id = ?", session); }
    private long auditCount(String action) {
        return count("SELECT COUNT(*) FROM activity_log WHERE action = ? AND actor_user_id IN (SELECT id FROM user_account WHERE email LIKE ?)", action, marker + "%");
    }
    private record Outcome(int status, JsonNode body) {
        String code() { return body.path("code").asText(null); }
    }
    private Outcome http(MockHttpServletRequestBuilder request, UserAccount actor) {
        try {
            var response = mvc.perform(request.with(user(new CustomUserDetails(actor)))).andReturn().getResponse();
            return new Outcome(response.getStatus(), json.readTree(response.getContentAsString()));
        } catch (Exception error) { throw new AssertionError(error); }
    }
    private Outcome postBooking(UserAccount actor, long session, long registration) {
        try { return http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request(session, registration))), actor); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private TransactionTemplate writer() {
        var template = new TransactionTemplate(transactions); template.setTimeout(60); return template;
    }
    private Outcome inWriter(java.util.function.Supplier<Outcome> work) {
        return writer().execute(status -> {
            Outcome outcome = work.get();
            // The HTTP advice catches service errors; explicitly end the enclosing test transaction as rollback.
            if (outcome.status() >= 400) status.setRollbackOnly();
            return outcome;
        });
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(30, TimeUnit.SECONDS)).as("bounded race coordination").isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    private void awaitBlocked(int waiting, int blocking, String sqlFragment) {
        assertThat(waiting).isNotEqualTo(blocking).isPositive();
        assertThat(count("SELECT IS_SRVROLEMEMBER('sysadmin')")).isEqualTo(1);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        do {
            if (count("""
                    SELECT COUNT(*) FROM sys.dm_exec_requests r CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
                    WHERE r.session_id = ? AND r.blocking_session_id = ? AND t.text LIKE ?
                    """, waiting, blocking, "%" + sqlFragment + "%") > 0) return;
        } while (System.nanoTime() < deadline);
        fail("Expected the exact second SQL Server connection to wait for the first; observed "
                + jdbc.queryForList("SELECT session_id, blocking_session_id, wait_type FROM sys.dm_exec_requests WHERE session_id = ?", waiting));
    }
    private ExecutorService newWorkerPool() {
        var pool = Executors.newFixedThreadPool(2);
        workerPools.add(pool);
        return pool;
    }

    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(35, TimeUnit.SECONDS)) {
            pool.shutdownNow(); assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test @Timeout(100)
    void twoMembersCompeteForLastSeat_onlyOne201AndOne409WithoutOrphansOrExtraDebit() throws Exception {
        long session = session(TODAY.plusDays(1), LocalTime.NOON, 1, TrainingFormat.COACH_LED);
        long regA = registration(memberA), regB = registration(memberB);
        var held = new CountDownLatch(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Outcome> first = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID")); sessions.findForUpdate(session).orElseThrow(); held.countDown(); await(release);
                return postBooking(memberA, session, regA);
            }));
            await(held);
            Future<Outcome> second = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID")); entered.countDown(); return postBooking(memberB, session, regB);
            }));
            await(entered); awaitBlocked(secondSpid.get(), firstSpid.get(), "class_session"); release.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).status()).isEqualTo(201);
            var loser = second.get(30, TimeUnit.SECONDS); assertThat(loser.status()).isEqualTo(409); assertThat(loser.code()).isEqualTo("FULL");
            assertThat(seats(session)).isEqualTo(1);
            assertThat(balance(regA)).isEqualTo(2); assertThat(balance(regB)).isEqualTo(3);
            assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ?", session)).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CONFIRMED' AND package_registration_id = ?", session, regA)).isEqualTo(1);
            assertThat(auditCount(AuditAction.BOOKING_CREATED)).isEqualTo(1);
        } finally { release.countDown(); stop(pool); }
    }

    @Test @Timeout(100)
    void differentSessionsShareLastCredit_onlyOne201AndFailedRequestRollsBackReservedSeat() throws Exception {
        long firstSession = session(), secondSession = session(TODAY.plusDays(2), LocalTime.NOON, 10, TrainingFormat.COACH_LED);
        long reg = registration(memberA, 1, 1, "ACTIVE", TrainingFormat.COACH_LED);
        var snapshots = new CountDownLatch(2); var held = new CountDownLatch(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger(); var pool = newWorkerPool();
        try {
            Future<Outcome> first = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID")); assertThat(registrations.findById(reg).orElseThrow().getRemainingSessions()).isEqualTo(1);
                snapshots.countDown(); await(snapshots); sessions.findForUpdate(firstSession).orElseThrow();
                jdbc.update("UPDATE sport_package_registration SET remaining_sessions = remaining_sessions WHERE id = ?", reg);
                held.countDown(); await(release); return postBooking(memberA, firstSession, reg);
            }));
            Future<Outcome> second = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID")); assertThat(registrations.findById(reg).orElseThrow().getRemainingSessions()).isEqualTo(1);
                snapshots.countDown(); await(held); entered.countDown(); return postBooking(memberA, secondSession, reg);
            }));
            await(held); await(entered); awaitBlocked(secondSpid.get(), firstSpid.get(), "UPDATE sport_package_registration"); release.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).status()).isEqualTo(201);
            var loser = second.get(30, TimeUnit.SECONDS); assertThat(loser.status()).isEqualTo(400); assertThat(loser.code()).isEqualTo("NO_SESSIONS_LEFT");
            assertThat(balance(reg)).isZero(); assertThat(seats(firstSession)).isEqualTo(1); assertThat(seats(secondSession)).isZero();
            assertThat(count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ? AND status = 'CONFIRMED'", reg)).isEqualTo(1);
            assertThat(auditCount(AuditAction.BOOKING_CREATED)).isEqualTo(1);
        } finally { release.countDown(); stop(pool); }
    }

    @Test @Timeout(100)
    void simultaneousCancellationWithPreloadedBooking_restoresExactlyOnceAndPersistsActor() throws Exception {
        long session = session(), reg = registration(memberA);
        long booking = service.createBooking(request(session, reg), memberA).getId();
        var snapshots = new CountDownLatch(2); var held = new CountDownLatch(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger(); var pool = newWorkerPool();
        try {
            Future<Outcome> first = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID")); bookings.findById(booking).orElseThrow(); snapshots.countDown(); await(snapshots);
                sessions.findForUpdate(session).orElseThrow(); held.countDown(); await(release);
                return http(delete("/api/v1/bookings/" + booking), memberA);
            }));
            Future<Outcome> second = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID")); bookings.findById(booking).orElseThrow(); snapshots.countDown(); await(held); entered.countDown();
                return http(delete("/api/v1/bookings/" + booking), memberA);
            }));
            await(held); await(entered); awaitBlocked(secondSpid.get(), firstSpid.get(), "class_session"); release.countDown();
            assertThat(first.get(30, TimeUnit.SECONDS).status()).isEqualTo(200);
            var loser = second.get(30, TimeUnit.SECONDS); assertThat(loser.status()).isEqualTo(400); assertThat(loser.code()).isEqualTo("ALREADY_CANCELLED");
            assertThat(seats(session)).isZero(); assertThat(balance(reg)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT cancelled_by_user_id FROM booking WHERE id = ?", Long.class, booking)).isEqualTo(memberA.getId());
            assertThat(jdbc.queryForObject("SELECT cancelled_at FROM booking WHERE id = ?", Timestamp.class, booking).toLocalDateTime()).isEqualTo(NOW);
            assertThat(auditCount(AuditAction.BOOKING_CANCELLED)).isEqualTo(1);
        } finally { release.countDown(); stop(pool); }
    }

    @ParameterizedTest @CsvSource({"MEMBER,121,200", "MEMBER,120,200", "MEMBER,119,400", "RECEPTIONIST,119,200", "MANAGER,119,200"})
    void cutoffBoundaryAndStaffException_restoreOnlyOnSuccessfulCancellation(String role, int minutes, int expected) {
        long session = session(TODAY, NOW.toLocalTime().plusMinutes(minutes), 10, TrainingFormat.COACH_LED), reg = registration(memberA);
        long booking = service.createBooking(request(session, reg), memberA).getId();
        UserAccount caller = role.equals("MEMBER") ? memberA : role.equals("MANAGER") ? manager : receptionist;
        var result = http(delete("/api/v1/bookings/" + booking), caller);
        assertThat(result.status()).isEqualTo(expected);
        assertThat(seats(session)).isEqualTo(expected == 200 ? 0 : 1);
        assertThat(balance(reg)).isEqualTo(expected == 200 ? 3 : 2);
        if (expected == 400) assertThat(result.code()).isEqualTo("CANCELLATION_CUTOFF");
        else assertThat(result.body().path("cancelledById").asLong()).isEqualTo(caller.getId());
    }

    @ParameterizedTest @ValueSource(strings = {"ACTIVE", "CAPPED", "PENDING_PAYMENT", "EXPIRED", "CANCELLED", "REFUNDED"})
    void cancellationKeepsBe10AndBe21ActiveOnlyCappedCreditPolicy(String scenario) {
        long session = session();
        String status = scenario.equals("CAPPED") ? "ACTIVE" : scenario;
        int before = scenario.equals("CAPPED") ? 3 : 2;
        long reg = registration(memberA, 3, before, status, TrainingFormat.COACH_LED);
        long booking = legacyBooking(session, memberA, reg, "CONFIRMED");
        jdbc.update("UPDATE class_session SET booked_count = 1 WHERE id = ?", session);
        assertThat(service.cancelBooking(booking, memberA).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(balance(reg)).isEqualTo(status.equals("ACTIVE") ? 3 : before);
        assertThat(jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE id = ?", String.class, reg)).isEqualTo(status);
        assertThatThrownBy(() -> service.cancelBooking(booking, memberA)).isInstanceOf(BusinessRuleException.class);
        assertThat(seats(session)).isZero(); assertThat(balance(reg)).isLessThanOrEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"BOOKING_CREATED", "BOOKING_CANCELLED"})
    void auditFailureAfterRealAuditInsert_rollsBackBookingSeatCreditAndSuccessAudit(String action) {
        long session = session(), reg = registration(memberA);
        Long booking = action.equals(AuditAction.BOOKING_CANCELLED) ? service.createBooking(request(session, reg), memberA).getId() : null;
        doAnswer(invocation -> {
            invocation.callRealMethod();
            AuditEvent event = invocation.getArgument(0);
            if (event.getAction().equals(action)) throw new IllegalStateException("Forced audit failure after real insert");
            return null;
        }).when(audit).record(any(AuditEvent.class));
        if (booking == null) {
            assertThatThrownBy(() -> service.createBooking(request(session, reg), memberA)).isInstanceOf(IllegalStateException.class);
            assertThat(seats(session)).isZero(); assertThat(balance(reg)).isEqualTo(3);
            assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ?", session)).isZero();
        } else {
            assertThatThrownBy(() -> service.cancelBooking(booking, memberA)).isInstanceOf(IllegalStateException.class);
            assertThat(seats(session)).isEqualTo(1); assertThat(balance(reg)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking)).isEqualTo("CONFIRMED");
            assertThat(count("SELECT COUNT(*) FROM booking WHERE id = ? AND cancelled_at IS NOT NULL", booking)).isZero();
        }
        assertThat(auditCount(action)).isZero();
    }

    @Test void realPersistenceFailureRollsBackReservationDebitAndDoesNotBecomeDuplicateMemberConflict() {
        long session = session(), regA = registration(memberA), regB = registration(memberB);
        var first = service.createBooking(request(session, regA), memberA);
        doReturn(first.getBookingCode()).when(codes).formatBookingCode(anyLong());
        assertThatThrownBy(() -> service.createBooking(request(session, regB), memberB)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(seats(session)).isEqualTo(1); assertThat(balance(regB)).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM booking WHERE session_id = ?", session)).isEqualTo(1);
        assertThat(auditCount(AuditAction.BOOKING_CREATED)).isEqualTo(1);
    }

    @Test void selfTrainingNullCoachAndPackageMetadataSurviveBookingCancellationAndHistory() {
        long session = session(TODAY.plusDays(1), LocalTime.NOON, 10, TrainingFormat.SELF_TRAINING);
        long reg = registration(memberA, 3, 3, "ACTIVE", TrainingFormat.SELF_TRAINING);
        var created = postBooking(memberA, session, reg); assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().path("coachId").isNull()).isTrue(); assertThat(created.body().path("classId").isNull()).isTrue();
        long booking = created.body().path("id").asLong();
        assertThat(http(delete("/api/v1/bookings/" + booking), memberA).status()).isEqualTo(200);
        var page = http(get("/api/v1/bookings/my?when=PAST&page=0&size=20"), memberA);
        assertThat(page.body().path("content").size()).isEqualTo(1);
        assertThat(page.body().path("content").get(0).path("coachName").isNull()).isTrue();
        assertThat(page.body().path("content").get(0).path("packageName").asText()).isEqualTo("BE-12 SELF_TRAINING");
        assertThat(balance(reg)).isEqualTo(3); assertThat(seats(session)).isZero();
    }

    @Test void duplicateAndSelectedExhaustedPackageCannotBeAuthorizedByAnotherEligibleRegistration() {
        long session = session(), exhausted = registration(memberA, 3, 0, "ACTIVE", TrainingFormat.COACH_LED), usable = registration(memberA);
        assertThat(schedule.getSession(session, memberA).eligible()).isTrue();
        var rejected = postBooking(memberA, session, exhausted); assertThat(rejected.status()).isEqualTo(400); assertThat(rejected.code()).isEqualTo("NO_SESSIONS_LEFT");
        assertThat(balance(usable)).isEqualTo(3); assertThat(seats(session)).isZero();
        assertThat(postBooking(memberA, session, usable).status()).isEqualTo(201);
        var duplicate = postBooking(memberA, session, usable); assertThat(duplicate.status()).isEqualTo(409); assertThat(duplicate.code()).isEqualTo("ALREADY_BOOKED");
        assertThat(balance(usable)).isEqualTo(2);
    }

    @Test void foreignRegistrationAndCancellationAre403AndClientMemberIdNeverChangesOwnership() throws Exception {
        long session = session(), regA = registration(memberA), regB = registration(memberB);
        var foreign = postBooking(memberA, session, regB);
        assertThat(foreign.status()).isEqualTo(403); assertThat(seats(session)).isZero(); assertThat(balance(regB)).isEqualTo(3);
        var spoofed = request(session, regA); spoofed.setMemberId(memberB.getId());
        var created = http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(spoofed)), memberA);
        assertThat(created.status()).isEqualTo(201); assertThat(created.body().path("memberId").asLong()).isEqualTo(memberA.getId());
        long booking = created.body().path("id").asLong();
        assertThat(http(delete("/api/v1/bookings/" + booking), memberB).status()).isEqualTo(403);
        assertThat(seats(session)).isEqualTo(1); assertThat(balance(regA)).isEqualTo(2);
        assertThat(http(get("/api/v1/bookings/my?page=0"), memberB).body().path("totalElements").asInt()).isZero();
    }

    @ParameterizedTest @ValueSource(strings = {"FACILITY", "CLASS", "COACH", "QUALIFICATION", "COACHLESS"})
    void bookingWriteRechecksActualBe11AssignmentVisibility(String unavailable) {
        long session = session(), reg = registration(memberA);
        switch (unavailable) {
            case "FACILITY" -> jdbc.update("UPDATE facility SET is_active = 0 WHERE id = ?", facilityId);
            case "CLASS" -> jdbc.update("UPDATE sport_class SET is_active = 0 WHERE id = ?", sportClass.getId());
            case "COACH" -> jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE id = ?", coachId);
            case "QUALIFICATION" -> jdbc.update("DELETE FROM coach_sport WHERE coach_id = ?", coachId);
            default -> jdbc.update("UPDATE class_session SET coach_id = NULL WHERE id = ?", session);
        }
        var result = postBooking(memberA, session, reg); assertThat(result.status()).isEqualTo(400); assertThat(result.code()).isEqualTo("SESSION_UNAVAILABLE");
        assertThat(seats(session)).isZero(); assertThat(balance(reg)).isEqualTo(3); assertThat(auditCount(AuditAction.BOOKING_CREATED)).isZero();
    }

    @Test void historyFiltersActualClockAndRetainsFutureCancelledAndTerminalRowsWithoutCrossMemberLeakage() {
        long reg = registration(memberA), otherReg = registration(memberB);
        long past = legacyBooking(session(TODAY.minusDays(1), LocalTime.NOON, 10, TrainingFormat.COACH_LED), memberA, reg, "CONFIRMED");
        long atStart = legacyBooking(session(TODAY, NOW.toLocalTime(), 10, TrainingFormat.COACH_LED), memberA, reg, "CONFIRMED");
        long future = legacyBooking(session(), memberA, reg, "CONFIRMED");
        long cancelled = legacyBooking(session(), memberA, reg, "CANCELLED");
        long completedSession = session(); jdbc.update("UPDATE class_session SET status = 'COMPLETED' WHERE id = ?", completedSession);
        long completed = legacyBooking(completedSession, memberA, reg, "CONFIRMED");
        long draftSession = session(); jdbc.update("UPDATE class_session SET status = 'DRAFT' WHERE id = ?", draftSession);
        long draft = legacyBooking(draftSession, memberA, reg, "CONFIRMED");
        long foreign = legacyBooking(session(), memberB, otherReg, "CONFIRMED");
        var upcoming = service.getMyBookings(memberA, BookingWhen.UPCOMING, 0, 100);
        var history = service.getMyBookings(memberA, BookingWhen.PAST, 0, 100);
        assertThat(upcoming.content()).extracting(BookingResponse::getId).containsExactly(future, draft);
        assertThat(history.content()).extracting(BookingResponse::getId).containsExactlyInAnyOrder(past, atStart, cancelled, completed).doesNotContain(foreign);
        var injected = http(get("/api/v1/bookings/my?when=UPCOMING&page=0&memberId=" + memberB.getId()), memberA);
        assertThat(injected.body().path("totalElements").asInt()).isEqualTo(2);
        var legacy = http(get("/api/v1/bookings/my?when=UPCOMING"), memberA);
        assertThat(legacy.body().isArray()).isTrue(); assertThat(legacy.body().size()).isEqualTo(2);
    }

    @Test void sqlPaginationHasStableIdTiesCorrectCountAndTwoQueriesWithoutNPlusOne() {
        long reg = registration(memberA); var ids = new ArrayList<Long>();
        for (int i = 0; i < 25; i++) ids.add(legacyBooking(session(), memberA, reg, "CONFIRMED"));
        var stats = emf.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var first = service.getMyBookings(memberA, BookingWhen.UPCOMING, 0, 10);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
        var second = service.getMyBookings(memberA, BookingWhen.UPCOMING, 1, 10);
        var third = service.getMyBookings(memberA, BookingWhen.UPCOMING, 2, 10);
        assertThat(first.totalElements()).isEqualTo(25); assertThat(first.totalPages()).isEqualTo(3);
        var all = new ArrayList<Long>();
        for (var page : List.of(first, second, third)) page.content().forEach(b -> all.add(b.getId()));
        assertThat(all).containsExactlyElementsOf(ids).doesNotHaveDuplicates();
        assertThat(service.getMyBookings(memberB, BookingWhen.UPCOMING, 0, 10).content()).isEmpty();
    }

    @Test void actualAtomicSeatCommandAndRefreshedManagedEntityCannotOverwriteItsCounter() {
        long session = session(TODAY.plusDays(1), LocalTime.NOON, 1, TrainingFormat.COACH_LED), reg = registration(memberA);
        writer().executeWithoutResult(status -> {
            var managed = sessions.findById(session).orElseThrow();
            service.createBooking(request(session, reg), memberA);
            assertThat(managed.getBookedCount()).isEqualTo(1);
            managed.setCancelReason("Unrelated metadata update"); sessions.saveAndFlush(managed);
            assertThat(sessions.reserveSeat(session, NOW)).isZero();
            assertThat(seats(session)).isEqualTo(1);
        });
        assertThat(seats(session)).isEqualTo(1); assertThat(balance(reg)).isEqualTo(2);
    }

    @Test void managerCancellationAndLaterIndividualCancellationDoNotRestoreRefundedCreditTwice() {
        long session = session(), reg = registration(memberA);
        long booking = service.createBooking(request(session, reg), memberA).getId();
        managerSessions.cancelSession(session, new SessionCancelRequest("BE-12 manager cancellation"), manager);
        assertThat(balance(reg)).isEqualTo(3); assertThat(seats(session)).isZero();
        assertThatThrownBy(() -> service.cancelBooking(booking, memberA)).isInstanceOf(BusinessRuleException.class).hasMessage("Booking is already cancelled");
        assertThat(balance(reg)).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings = {"CHECKIN_FIRST", "CANCEL_FIRST"}) @Timeout(100)
    void cancellationAndCheckInSerializeWithoutAllowedCheckInOnCancelledBooking(String order) throws Exception {
        long session = session(TODAY, LocalTime.NOON, 10, TrainingFormat.COACH_LED), reg = registration(memberA);
        long booking = service.createBooking(request(session, reg), memberA).getId();
        var held = new CountDownLatch(1); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger(); var pool = newWorkerPool();
        java.util.function.Supplier<Outcome> cancel = () -> http(delete("/api/v1/bookings/" + booking), memberA);
        java.util.function.Supplier<Outcome> checkIn = () -> http(post("/api/v1/check-ins").contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + memberA.getId() + "\",\"bookingId\":" + booking + "}"), receptionist);
        try {
            Future<Outcome> first = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID")); sessions.findForUpdate(session).orElseThrow(); held.countDown(); await(release);
                return order.equals("CHECKIN_FIRST") ? checkIn.get() : cancel.get();
            }));
            await(held);
            Future<Outcome> second = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID")); entered.countDown();
                return order.equals("CHECKIN_FIRST") ? cancel.get() : checkIn.get();
            }));
            await(entered); awaitBlocked(secondSpid.get(), firstSpid.get(), ""); release.countDown();
            var a = first.get(30, TimeUnit.SECONDS); var b = second.get(30, TimeUnit.SECONDS);
            assertThat(a.status()).isEqualTo(order.equals("CHECKIN_FIRST") ? 201 : 200);
            if (order.equals("CHECKIN_FIRST")) {
                assertThat(b.status()).isEqualTo(400); assertThat(b.code()).isEqualTo("ALREADY_CHECKED_IN");
                assertThat(seats(session)).isEqualTo(1); assertThat(balance(reg)).isEqualTo(2);
            } else {
                assertThat(b.status()).isEqualTo(201); assertThat(b.body().path("result").asText()).isEqualTo("DENIED");
                assertThat(seats(session)).isZero(); assertThat(balance(reg)).isEqualTo(3);
            }
            assertThat(count("SELECT COUNT(*) FROM check_in c JOIN booking b ON b.id = c.booking_id WHERE b.id = ? AND b.status = 'CANCELLED' AND c.result = 'ALLOWED'", booking)).isZero();
        } finally { release.countDown(); stop(pool); }
    }
}
