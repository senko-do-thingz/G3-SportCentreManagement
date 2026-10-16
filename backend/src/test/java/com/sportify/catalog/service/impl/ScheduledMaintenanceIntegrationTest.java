package com.sportify.catalog.service.impl;

import com.sportify.IsolatedMaintenanceDatabase;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.service.PaymentVerificationService;
import com.sportify.payment.repository.PaymentRepository;
import com.microsoft.sqlserver.jdbc.SQLServerException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/** Real SQL Server, private migrated database, committed tracked fixtures and independent worker transactions. */
@Tag("integration")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@TestPropertySource(properties = {"spring.datasource.hikari.connection-init-sql=SET LOCK_TIMEOUT 5000",
        "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.connection-timeout=10000"})
@ActiveProfiles(value = {"dev", "test"}, inheritProfiles = false)
@Import(ScheduledMaintenanceIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduledMaintenanceIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        IsolatedMaintenanceDatabase.register(registry);
    }
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(0, 0);
    static final LocalDateTime OLD = TODAY.minusDays(8).atTime(23, 59, 59);
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock maintenanceTestClock() {
            // UTC deliberately differs from the business zone at this instant.
            return Clock.fixed(Instant.parse("2026-10-15T17:00:00Z"), ZoneOffset.UTC);
        }
    }
    @Autowired ScheduledMaintenanceService maintenance;
    @Autowired MembershipScheduledJobs jobs;
    @Autowired PaymentRepository payments;
    @Autowired ScheduledAnnotationBeanPostProcessor scheduling;
    @Autowired PaymentVerificationService verification;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @Autowired MemberCardRepository cards;
    @Autowired SportPackageRegistrationRepository registrations;
    private final AtomicInteger cases = new AtomicInteger();
    private String marker;
    private int serial;
    private long member, manager, sport, pack;
    private MaintenanceTestResources resources;
    private Map<String, Long> baseline;

    private Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : MaintenanceTestResources.DELETE_ORDER) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT_BIG(*) FROM " + table, Long.class));
        }
        return counts;
    }

    @BeforeAll void baseline() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo(IsolatedMaintenanceDatabase.name());
        // Trigger methods remain callable, but wall-clock cron callbacks must not race test fixtures.
        scheduling.getScheduledTasks().forEach(task -> task.cancel());
        baseline = counts();
        assertThat(baseline.get("sport")).isEqualTo(6);
        assertThat(baseline.get("sport_package")).isEqualTo(36);
        assertThat(baseline.get("member_card")).isZero();
        assertThat(baseline.get("sport_package_registration")).isZero();
    }

    @BeforeEach void fixtures() {
        resources = new MaintenanceTestResources(jdbc);
        assertThat(counts()).as("Prior invocation must leave the isolated database at its baseline").isEqualTo(baseline);
        marker = "be06r3_" + cases.incrementAndGet() + "_";
        serial = 0;
        member = user("MEMBER"); manager = user("MANAGER");
        jdbc.update("INSERT INTO member_profile(user_id,member_code,joined_on) VALUES (?,?,?)", member, code(), TODAY);
        resources.remember("member_profile", member);
        sport = id("INSERT INTO sport(code,name,venue_type) OUTPUT INSERTED.id VALUES (?,?,'INDOOR')", code(), code());
        pack = id("INSERT INTO sport_package(code,name,sport_id,training_format,duration_days,session_count,"
                + "price_amount,session_minutes) OUTPUT INSERTED.id VALUES (?,?,?,'SELF_TRAINING',30,10,100,60)",
                code(), "BE06 package", sport);
    }

    @AfterEach void cleanup() {
        List<Throwable> failures = new ArrayList<>();
        try { if (resources != null) resources.close(); } catch (Throwable failure) { failures.add(failure); }
        try { assertThat(counts()).as("Committed fixtures must be removed even after test failure").isEqualTo(baseline); }
        catch (Throwable failure) { failures.add(failure); }
        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError("BE-06 fixture isolation failed");
            failures.forEach(error::addSuppressed);
            throw error;
        }
    }
    private String code() { return marker + (++serial); }
    private long id(String sql, Object... args) {
        String table = sql.substring("INSERT INTO ".length(), sql.indexOf('(')).trim();
        return resources.insert(table, sql, args);
    }
    private long user(String role) {
        return id("INSERT INTO user_account(email,password_hash,full_name,role_id,status,version) "
                + "OUTPUT INSERTED.id VALUES (?,'test-only-non-credential','BE06 fixture',"
                + "(SELECT id FROM role WHERE code=?),'ACTIVE',0)", code() + "@example.test", role);
    }
    private long card(String status, LocalDateTime created, LocalDate end, boolean standard) {
        return id("INSERT INTO member_card(card_code,member_id,tier_id,start_date,end_date,status,price_paid,created_at) "
                + "OUTPUT INSERTED.id VALUES (?,?,(SELECT id FROM membership_card_tier WHERE code=?),?,?,?,?,?)",
                code(), member, standard ? "STANDARD" : "GOLD", TODAY.minusDays(40), end, status,
                standard ? BigDecimal.ZERO : new BigDecimal("100"), created);
    }
    private long registration(String status, LocalDateTime created, LocalDate end) {
        return id("INSERT INTO sport_package_registration(registration_code,member_id,package_id,channel,"
                + "original_price,discount_percentage,paid_amount,total_sessions,remaining_sessions,start_date,"
                + "end_date,status,created_by_user_id,created_at) OUTPUT INSERTED.id "
                + "VALUES (?,?,?,'ONLINE',100,0,100,10,6,?,?,?,?,?)",
                code(), member, pack, TODAY.minusDays(40), end, status, member, created);
    }
    private Map<String,Object> row(String table, long id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE id=?", id);
    }
    private String state(String table, long id) { return (String) row(table, id).get("status"); }
    private long payment(String table, long item) {
        long payment = id("INSERT INTO payment(payment_code,member_id,amount,payment_method,payment_status) "
                + "OUTPUT INSERTED.id VALUES (?,?,100,'CASH','PENDING')", code(), member);
        jdbc.update("UPDATE " + table + " SET payment_id=? WHERE id=?", payment, item);
        return payment;
    }
    private UserAccount actor() {
        return tx().execute(status -> {
            UserAccount account = users.findById(manager).orElseThrow();
            account.getRole().getCode();
            return account;
        });
    }
    private void confirm(long payment) {
        verification.confirmPayment(payment, ConfirmPaymentRequest.builder().method(PaymentMethod.CASH)
                .amountReceived(new BigDecimal("100")).build(), actor());
    }
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
    private ExecutorService pool() {
        return resources.pool();
    }
    private static void await(CompletableFuture<Void> signal) {
        try { signal.get(20, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        catch (ExecutionException | TimeoutException e) { throw new IllegalStateException("BE-06 worker coordination failed", e); }
    }
    private int total(ScheduledMaintenanceService.MaintenanceResult r) {
        return r.cancelledPendingCards() + r.expiredCards() + r.cancelledPendingRegistrations() + r.expiredRegistrations();
    }

    @ParameterizedTest @CsvSource({"0,0", "1,0", "6,0", "7,0", "8,1", "15,1"})
    void pendingCardCalendarBoundaries(int age, int affected) {
        long first = card("PENDING_PAYMENT", TODAY.minusDays(age).atStartOfDay(), TODAY.plusDays(30), false);
        long last = card("PENDING_PAYMENT", TODAY.minusDays(age).atTime(23,59,59), TODAY.plusDays(30), false);
        var result = maintenance.runDailyMaintenance();
        assertThat(result.cancelledPendingCards()).isEqualTo(affected * 2);
        assertThat(result.businessDate()).isEqualTo(TODAY);
        assertThat(state("member_card", first)).isEqualTo(affected == 1 ? "CANCELLED" : "PENDING_PAYMENT");
        assertThat(state("member_card", last)).isEqualTo(state("member_card", first));
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
    }

    @ParameterizedTest @CsvSource({"0,0", "1,0", "6,0", "7,0", "8,1", "15,1"})
    void pendingRegistrationCalendarBoundaries(int age, int affected) {
        long first = registration("PENDING_PAYMENT", TODAY.minusDays(age).atStartOfDay(), TODAY.plusDays(30));
        long last = registration("PENDING_PAYMENT", TODAY.minusDays(age).atTime(23,59,59), TODAY.plusDays(30));
        var result = maintenance.runDailyMaintenance();
        assertThat(result.cancelledPendingRegistrations()).isEqualTo(affected * 2);
        assertThat(state("sport_package_registration", first)).isEqualTo(affected == 1 ? "CANCELLED" : "PENDING_PAYMENT");
        assertThat(state("sport_package_registration", last)).isEqualTo(state("sport_package_registration", first));
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
    }

    @ParameterizedTest @CsvSource({"-1,1", "0,0", "1,0"})
    void activeCardExpirationIsStrictlyBeforeBusinessDate(int offset, int affected) {
        long item = card("ACTIVE", OLD, TODAY.plusDays(offset), false);
        var before = row("member_card", item);
        var eligible = jdbc.queryForList("SELECT id FROM member_card WHERE status='ACTIVE' AND end_date < ?", Long.class, TODAY);
        assertThat(eligible).as("Exact eligible fixture ids before the unfiltered production bulk update")
                .containsExactlyElementsOf(affected == 1 ? List.of(item) : List.of());
        assertThat(maintenance.runDailyMaintenance().expiredCards()).isEqualTo(affected);
        assertThat(state("member_card", item)).isEqualTo(affected == 1 ? "EXPIRED" : "ACTIVE");
        unchangedExcept(before, row("member_card", item), affected == 1);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
    }

    @ParameterizedTest @ValueSource(booleans = {true,false})
    void nullEndDateIncludingPermanentStandardIsUnchanged(boolean standard) {
        long item = card("ACTIVE", OLD, null, standard);
        var before = row("member_card", item);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        assertThat(row("member_card", item)).isEqualTo(before);
    }

    @ParameterizedTest @CsvSource({"-1,1", "0,0", "1,0"})
    void registrationExpirationPreservesAllOtherData(int offset, int affected) {
        long item = registration("ACTIVE", OLD, TODAY.plusDays(offset));
        var before = row("sport_package_registration", item);
        assertThat(maintenance.runDailyMaintenance().expiredRegistrations()).isEqualTo(affected);
        assertThat(state("sport_package_registration", item)).isEqualTo(affected == 1 ? "EXPIRED" : "ACTIVE");
        unchangedExcept(before, row("sport_package_registration", item), affected == 1);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
    }

    private void unchangedExcept(Map<String,Object> before, Map<String,Object> after, boolean changed) {
        if (changed) {
            assertThat(after.get("updated_at")).isEqualTo(Timestamp.valueOf(NOW));
            before.remove("status"); before.remove("updated_at");
            after.remove("status"); after.remove("updated_at");
        }
        assertThat(after).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(value=CardStatus.class, names={"EXPIRED","CANCELLED","REPLACED"})
    void terminalCardStatusesAreNotChanged(CardStatus status) {
        long item = card(status.name(), OLD, TODAY.minusDays(1), false);
        var before = row("member_card", item);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        assertThat(row("member_card", item)).isEqualTo(before);
    }

    @ParameterizedTest @EnumSource(value=PackageRegistrationStatus.class, names={"EXPIRED","CANCELLED","REFUNDED"})
    void terminalRegistrationStatusesAreNotChanged(PackageRegistrationStatus status) {
        long item = registration(status.name(), OLD, TODAY.minusDays(1));
        var before = row("sport_package_registration", item);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        assertThat(row("sport_package_registration", item)).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(strings={"card","registration"})
    void pendingItemWithPastEndDateDoesNotExpireBeforeCancellationAge(String kind) {
        boolean card = kind.equals("card");
        String table = card ? "member_card" : "sport_package_registration";
        long item = card ? card("PENDING_PAYMENT",TODAY.minusDays(7).atStartOfDay(),TODAY.minusDays(1),false)
                : registration("PENDING_PAYMENT",TODAY.minusDays(7).atStartOfDay(),TODAY.minusDays(1));
        var before = row(table,item);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        assertThat(row(table,item)).isEqualTo(before);
    }

    @Test void nullRegistrationEndDateIsRejectedByActualSchema() {
        assertThatThrownBy(() -> registration("ACTIVE", OLD, null))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void zeroCreditsAloneDoNotExpireAnActivePackage() {
        long item = registration("ACTIVE", OLD, TODAY.plusDays(1));
        jdbc.update("UPDATE sport_package_registration SET remaining_sessions=0 WHERE id=?", item);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        assertThat(state("sport_package_registration", item)).isEqualTo("ACTIVE");
    }

    @Test void allFourCountsIdempotencyAndRelatedRowsArePreserved() {
        long pendingCard = card("PENDING_PAYMENT", OLD, TODAY.plusDays(30), false);
        long expiredCard = card("ACTIVE", OLD, TODAY.minusDays(1), false);
        long pendingReg = registration("PENDING_PAYMENT", OLD, TODAY.plusDays(30));
        long expiredReg = registration("ACTIVE", OLD, TODAY.minusDays(1));
        long pendingCardPayment = payment("member_card", pendingCard);
        long pendingRegPayment = payment("sport_package_registration", pendingReg);
        long paid = payment("sport_package_registration", expiredReg);
        jdbc.update("UPDATE payment SET payment_status='SUCCESS',payment_time=? WHERE id=?", NOW.minusDays(8), paid);
        long invoice = id("INSERT INTO invoice(invoice_number,payment_id,member_id,subtotal_amount,"
                + "total_amount,status) OUTPUT INSERTED.id VALUES (?,?,?,100,100,'PAID')", code(), paid, member);
        long line = id("INSERT INTO invoice_line(invoice_id,item_type,item_reference_id,description,quantity,"
                + "unit_price,line_total) OUTPUT INSERTED.id VALUES (?,'SPORT_PACKAGE',?,'BE06 snapshot',1,100,100)", invoice, expiredReg);
        long session = id("INSERT INTO class_session(sport_id,session_date,start_time,end_time,training_type,"
                + "booked_count,status) OUTPUT INSERTED.id VALUES (?,?,'09:00','10:00','SELF_TRAINING',1,'COMPLETED')", sport, TODAY.minusDays(2));
        long booking = id("INSERT INTO booking(booking_code,session_id,member_id,package_registration_id) "
                + "OUTPUT INSERTED.id VALUES (?,?,?,?)", code(), session, member, expiredReg);
        long attendance = id("INSERT INTO check_in(member_id,recorded_by,booking_id,result) "
                + "OUTPUT INSERTED.id VALUES (?,?,?,'ALLOWED')", member, manager, booking);
        var protectedRows = Map.of("payment", List.of(pendingCardPayment,pendingRegPayment,paid),
                "invoice",List.of(invoice),"invoice_line",List.of(line),"class_session",List.of(session),
                "booking",List.of(booking),"check_in",List.of(attendance));
        Map<String,Map<String,Object>> snapshots = new HashMap<>();
        protectedRows.forEach((table, ids) -> ids.forEach(id -> snapshots.put(table+id, row(table,id))));
        var pendingCardBefore = row("member_card", pendingCard);
        var pendingRegBefore = row("sport_package_registration", pendingReg);
        jdbc.update("UPDATE sport_package_registration SET cancelled_at=?,cancel_reason='Historical note' WHERE id=?",
                NOW.minusDays(10),expiredReg);
        var expiredRegBefore = row("sport_package_registration", expiredReg);
        long auditCount = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class);
        assertThat(maintenance.runDailyMaintenance()).isEqualTo(
                new ScheduledMaintenanceService.MaintenanceResult(TODAY,1,1,1,1));
        assertThat(state("member_card", pendingCard)).isEqualTo("CANCELLED");
        assertThat(state("member_card", expiredCard)).isEqualTo("EXPIRED");
        assertThat(state("sport_package_registration", pendingReg)).isEqualTo("CANCELLED");
        assertThat(state("sport_package_registration", expiredReg)).isEqualTo("EXPIRED");
        unchangedExcept(pendingCardBefore,row("member_card",pendingCard),true);
        unchangedExcept(pendingRegBefore,row("sport_package_registration",pendingReg),true);
        unchangedExcept(expiredRegBefore,row("sport_package_registration",expiredReg),true);
        protectedRows.forEach((table, ids) -> ids.forEach(id -> assertThat(row(table,id)).isEqualTo(snapshots.get(table+id))));
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class)).isEqualTo(auditCount);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        protectedRows.forEach((table, ids) -> ids.forEach(id -> assertThat(row(table,id)).isEqualTo(snapshots.get(table+id))));
    }

    @Test void failureInFourthUpdateRollsBackAllEarlierUpdates() {
        long c = card("PENDING_PAYMENT", OLD, TODAY.plusDays(30), false);
        long e = card("ACTIVE", OLD, TODAY.minusDays(1), false);
        long r = registration("PENDING_PAYMENT", OLD, TODAY.plusDays(30));
        long x = registration("ACTIVE", OLD, TODAY.minusDays(1));
        var beforeC=row("member_card",c); var beforeE=row("member_card",e);
        var beforeR=row("sport_package_registration",r); var beforeX=row("sport_package_registration",x);
        long audits = jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class);
        // The real fourth UPDATE reaches the trigger inside the service transaction. The trigger
        // proves all three earlier changes are visible before throwing its distinct vendor error.
        jdbc.execute("""
                CREATE TRIGGER %s ON sport_package_registration AFTER UPDATE AS
                BEGIN
                    SET NOCOUNT ON;
                    IF EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON d.id=i.id
                               WHERE i.id=%d AND d.status='ACTIVE' AND i.status='EXPIRED')
                    BEGIN
                        IF NOT EXISTS (SELECT 1 FROM member_card WHERE id=%d AND status='CANCELLED')
                           OR NOT EXISTS (SELECT 1 FROM member_card WHERE id=%d AND status='EXPIRED')
                           OR NOT EXISTS (SELECT 1 FROM sport_package_registration WHERE id=%d AND status='CANCELLED')
                            THROW 51007, 'Earlier maintenance operations were not visible', 1;
                        THROW 51006, 'Synthetic fourth maintenance operation failure', 1;
                    END;
                END;
                """.formatted(MaintenanceTestResources.FAILURE_TRIGGER,x,c,e,r));
        Logger logger = (Logger) LoggerFactory.getLogger(MembershipScheduledJobs.class);
        ListAppender<ILoggingEvent> messages = new ListAppender<>();
        messages.start(); logger.addAppender(messages);
        try {
            assertThatThrownBy(jobs::processDailyMemberships).satisfies(failure -> {
                Throwable cause = failure;
                while (cause.getCause() != null) cause = cause.getCause();
                assertThat(cause).isInstanceOf(SQLServerException.class);
                assertThat(((SQLServerException) cause).getErrorCode()).isEqualTo(51006);
            });
            assertThat(messages.list.stream().filter(event -> event.getThreadName().equals(Thread.currentThread().getName()))
                    .map(ILoggingEvent::getFormattedMessage)).noneMatch(message -> message.contains("Maintenance committed"));
        } finally {
            logger.detachAppender(messages); messages.stop();
            jdbc.execute("DROP TRIGGER " + MaintenanceTestResources.FAILURE_TRIGGER);
        }
        assertThat(row("member_card",c)).isEqualTo(beforeC); assertThat(row("member_card",e)).isEqualTo(beforeE);
        assertThat(row("sport_package_registration",r)).isEqualTo(beforeR); assertThat(row("sport_package_registration",x)).isEqualTo(beforeX);
        assertThat(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM activity_log", Long.class)).isEqualTo(audits);
    }

    @Test void modifyingQueriesFlushAndClearTheirPersistenceContext() {
        long item = card("PENDING_PAYMENT", OLD, TODAY.plusDays(30), false);
        tx().executeWithoutResult(status -> {
            MemberCard managed = cards.findById(item).orElseThrow();
            managed.setPricePaid(new BigDecimal("123"));
            assertThat(cards.cancelOverduePending(TODAY.minusDays(7).atStartOfDay(),NOW)).isEqualTo(1);
            assertThat(em.contains(managed)).isFalse();
            MemberCard fresh = cards.findById(item).orElseThrow();
            assertThat(fresh.getStatus()).isEqualTo(CardStatus.CANCELLED);
            assertThat(fresh.getPricePaid()).isEqualByComparingTo("123");
        });
    }

    @Test void concurrentSchedulersDoNotDoubleProcess() throws Exception {
        card("PENDING_PAYMENT", OLD, TODAY.plusDays(30), false);
        card("ACTIVE", OLD, TODAY.minusDays(1), false);
        registration("PENDING_PAYMENT", OLD, TODAY.plusDays(30));
        registration("ACTIVE", OLD, TODAY.minusDays(1));
        var workers = pool(); var barrier = new CyclicBarrier(2);
        Callable<ScheduledMaintenanceService.MaintenanceResult> run = () -> { barrier.await(20,TimeUnit.SECONDS); return maintenance.runDailyMaintenance(); };
        var first = workers.submit(run); var second = workers.submit(run);
        var a = first.get(30,TimeUnit.SECONDS); var b = second.get(30,TimeUnit.SECONDS);
        assertThat(total(a)+total(b)).isEqualTo(4);
        assertThat(List.of(total(a),total(b))).containsExactlyInAnyOrder(0,4);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"card","registration"})
    void maintenanceFirstPreventsLaterPaymentConfirmation(String kind) {
        boolean card = kind.equals("card");
        String table = card ? "member_card" : "sport_package_registration";
        long item = card ? card("PENDING_PAYMENT",OLD,TODAY.plusDays(30),false)
                : registration("PENDING_PAYMENT",OLD,TODAY.plusDays(30));
        long payment = payment(table,item);
        maintenance.runDailyMaintenance();
        assertThatThrownBy(() -> confirm(payment)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("CANCELLED");
        assertThat(state(table,item)).isEqualTo("CANCELLED");
        assertThat(row("payment",payment).get("payment_status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE payment_id=?",Integer.class,payment)).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"card","registration"})
    void paymentConfirmationFirstPreservesActiveItemAndReceipt(String kind) {
        boolean card = kind.equals("card");
        String table = card ? "member_card" : "sport_package_registration";
        long item = card ? card("PENDING_PAYMENT",OLD,TODAY.plusDays(30),false)
                : registration("PENDING_PAYMENT",OLD,TODAY.plusDays(30));
        long payment = payment(table,item);
        confirm(payment);
        var itemBefore=row(table,item);var paidBefore=row("payment",payment);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
        assertThat(state(table,item)).isEqualTo("ACTIVE");
        assertThat(row(table,item)).isEqualTo(itemBefore);
        assertThat(row("payment",payment)).isEqualTo(paidBefore);
        assertThat(paidBefore.get("payment_status")).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE payment_id=?",Integer.class,payment)).isOne();
    }

    @ParameterizedTest @ValueSource(strings={"card","registration"})
    void cancellationAfterActivationReadCannotResurrectCachedPendingItem(String kind) throws Exception {
        boolean card = kind.equals("card");
        String table = card ? "member_card" : "sport_package_registration";
        long item = card ? card("PENDING_PAYMENT",OLD,TODAY.plusDays(30),false)
                : registration("PENDING_PAYMENT",OLD,TODAY.plusDays(30));
        long payment = payment(table,item);
        CompletableFuture<Void> read = new CompletableFuture<>();
        CompletableFuture<Void> resume = resources.releaseGate();
        var activation = pool().submit(() -> {
            try {
                assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
                    // Follow the production payment-first order. Complete the read before signaling:
                    // no repository proxy callback or open result set is parked at the barrier.
                    payments.findByIdForUpdate(payment).orElseThrow();
                    Object cached = card ? em.find(MemberCard.class,item) : em.find(SportPackageRegistration.class,item);
                    assertThat(em.contains(cached)).isTrue();
                    assertThat(card ? ((MemberCard) cached).getStatus().name()
                            : ((SportPackageRegistration) cached).getStatus().name()).isEqualTo("PENDING_PAYMENT");
                    read.complete(null);
                    await(resume);
                    // The managed object is deliberately still stale; the real activation must refresh it.
                    assertThat(card ? ((MemberCard) cached).getStatus().name()
                            : ((SportPackageRegistration) cached).getStatus().name()).isEqualTo("PENDING_PAYMENT");
                    confirm(payment);
                })).isInstanceOf(BusinessRuleException.class).hasMessageContaining("CANCELLED");
            } catch (Throwable failure) {
                read.completeExceptionally(failure);
                throw failure;
            }
        });
        try {
            await(read);
            assertThat(total(maintenance.runDailyMaintenance())).isEqualTo(1);
        } finally { resume.complete(null); }
        activation.get(30,TimeUnit.SECONDS);
        assertThat(activation.isDone()).isTrue();
        assertThat(state(table,item)).isEqualTo("CANCELLED");
        assertThat(row("payment",payment).get("payment_status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE payment_id=?",Integer.class,payment)).isZero();
    }

    @Test void committedFixtureCleanupAfterAssertionFailurePreservesCatalogAndMemberBaseline() {
        var before = counts();
        var extra = new MaintenanceTestResources(jdbc);
        assertThatThrownBy(() -> {
            try {
                long user = extra.insert("user_account", "INSERT INTO user_account(email,password_hash,full_name,role_id,status,version) "
                        + "OUTPUT INSERTED.id VALUES (?,'test-only-non-credential','BE06 cleanup',"
                        + "(SELECT id FROM role WHERE code='MEMBER'),'ACTIVE',0)", code()+"@example.test");
                jdbc.update("INSERT INTO member_profile(user_id,member_code,joined_on) VALUES (?,?,?)",user,code(),TODAY);
                extra.remember("member_profile",user);
                long sport = extra.insert("sport","INSERT INTO sport(code,name,venue_type) OUTPUT INSERTED.id VALUES (?,?,'INDOOR')",code(),code());
                extra.insert("sport_package","INSERT INTO sport_package(code,name,sport_id,training_format,duration_days,session_count,price_amount,session_minutes) "
                        + "OUTPUT INSERTED.id VALUES (?,? ,?,'SELF_TRAINING',30,10,100,60)",code(),"BE06 cleanup",sport);
                throw new AssertionError("Synthetic assertion after committed fixtures");
            } finally { extra.close(); }
        }).isInstanceOf(AssertionError.class).hasMessage("Synthetic assertion after committed fixtures");
        assertThat(counts()).isEqualTo(before);
    }

    @Test void expirationAndAtomicCreditDebitNeverModifyBalancesTwice() throws Exception {
        long item = registration("ACTIVE",OLD,TODAY.minusDays(1));
        var workers=pool();var barrier=new CyclicBarrier(2);
        Future<?> expire=workers.submit(() -> { barrier.await(20,TimeUnit.SECONDS); return maintenance.runDailyMaintenance(); });
        Future<Integer> consume=workers.submit(() -> {
            barrier.await(20,TimeUnit.SECONDS);
            return tx().execute(status -> registrations.consumeSession(item,"ACTIVE",TODAY.minusDays(1),NOW));
        });
        expire.get(30,TimeUnit.SECONDS);int debited=consume.get(30,TimeUnit.SECONDS);
        assertThat(debited).isBetween(0,1);
        assertThat(state("sport_package_registration",item)).isEqualTo("EXPIRED");
        assertThat(row("sport_package_registration",item).get("remaining_sessions")).isEqualTo(6-debited);
        assertThat(total(maintenance.runDailyMaintenance())).isZero();
    }
}
