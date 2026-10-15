package com.sportify.catalog.controller;

import com.sportify.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.exception.ScheduleConflictException;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.ManagerSessionService;
import com.sportify.catalog.service.BookingService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SQL Server coverage. Registered for CI; requires Docker and is not executed locally. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ManagerSessionIntegrationTest extends AbstractIntegrationTest {
    @Autowired private ManagerSessionService service;
    @Autowired private BookingService bookingService;
    @Autowired private ClassSessionRepository sessions;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private SportPackageRegistrationRepository registrations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SportClassRepository classes;
    @Autowired private SportRepository sports;
    @Autowired private FacilityRepository facilities;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Clock clock;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper objectMapper;
    private final String marker = "be10it" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private Sport sport;
    private SportClass sportClass;
    private UserAccount manager;
    private long coachId;
    private long facilityId;
    private LocalDate date;

    @BeforeEach
    void setUp() {
        date = LocalDate.now(clock).plusDays(7).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        sport = sports.findAll().stream().filter(row -> Boolean.TRUE.equals(row.getIsActive())).findFirst().orElseThrow();
        manager = account("MANAGER");
        coachId = coach();
        facilityId = facility();
        sportClass = newClass();
    }

    @AfterEach
    void cleanUp() {
        String userIds = "SELECT id FROM user_account WHERE email LIKE ?";
        String classIds = "SELECT id FROM sport_class WHERE code LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + userIds + ")", marker + "%");
        jdbc.update("DELETE FROM booking WHERE session_id IN (SELECT id FROM class_session WHERE class_id IN (" + classIds + "))", marker + "%");
        jdbc.update("DELETE FROM class_session WHERE class_id IN (" + classIds + ")", marker + "%");
        jdbc.update("DELETE FROM sport_class WHERE code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM sport_package_registration WHERE registration_code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM facility WHERE code LIKE ?", marker + "%");
        jdbc.update("DELETE FROM coach_sport WHERE coach_id IN (" + userIds + ")", marker + "%");
        jdbc.update("DELETE FROM coach_profile WHERE user_id IN (" + userIds + ")", marker + "%");
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + userIds + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
    }

    private String code() {
        return marker + ++serial;
    }

    private UserAccount account(String role) {
        String name = code();
        // UUID-derived numeric phones avoid collisions with seeded and other suite accounts.
        String phone = Long.toString(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
        return users.saveAndFlush(UserAccount.builder().fullName(name).email(name + "@sportify.test").phone(phone)
                .passwordHash("test-password-hash").status("ACTIVE").role(roles.findByCode(role).orElseThrow()).build());
    }

    private long coach() {
        long id = account("COACH").getId();
        jdbc.update("INSERT INTO coach_profile(user_id, primary_sport_id, is_public, display_order) VALUES (?, ?, 1, 0)", id, sport.getId());
        jdbc.update("INSERT INTO coach_sport(coach_id, sport_id) VALUES (?, ?)", id, sport.getId());
        return id;
    }

    private long facility() {
        return facilities.saveAndFlush(Facility.builder().code(code()).name("Test teaching area")
                .facilityType(FacilityType.COURT).sport(sport).isActive(true).build()).getId();
    }

    private SportClass newClass() {
        return classes.saveAndFlush(SportClass.builder().code(code()).name("Session integration class")
                .sport(sport).level("BEGINNER").maxMembers(20).isActive(true).build());
    }

    private long member() {
        long id = account("MEMBER").getId();
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                id, code(), LocalDate.now(clock));
        return id;
    }

    private long registration(long memberId, int total, int remaining, String status) {
        Long packageId = jdbc.queryForObject("""
                SELECT TOP (1) id FROM sport_package
                WHERE sport_id = ? AND training_format = 'COACH_LED' ORDER BY id
                """, Long.class, sport.getId());
        return jdbc.queryForObject("""
                INSERT INTO sport_package_registration(registration_code, member_id, package_id, channel,
                    original_price, paid_amount, total_sessions, remaining_sessions, start_date, end_date, status, created_by_user_id)
                OUTPUT INSERTED.id VALUES (?, ?, ?, 'RECEPTION', 100000, 100000, ?, ?, ?, ?, ?, ?)
                """, Long.class, code(), memberId, packageId, total, remaining, date.minusDays(10), date.plusDays(30), status, manager.getId());
    }

    private long booking(long sessionId, long memberId, Long registrationId, String status) {
        return jdbc.queryForObject("""
                INSERT INTO booking(booking_code, session_id, member_id, package_registration_id, status)
                OUTPUT INSERTED.id VALUES (?, ?, ?, ?, ?)
                """, Long.class, code(), sessionId, memberId, registrationId, status);
    }

    private long insertSession(LocalDate day, LocalTime start, LocalTime end, Long coach, Long facility, String status) {
        return jdbc.queryForObject("""
                INSERT INTO class_session(class_id, sport_id, session_date, start_time, end_time, coach_id, facility_id,
                    training_type, capacity, booked_count, status) OUTPUT INSERTED.id
                VALUES (?, ?, ?, ?, ?, ?, ?, 'COACH_LED', 16, 0, ?)
                """, Long.class, sportClass.getId(), sport.getId(), day, start, end, coach, facility, status);
    }

    private SessionCreateRequest single(LocalDate day, LocalTime start, LocalTime end, long coach, long facility) {
        return new SessionCreateRequest(day, null, null, null, start, end, facility, coach, 16, TrainingFormat.COACH_LED, null);
    }

    private SessionCreateRequest single() {
        return single(date, LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId);
    }

    private int count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 1})
    void sharedRegistration_parallelBookingsOnDifferentSessionsConsumeEachAvailableUnitExactlyOnce(int available) throws Exception {
        long firstId = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long secondId = service.createSessions(sportClass.getId(), single(date.plusDays(1), LocalTime.of(10, 0),
                LocalTime.of(11, 0), coachId, facilityId), manager).getFirst().id();
        long memberId = member();
        long registrationId = registration(memberId, available, available, "ACTIVE");
        var firstRequest = packageBooking(firstId, memberId, registrationId);
        var secondRequest = packageBooking(secondId, memberId, registrationId);
        CountDownLatch snapshotsRead = new CountDownLatch(2);
        CountDownLatch registrationLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger firstSpid = new AtomicInteger();
        AtomicInteger secondSpid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> writerTransaction().execute(status -> {
                firstSpid.set(count("SELECT @@SPID"));
                assertEquals(available, registrations.findById(registrationId).orElseThrow().getRemainingSessions());
                snapshotsRead.countDown();
                await(snapshotsRead);
                sessions.findForUpdate(firstId).orElseThrow();
                holdRegistrationForRace(registrationId);
                registrationLocked.countDown();
                await(release);
                return bookingService.createBooking(firstRequest, manager);
            }));
            Future<?> second = executor.submit(() -> writerTransaction().execute(status -> {
                secondSpid.set(count("SELECT @@SPID"));
                assertEquals(available, registrations.findById(registrationId).orElseThrow().getRemainingSessions());
                snapshotsRead.countDown();
                await(registrationLocked);
                return bookingService.createBooking(secondRequest, manager);
            }));
            assertTrue(registrationLocked.await(20, TimeUnit.SECONDS), "Both snapshots precede the shared registration lock");
            awaitRegistrationUpdateBlocked(secondSpid.get(), firstSpid.get());
            release.countDown();
            assertNotNull(first.get(20, TimeUnit.SECONDS));
            if (available == 2) {
                assertNotNull(second.get(20, TimeUnit.SECONDS));
            } else {
                var error = assertThrows(ExecutionException.class, () -> second.get(20, TimeUnit.SECONDS));
                assertEquals("Selected package registration has zero remaining sessions",
                        assertInstanceOf(BusinessRuleException.class, error.getCause()).getMessage());
            }
            assertEquals(0, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
            assertEquals(available, count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ? AND status = 'CONFIRMED'", registrationId));
            assertEquals(1, count("SELECT booked_count FROM class_session WHERE id = ?", firstId));
            assertEquals(available - 1, count("SELECT booked_count FROM class_session WHERE id = ?", secondId));
            assertEquals(available, count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ?", registrationId));
        } finally {
            release.countDown();
            stopWriters(executor);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "CAPPED", "PENDING_PAYMENT", "EXPIRED", "CANCELLED", "REFUNDED"})
    void individualCancellation_restoresAtMostOneActiveUnitWithinCapAndRejectsDuplicateRefund(String scenario) {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long memberId = member();
        int remaining = scenario.equals("CAPPED") ? 2 : 1;
        String status = scenario.equals("CAPPED") ? "ACTIVE" : scenario;
        long registrationId = registration(memberId, 2, remaining, status);
        long bookingId = booking(id, memberId, registrationId, "CONFIRMED");
        jdbc.update("UPDATE class_session SET booked_count = 1 WHERE id = ?", id);

        assertEquals(BookingStatus.CANCELLED, bookingService.cancelBooking(bookingId, manager).getStatus());

        int expected = status.equals("ACTIVE") ? 2 : 1;
        assertEquals(expected, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        assertEquals(0, count("SELECT booked_count FROM class_session WHERE id = ?", id));
        var error = assertThrows(BusinessRuleException.class, () -> bookingService.cancelBooking(bookingId, manager));
        assertEquals("Booking is already cancelled", error.getMessage());
        assertEquals(expected, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        assertEquals(status, jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE id = ?", String.class, registrationId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BOOKING", "SESSION"})
    void sharedRegistration_consumptionWaitsForIndividualOrManagerRefundWithoutLosingEitherChange(String refundType) throws Exception {
        long refundSessionId = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long newSessionId = service.createSessions(sportClass.getId(), single(date.plusDays(1), LocalTime.of(10, 0),
                LocalTime.of(11, 0), coachId, facilityId), manager).getFirst().id();
        long memberId = member();
        long registrationId = registration(memberId, 2, 1, "ACTIVE");
        long bookingId = booking(refundSessionId, memberId, registrationId, "CONFIRMED");
        jdbc.update("UPDATE class_session SET booked_count = 1 WHERE id = ?", refundSessionId);
        var request = packageBooking(newSessionId, memberId, registrationId);
        CountDownLatch snapshotRead = new CountDownLatch(1);
        CountDownLatch registrationLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger refundSpid = new AtomicInteger();
        AtomicInteger bookingSpid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> consumption = executor.submit(() -> writerTransaction().execute(status -> {
                bookingSpid.set(count("SELECT @@SPID"));
                assertEquals(1, registrations.findById(registrationId).orElseThrow().getRemainingSessions());
                snapshotRead.countDown();
                await(registrationLocked);
                return bookingService.createBooking(request, manager);
            }));
            Future<?> refund = executor.submit(() -> writerTransaction().execute(status -> {
                refundSpid.set(count("SELECT @@SPID"));
                await(snapshotRead);
                sessions.findForUpdate(refundSessionId).orElseThrow();
                // Match production dependencies before holding the contested registration for the test.
                jdbc.queryForList("SELECT id FROM booking WITH (UPDLOCK, HOLDLOCK) WHERE session_id = ?",
                        Long.class, refundSessionId);
                holdRegistrationForRace(registrationId);
                registrationLocked.countDown();
                await(release);
                if (refundType.equals("BOOKING")) {
                    bookingService.cancelBooking(bookingId, manager);
                } else {
                    service.cancelSession(refundSessionId, new SessionCancelRequest("Refund race"), manager);
                }
                return true;
            }));
            assertTrue(registrationLocked.await(20, TimeUnit.SECONDS));
            awaitRegistrationUpdateBlocked(bookingSpid.get(), refundSpid.get());
            release.countDown();
            assertNotNull(refund.get(20, TimeUnit.SECONDS));
            assertNotNull(consumption.get(20, TimeUnit.SECONDS));
            assertEquals(1, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
            assertEquals(1, count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ? AND status = 'CONFIRMED'", registrationId));
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, bookingId));
            assertEquals(0, count("SELECT booked_count FROM class_session WHERE id = ?", refundSessionId));
            assertEquals(1, count("SELECT booked_count FROM class_session WHERE id = ?", newSessionId));
        } finally {
            release.countDown();
            stopWriters(executor);
        }
    }

    @Test
    void sharedRegistration_staleManagedEntitySaveCannotOverwriteAnAtomicDebit() {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long memberId = member();
        long registrationId = registration(memberId, 2, 2, "ACTIVE");
        writerTransaction().executeWithoutResult(status -> {
            var stale = registrations.findById(registrationId).orElseThrow();
            bookingService.createBooking(packageBooking(id, memberId, registrationId), manager);
            assertEquals(2, stale.getRemainingSessions(), "The managed balance remains a snapshot after native SQL");
            stale.setCancelReason("Exercise an unrelated entity update");
            registrations.saveAndFlush(stale);
            assertEquals(1, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        });
        assertEquals(1, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        assertEquals(1, count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ? AND status = 'CONFIRMED'", registrationId));
    }

    @Test
    void sharedRegistration_transactionFailureRollsBackDebitBookingAndSessionCounter() {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long memberId = member();
        long registrationId = registration(memberId, 2, 2, "ACTIVE");
        assertThrows(IllegalStateException.class, () -> writerTransaction().executeWithoutResult(status -> {
            bookingService.createBooking(packageBooking(id, memberId, registrationId), manager);
            throw new IllegalStateException("Force rollback after the booking and debit are written");
        }));
        assertEquals(2, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        assertEquals(0, count("SELECT COUNT(*) FROM booking WHERE package_registration_id = ?", registrationId));
        assertEquals(0, count("SELECT booked_count FROM class_session WHERE id = ?", id));
    }

    private BookingCreateRequest packageBooking(long sessionId, long memberId, long registrationId) {
        return BookingCreateRequest.builder().sessionId(sessionId).memberId(memberId).packageRegistrationId(registrationId).build();
    }

    private TransactionTemplate writerTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(25);
        return transaction;
    }

    private void holdRegistrationForRace(long registrationId) {
        // Test-only no-op UPDATE holds the row before any booking write can obscure the contested resource.
        assertEquals(1, jdbc.update("""
                UPDATE sport_package_registration SET remaining_sessions = remaining_sessions WHERE id = ?
                """, registrationId));
    }

    private void awaitRegistrationUpdateBlocked(int waitingSpid, int blockingSpid) {
        assertEquals(1, count("SELECT IS_SRVROLEMEMBER('sysadmin')"), "CI uses the container sa login for DMV visibility");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (count("""
                    SELECT COUNT(*) FROM sys.dm_exec_requests r
                    CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
                    WHERE r.session_id = ? AND r.blocking_session_id = ?
                        AND t.text LIKE '%UPDATE sport_package_registration%'
                    """, waitingSpid, blockingSpid) > 0) {
                return;
            }
        } while (System.nanoTime() < deadline);
        var requests = jdbc.queryForList("""
                SELECT r.blocking_session_id, r.wait_type, t.text
                FROM sys.dm_exec_requests r CROSS APPLY sys.dm_exec_sql_text(r.sql_handle) t
                WHERE r.session_id = ?
                """, waitingSpid);
        fail("SQL Server must report the conditional registration UPDATE waiting for the other transaction; observed " + requests);
    }

    private static void stopWriters(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(35, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Test transactions must finish before fixture cleanup");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH", "FACILITY", "BOTH", "INSIDE", "WRAPS", "TOUCH_BEFORE", "TOUCH_AFTER",
            "OTHER_DATE", "OTHER_RESOURCES", "CANCELLED", "DRAFT"})
    void conflictQuery_enforcesOverlapAndResourceRulesWithRealSqlServerTimeParameters(String scenario) {
        long otherCoach = coach();
        long otherFacility = facility();
        String status = scenario.equals("CANCELLED") ? "CANCELLED" : scenario.equals("DRAFT") ? "DRAFT" : "PUBLISHED";
        long existingId = insertSession(date, LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, status);
        long selectedCoach = scenario.equals("FACILITY") || scenario.equals("OTHER_RESOURCES") ? otherCoach : coachId;
        long selectedFacility = scenario.equals("COACH") || scenario.equals("OTHER_RESOURCES") ? otherFacility : facilityId;
        LocalDate requestedDate = scenario.equals("OTHER_DATE") ? date.plusDays(1) : date;
        LocalTime start = switch (scenario) {
            case "TOUCH_BEFORE" -> LocalTime.of(9, 0);
            case "TOUCH_AFTER" -> LocalTime.of(11, 0);
            case "WRAPS" -> LocalTime.of(9, 30);
            default -> LocalTime.of(10, 30);
        };
        LocalTime end = switch (scenario) {
            case "TOUCH_BEFORE" -> LocalTime.of(10, 0);
            case "TOUCH_AFTER" -> LocalTime.of(12, 0);
            case "INSIDE" -> LocalTime.of(10, 45);
            default -> LocalTime.of(11, 30);
        };
        SessionCreateRequest request = single(requestedDate, start, end, selectedCoach, selectedFacility);
        boolean allowed = Set.of("TOUCH_BEFORE", "TOUCH_AFTER", "OTHER_DATE", "OTHER_RESOURCES", "CANCELLED").contains(scenario);
        if (allowed) {
            assertEquals(1, service.createSessions(sportClass.getId(), request, manager).size());
        } else {
            ScheduleConflictException ex = assertThrows(ScheduleConflictException.class,
                    () -> service.createSessions(sportClass.getId(), request, manager));
            List<?> conflicts = (List<?>) ex.getDetails().get("conflicts");
            assertEquals(1, conflicts.size());
            SessionConflictResponse conflict = (SessionConflictResponse) conflicts.getFirst();
            assertEquals(existingId, conflict.sessionId());
            assertEquals(requestedDate, conflict.requestedDate());
            assertEquals(scenario.equals("COACH") ? SessionConflictResponse.ConflictType.COACH
                    : scenario.equals("FACILITY") ? SessionConflictResponse.ConflictType.FACILITY
                    : SessionConflictResponse.ConflictType.BOTH, conflict.conflictType());
            assertEquals(1, count("SELECT COUNT(*) FROM class_session WHERE class_id = ?", sportClass.getId()));
        }
    }

    @Test
    void weeklyCreate_generatesCorrectInclusiveDatesAndOneAuditPerSession() {
        var request = new SessionCreateRequest(null, Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), date, date.plusDays(14),
                LocalTime.of(10, 0), LocalTime.of(11, 0), facilityId, coachId, 16, TrainingFormat.COACH_LED, SessionStatus.DRAFT);
        List<ManagerSessionResponse> result = service.createSessions(sportClass.getId(), request, manager);
        assertEquals(List.of(date, date.plusDays(3), date.plusDays(7), date.plusDays(10), date.plusDays(14)),
                result.stream().map(ManagerSessionResponse::date).toList());
        assertTrue(result.stream().allMatch(row -> row.status() == SessionStatus.DRAFT));
        assertEquals(5, count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id = ? AND action = 'SESSION_CREATED'", manager.getId()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void weeklyConflict_insertsNothingAndReturnsAllConflictingDates(int conflicts) {
        insertSession(date.plusDays(7), LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, "PUBLISHED");
        if (conflicts == 2) {
            insertSession(date.plusDays(14), LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, "DRAFT");
        }
        var request = new SessionCreateRequest(null, Set.of(DayOfWeek.MONDAY), date, date.plusDays(14),
                LocalTime.of(10, 0), LocalTime.of(11, 0), facilityId, coachId, 16, TrainingFormat.COACH_LED, null);
        var ex = assertThrows(ScheduleConflictException.class, () -> service.createSessions(sportClass.getId(), request, manager));
        assertEquals(conflicts, ((List<?>) ex.getDetails().get("conflicts")).size());
        assertEquals(conflicts, count("SELECT COUNT(*) FROM class_session WHERE class_id = ?", sportClass.getId()));
        assertEquals(0, count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id = ?", manager.getId()));
    }

    @Test
    void calendar_filtersInclusiveDatesAndResourcesWithGroupedConfirmedCountsAndNoNPlusOne() {
        long first = insertSession(date, LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, "PUBLISHED");
        long last = insertSession(date.plusDays(2), LocalTime.of(9, 0), LocalTime.of(10, 0), coachId, facilityId, "CANCELLED");
        insertSession(date.minusDays(1), LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, "PUBLISHED");
        insertSession(date.plusDays(3), LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, "PUBLISHED");
        booking(first, member(), null, "CONFIRMED");
        booking(first, member(), null, "CONFIRMED");
        booking(first, member(), null, "CANCELLED");
        jdbc.update("UPDATE class_session SET booked_count = 9 WHERE id = ?", first);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = service.listSessions(date, date.plusDays(2), sportClass.getId(), coachId, facilityId);
        assertEquals(List.of(first, last), result.stream().map(ManagerSessionResponse::id).toList());
        assertEquals(2, result.getFirst().bookedCount());
        assertEquals(SessionStatus.CANCELLED, result.getLast().status());
        assertEquals(0, result.getLast().bookedCount());
        assertEquals(2, statistics.getPrepareStatementCount(), "One entity graph query and one grouped count query");
        assertTrue(service.listSessions(date, date.plusDays(2), -1L, coachId, facilityId).isEmpty());
        assertTrue(service.listSessions(date, date.plusDays(2), sportClass.getId(), -1L, facilityId).isEmpty());
        assertTrue(service.listSessions(date, date.plusDays(2), sportClass.getId(), coachId, -1L).isEmpty());
    }

    @Test
    void update_excludesSelfAllowsTimeChangesWithBookingsAndRejectsCapacityBelowConfirmedCount() throws Exception {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        booking(id, member(), null, "CONFIRMED");
        booking(id, member(), null, "CONFIRMED");
        jdbc.update("UPDATE class_session SET booked_count = 1 WHERE id = ?", id);
        mvc.perform(put("/api/v1/manager/sessions/" + id).with(user(new CustomUserDetails(manager)))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"date":"%s","startTime":"10:00:00","endTime":"11:00:00",
                                 "coachId":%d,"facilityId":%d,"capacity":1}
                                """.formatted(date, coachId, facilityId)))
                .andExpect(status().isBadRequest());
        var result = service.updateSession(id, new SessionUpdateRequest(date, LocalTime.of(11, 0), LocalTime.of(12, 0),
                facilityId, coachId, 2), manager);
        assertEquals(2, result.capacity());
        assertEquals(2, result.bookedCount());
        assertEquals(1, count("SELECT booked_count FROM class_session WHERE id = ?", id));
        assertEquals(1, count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id = ? AND action = 'SESSION_UPDATED'", manager.getId()));
    }

    @Test
    void cancel_cancelsConfirmedOnlyRefundsPurchasedSnapshotsAndPersistsReasonActorAndAudit() throws Exception {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long firstMember = member();
        long normalReg = registration(firstMember, 5, 3, "ACTIVE");
        long firstBooking = booking(id, firstMember, normalReg, "CONFIRMED");
        long secondMember = member();
        long cappedReg = registration(secondMember, 4, 4, "ACTIVE");
        booking(id, secondMember, cappedReg, "CONFIRMED");
        booking(id, member(), null, "CONFIRMED");
        long fourthMember = member();
        long cancelledReg = registration(fourthMember, 5, 1, "ACTIVE");
        long cancelledBooking = booking(id, fourthMember, cancelledReg, "CANCELLED");
        long fifthMember = member();
        long expiredReg = registration(fifthMember, 3, 0, "EXPIRED");
        booking(id, fifthMember, expiredReg, "CONFIRMED");
        jdbc.update("UPDATE class_session SET booked_count = 4 WHERE id = ?", id);
        mvc.perform(put("/api/v1/manager/sessions/" + id + "/cancel").with(user(new CustomUserDetails(manager)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  Unsafe weather  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("Unsafe weather"))
                .andExpect(jsonPath("$.cancelledByUserId").value(manager.getId()))
                .andExpect(jsonPath("$.cancelledAt").exists()).andExpect(jsonPath("$.bookedCount").value(0));
        assertEquals(0, count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CONFIRMED'", id));
        assertEquals(4, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", normalReg));
        assertEquals(4, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", cappedReg));
        assertEquals(1, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", cancelledReg));
        assertEquals(0, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", expiredReg));
        assertEquals("EXPIRED", jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE id = ?", String.class, expiredReg));
        assertEquals(manager.getId(), jdbc.queryForObject("SELECT cancelled_by_user_id FROM booking WHERE id = ?", Long.class, firstBooking));
        assertNotNull(jdbc.queryForObject("SELECT cancelled_at FROM booking WHERE id = ?", LocalDateTime.class, firstBooking));
        assertNull(jdbc.queryForObject("SELECT cancelled_by_user_id FROM booking WHERE id = ?", Long.class, cancelledBooking));
        assertEquals(1, count("""
                SELECT COUNT(*) FROM activity_log WHERE actor_user_id = ? AND action = 'SESSION_CANCELLED'
                    AND JSON_VALUE(details, '$.bookingsCancelled') = '4' AND JSON_VALUE(details, '$.reason') = 'Unsafe weather'
                """, manager.getId()));
        var auditDetails = objectMapper.readTree(jdbc.queryForObject("""
                SELECT details FROM activity_log WHERE entity_type = 'CLASS_SESSION' AND entity_id = ?
                    AND action = 'SESSION_CANCELLED'
                """, String.class, id));
        Map<Long, Integer> packageBookings = new HashMap<>();
        for (var registration : auditDetails.get("registrations")) {
            assertEquals(2, registration.size());
            packageBookings.put(registration.get("registrationId").asLong(), registration.get("packageBookings").asInt());
        }
        assertEquals(3, auditDetails.get("registrations").size());
        assertEquals(Map.of(normalReg, 1, cappedReg, 1, expiredReg, 1), packageBookings,
                "Audit counts bookings even when credits are capped or the registration is inactive");
        mvc.perform(put("/api/v1/manager/sessions/" + id + "/cancel").with(user(new CustomUserDetails(manager)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Again\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(4, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", normalReg));
    }

    @Test
    void update_packageValidityQueryUsesInclusiveBoundsAndIgnoresNullLinksCancelledBookingsAndOtherSessions() {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long firstMember = member();
        long firstReg = registration(firstMember, 5, 3, "ACTIVE");
        long secondMember = member();
        long secondReg = registration(secondMember, 5, 3, "ACTIVE");
        jdbc.update("UPDATE sport_package_registration SET start_date = ?, end_date = ? WHERE id IN (?, ?)",
                date, date.plusDays(2), firstReg, secondReg);
        booking(id, firstMember, firstReg, "CONFIRMED");
        booking(id, secondMember, secondReg, "CONFIRMED");
        booking(id, member(), null, "CONFIRMED");
        long ignoredMember = member();
        long ignoredReg = registration(ignoredMember, 5, 3, "ACTIVE");
        jdbc.update("UPDATE sport_package_registration SET end_date = ? WHERE id = ?", date.minusDays(1), ignoredReg);
        booking(id, ignoredMember, ignoredReg, "CANCELLED");
        long otherId = insertSession(date.plusDays(10), LocalTime.of(10, 0), LocalTime.of(11, 0), coachId, facilityId, "PUBLISHED");
        booking(otherId, ignoredMember, ignoredReg, "CONFIRMED");
        assertEquals(0, bookingRepository.countOutsidePackageValidity(id, BookingStatus.CONFIRMED, date));
        assertEquals(0, bookingRepository.countOutsidePackageValidity(id, BookingStatus.CONFIRMED, date.plusDays(2)));
        assertEquals(2, bookingRepository.countOutsidePackageValidity(id, BookingStatus.CONFIRMED, date.minusDays(1)));
        assertEquals(2, bookingRepository.countOutsidePackageValidity(id, BookingStatus.CONFIRMED, date.plusDays(3)));
        int auditsBefore = count("SELECT COUNT(*) FROM activity_log WHERE entity_id = ? AND action = 'SESSION_UPDATED'", id);
        var error = assertThrows(BusinessRuleException.class, () -> service.updateSession(id,
                new SessionUpdateRequest(date.plusDays(3), LocalTime.of(11, 0), LocalTime.of(12, 0), facilityId, coachId, 16), manager));
        assertEquals("The new date is outside the validity period of packages used by confirmed bookings of this session",
                error.getMessage());
        assertEquals(date, jdbc.queryForObject("SELECT session_date FROM class_session WHERE id = ?", LocalDate.class, id));
        assertEquals(auditsBefore, count("SELECT COUNT(*) FROM activity_log WHERE entity_id = ? AND action = 'SESSION_UPDATED'", id));
        assertEquals(date.plusDays(2), service.updateSession(id,
                new SessionUpdateRequest(date.plusDays(2), LocalTime.of(11, 0), LocalTime.of(12, 0), facilityId, coachId, 16), manager).date());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_PAYMENT", "EXPIRED", "CANCELLED", "REFUNDED"})
    void cancel_refundsActiveRegistrationOnlyButCancelsBookingsForEveryRegistrationStatus(String inactiveStatus) {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long activeMember = member();
        long activeRegistration = registration(activeMember, 5, 2, "ACTIVE");
        booking(id, activeMember, activeRegistration, "CONFIRMED");
        long inactiveMember = member();
        long inactiveRegistration = registration(inactiveMember, 5, 2, inactiveStatus);
        booking(id, inactiveMember, inactiveRegistration, "CONFIRMED");

        assertEquals(SessionStatus.CANCELLED, service.cancelSession(id, new SessionCancelRequest("Weather"), manager).status());

        assertEquals(3, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", activeRegistration));
        assertEquals(2, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", inactiveRegistration));
        assertEquals(inactiveStatus, jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE id = ?",
                String.class, inactiveRegistration));
        assertEquals(2, count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CANCELLED' AND cancelled_by_user_id = ?",
                id, manager.getId()));
        assertEquals(0, count("SELECT booked_count FROM class_session WHERE id = ?", id));
    }

    @Test
    void cancellationFailure_rollsBackSessionBookingsRegistrationAndAuditTogether() {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long memberId = member();
        long registrationId = registration(memberId, 5, 3, "ACTIVE");
        long bookingId = booking(id, memberId, registrationId, "CONFIRMED");
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.cancelSession(id, new SessionCancelRequest("Rollback test"), manager);
            throw new IllegalStateException("Force rollback after cancellation and its audit are written");
        }));
        assertEquals("PUBLISHED", jdbc.queryForObject("SELECT status FROM class_session WHERE id = ?", String.class, id));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, bookingId));
        assertEquals(3, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        assertEquals(0, count("SELECT COUNT(*) FROM activity_log WHERE entity_type = 'CLASS_SESSION' AND entity_id = ? AND action = 'SESSION_CANCELLED'", id));
    }

    @Test
    void createBooking_racingWithSessionCancellationWaitsAndRejectsWithoutLosingCancellationOrRefund() throws Exception {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        long memberId = member();
        long registrationId = registration(memberId, 5, 3, "ACTIVE");
        booking(id, memberId, registrationId, "CONFIRMED");
        jdbc.update("UPDATE class_session SET booked_count = 1 WHERE id = ?", id);
        var request = BookingCreateRequest.builder().sessionId(id).packageRegistrationId(registrationId).build();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch bookingStarted = new CountDownLatch(1);
        AtomicInteger cancellationSpid = new AtomicInteger();
        AtomicInteger bookingSpid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> cancellation = null;
        Future<?> creation = null;
        try {
            cancellation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                cancellationSpid.set(count("SELECT @@SPID"));
                sessions.findForUpdate(id).orElseThrow();
                locked.countDown();
                await(release);
                return service.cancelSession(id, new SessionCancelRequest("Weather race"), manager);
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS), "Cancellation must lock the session first");
            creation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                bookingSpid.set(count("SELECT @@SPID"));
                bookingStarted.countDown();
                return bookingService.createBooking(request, users.findById(memberId).orElseThrow());
            }));
            assertTrue(bookingStarted.await(10, TimeUnit.SECONDS));
            awaitBlocked(bookingSpid.get(), cancellationSpid.get());
            release.countDown();
            assertNotNull(cancellation.get(20, TimeUnit.SECONDS));
            Future<?> competingBooking = creation;
            ExecutionException error = assertThrows(ExecutionException.class,
                    () -> competingBooking.get(20, TimeUnit.SECONDS));
            var businessError = assertInstanceOf(BusinessRuleException.class, error.getCause());
            assertEquals("Session is not published for booking", businessError.getMessage());
            assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM class_session WHERE id = ?", String.class, id));
            assertEquals("Weather race", jdbc.queryForObject("SELECT cancel_reason FROM class_session WHERE id = ?", String.class, id));
            assertEquals(0, count("SELECT booked_count FROM class_session WHERE id = ?", id));
            assertEquals(0, count("SELECT COUNT(*) FROM booking WHERE session_id = ? AND status = 'CONFIRMED'", id));
            assertEquals(1, count("SELECT COUNT(*) FROM booking WHERE session_id = ?", id));
            assertEquals(4, count("SELECT remaining_sessions FROM sport_package_registration WHERE id = ?", registrationId));
        } finally {
            release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                if (cancellation != null) cancellation.cancel(true);
                if (creation != null) creation.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    @Test
    void cancel_withoutBookingsSucceedsAndAuditsZeroCancellations() {
        long id = service.createSessions(sportClass.getId(), single(), manager).getFirst().id();
        assertEquals(SessionStatus.CANCELLED, service.cancelSession(id, new SessionCancelRequest("Maintenance"), manager).status());
        assertEquals(1, count("""
                SELECT COUNT(*) FROM activity_log WHERE entity_type = 'CLASS_SESSION' AND entity_id = ?
                    AND action = 'SESSION_CANCELLED' AND JSON_VALUE(details, '$.bookingsCancelled') = '0'
                    AND JSON_QUERY(details, '$.registrations') = '[]'
                """, id));
    }

    @Test
    void migration_hasCancellationColumnsAndBothResourceDateIndexes() {
        assertEquals(1, count("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '23' AND success = 1"));
        assertEquals(3, count("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'class_session'
                    AND COLUMN_NAME IN ('cancel_reason', 'cancelled_at', 'cancelled_by_user_id')
                """));
        for (String resource : List.of("coach_id", "facility_id")) {
            assertTrue(count("""
                    SELECT COUNT(*) FROM sys.indexes i
                    JOIN sys.index_columns a ON a.object_id = i.object_id AND a.index_id = i.index_id AND a.key_ordinal = 1
                    JOIN sys.index_columns b ON b.object_id = i.object_id AND b.index_id = i.index_id AND b.key_ordinal = 2
                    WHERE i.object_id = OBJECT_ID('class_session') AND i.has_filter = 0 AND i.is_disabled = 0
                      AND COL_NAME(i.object_id, a.column_id) = ? AND COL_NAME(i.object_id, b.column_id) = 'session_date'
                    """, resource) > 0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"COACH", "FACILITY"})
    void concurrentCreate_serializesSharedResourceAndOnlyOneOverlappingSessionCommits(String resource) throws Exception {
        long secondCoach = resource.equals("COACH") ? coachId : coach();
        long secondFacility = resource.equals("FACILITY") ? facilityId : facility();
        SessionCreateRequest secondRequest = single(date, LocalTime.of(10, 30), LocalTime.of(11, 30), secondCoach, secondFacility);
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        AtomicInteger firstSpid = new AtomicInteger();
        AtomicInteger secondSpid = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> first = null;
        Future<?> second = null;
        try {
            first = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                firstSpid.set(count("SELECT @@SPID"));
                var result = service.createSessions(sportClass.getId(), single(), manager);
                inserted.countDown();
                await(release);
                return result;
            }));
            assertTrue(inserted.await(20, TimeUnit.SECONDS), "First writer must insert while holding resource locks");
            second = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                secondSpid.set(count("SELECT @@SPID"));
                secondStarted.countDown();
                return service.createSessions(sportClass.getId(), secondRequest, manager);
            }));
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS));
            awaitBlocked(secondSpid.get(), firstSpid.get());
            release.countDown();
            assertNotNull(first.get(20, TimeUnit.SECONDS));
            Future<?> competingWriter = second;
            ExecutionException error = assertThrows(ExecutionException.class, () -> competingWriter.get(20, TimeUnit.SECONDS));
            assertInstanceOf(ScheduleConflictException.class, error.getCause());
            assertEquals(1, count("SELECT COUNT(*) FROM class_session WHERE class_id = ?", sportClass.getId()));
        } finally {
            release.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                if (first != null) first.cancel(true);
                if (second != null) second.cancel(true);
                executor.shutdownNow();
            }
        }
    }

    private void awaitBlocked(int waitingSpid, int blockingSpid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            if (count("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id = ? AND blocking_session_id = ?",
                    waitingSpid, blockingSpid) > 0) {
                return;
            }
        } while (System.nanoTime() < deadline);
        fail("SQL Server must report the competing writer waiting for the first transaction");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test writer release");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
