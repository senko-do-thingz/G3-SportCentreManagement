package com.sportify.catalog.service.impl;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.service.PaymentVerificationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real migrated SQL Server only. All fixtures are unique, committed for worker visibility and removed by owner id. */
@ActiveProfiles(value = {"dev", "test"}, inheritProfiles = false)
@Import(ScheduledMaintenanceIntegrationTest.TimeConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduledMaintenanceIntegrationTest extends AbstractIntegrationTest {
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
    @Autowired MembershipCardService cardService;
    @Autowired SportPackageService packageService;
    @Autowired PaymentVerificationService verification;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager em;
    @MockitoSpyBean MemberCardRepository cards;
    @MockitoSpyBean SportPackageRegistrationRepository registrations;
    private final String marker = "be06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, manager, sport, pack;
    private final List<ExecutorService> pools = new ArrayList<>();

    @BeforeEach void fixtures() {
        member = user("MEMBER"); manager = user("MANAGER");
        jdbc.update("INSERT INTO member_profile(user_id,member_code,joined_on) VALUES (?,?,?)", member, code(), TODAY);
        sport = id("INSERT INTO sport(code,name,venue_type) OUTPUT INSERTED.id VALUES (?,?,'INDOOR')", code(), code());
        pack = id("INSERT INTO sport_package(code,name,sport_id,training_format,duration_days,session_count,"
                + "price_amount,session_minutes) OUTPUT INSERTED.id VALUES (?,?,?,'SELF_TRAINING',30,10,100,60)",
                code(), "BE06 package", sport);
    }
    @AfterEach void cleanup() throws Exception {
        for (var pool : pools) {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
        reset(cards, registrations);
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (?,?)", member, manager);
        jdbc.update("DELETE FROM check_in WHERE member_id=?", member);
        jdbc.update("DELETE FROM booking WHERE member_id=?", member);
        jdbc.update("DELETE FROM class_session WHERE sport_id=?", sport);
        jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE member_id=?)", member);
        jdbc.update("DELETE FROM invoice WHERE member_id=?", member);
        jdbc.update("DELETE FROM member_card WHERE member_id=?", member);
        jdbc.update("DELETE FROM sport_package_registration WHERE member_id=?", member);
        jdbc.update("DELETE FROM payment WHERE member_id=?", member);
        jdbc.update("DELETE FROM sport_package WHERE id=?", pack);
        jdbc.update("DELETE FROM sport WHERE id=?", sport);
        jdbc.update("DELETE FROM member_profile WHERE user_id=?", member);
        jdbc.update("DELETE FROM user_account WHERE id IN (?,?)", member, manager);
    }
    private String code() { return marker + (++serial); }
    private long id(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args)); }
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
        var pool = Executors.newFixedThreadPool(2); pools.add(pool); return pool;
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(20, TimeUnit.SECONDS)).as("Worker coordination deadline").isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
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
        doThrow(new IllegalStateException("Synthetic fourth statement failure")).when(registrations).expireActive(any(),any());
        assertThatThrownBy(maintenance::runDailyMaintenance).isInstanceOf(IllegalStateException.class);
        assertThat(row("member_card",c)).isEqualTo(beforeC); assertThat(row("member_card",e)).isEqualTo(beforeE);
        assertThat(row("sport_package_registration",r)).isEqualTo(beforeR); assertThat(row("sport_package_registration",x)).isEqualTo(beforeX);
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
        CountDownLatch read = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        if (card) doAnswer(call -> {
            Object loaded = call.callRealMethod();
            if (first.getAndSet(false)) { read.countDown(); await(resume); }
            return loaded;
        }).when(cards).findById(item);
        else doAnswer(call -> {
            Object loaded = call.callRealMethod();
            if (first.getAndSet(false)) { read.countDown(); await(resume); }
            return loaded;
        }).when(registrations).findById(item);
        var activation = pool().submit(() -> {
            assertThatThrownBy(() -> confirm(payment)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("CANCELLED");
        });
        try {
            await(read);
            assertThat(total(maintenance.runDailyMaintenance())).isEqualTo(1);
        } finally { resume.countDown(); }
        activation.get(30,TimeUnit.SECONDS);
        assertThat(state(table,item)).isEqualTo("CANCELLED");
        assertThat(row("payment",payment).get("payment_status")).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE payment_id=?",Integer.class,payment)).isZero();
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
