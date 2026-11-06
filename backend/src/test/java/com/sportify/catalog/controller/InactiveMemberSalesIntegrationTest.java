package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * E2E-BUG-04 (Flow 1 + Flow 3): the TARGET member must be ACTIVE for desk package and card sales, for confirming a
 * pending payment and for direct staff activation of a pending card or registration. E2E-D11 APPROVED: MEMBER_INACTIVE
 * (400), pending orders stay pending, no partial side effects. Real SQL Server transactions and HTTP security.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InactiveMemberSalesIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private final String marker = "b04" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private final List<ExecutorService> workerPools = new ArrayList<>();
    private Sport sport;
    private long packageId;
    private BigDecimal packagePrice;
    private long goldTierId;
    private BigDecimal goldPrice;
    private UserAccount manager;
    private UserAccount receptionist;
    private UserAccount member;

    @BeforeEach void fixture() {
        sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        packagePrice = new BigDecimal("250000.00");
        packageId = packages.saveAndFlush(SportPackage.builder().code(code()).name("B04 package").sport(sport)
                .trainingFormat(TrainingFormat.COACH_LED).durationDays(30).sessionCount(8).sessionMinutes(90)
                .priceAmount(packagePrice).isActive(true).build()).getId();
        var gold = jdbc.queryForMap("SELECT id, price FROM membership_card_tier WHERE code = 'GOLD'");
        goldTierId = ((Number) gold.get("id")).longValue();
        goldPrice = ((BigDecimal) gold.get("price")).setScale(2);
        manager = account("MANAGER");
        receptionist = account("RECEPTIONIST");
        member = member();
    }

    @AfterEach void cleanup() {
        assertThat(workerPools).allMatch(ExecutorService::isTerminated, "all concurrent writers stopped before cleanup");
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ") OR entity_id IN (" + own + ")", marker + "%", marker + "%");
        jdbc.update("DELETE FROM notification WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package_registration WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("UPDATE member_card SET replaces_card_id = NULL WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM member_card WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE member_id IN (" + own + "))", marker + "%");
        jdbc.update("DELETE FROM invoice WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM payment WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package WHERE sport_id = ?", sport.getId());
        jdbc.update("DELETE FROM sport WHERE id = ?", sport.getId());
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
    }

    // ---------------------------------------------------------------- B04-01, B04-02 desk sales

    @Test
    void b04_01_deskPackageSaleIsRejectedForAnInactiveMemberWithoutAnySideEffect() {
        deactivate(member);
        Ledger before = ledger();

        Outcome sale = deskPackageSale(member);

        assertThat(sale.status()).isEqualTo(400);
        assertThat(sale.code()).isEqualTo("MEMBER_INACTIVE");
        assertThat(ledger()).isEqualTo(before);
        assertThat(before.registrations()).isZero();
    }

    @Test
    void b04_02_deskCardSaleIsRejectedForAnInactiveMemberWithoutAnySideEffect() {
        deactivate(member);
        Ledger before = ledger();

        Outcome sale = deskCardSale(member);

        assertThat(sale.status()).isEqualTo(400);
        assertThat(sale.code()).isEqualTo("MEMBER_INACTIVE");
        assertThat(ledger()).isEqualTo(before);
        assertThat(before.cards()).isZero();
    }

    // ---------------------------------------------------------------- B04-03 pending confirmation

    @Test
    void b04_03_pendingPaymentCreatedWhileActiveCannotBeConfirmedAfterDeactivation() {
        long payment = onlinePendingPackage(member);
        deactivate(member);
        Ledger before = ledger();

        Outcome confirm = confirm(payment);

        assertThat(confirm.status()).isEqualTo(400);
        assertThat(confirm.code()).isEqualTo("MEMBER_INACTIVE");
        assertThat(ledger()).isEqualTo(before);
        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
        assertThat(registrationStatusForPayment(payment)).isEqualTo("PENDING_PAYMENT");
        assertThat(before.invoices()).isZero();
    }

    // ---------------------------------------------------------------- B04-04 existing behavior

    @Test
    void b04_04_activeMemberDeskSalesAndPendingConfirmationStillSucceedWithOneInvoiceEach() {
        Outcome packageSale = deskPackageSale(member);
        Outcome cardSale = deskCardSale(member);
        long pending = onlinePendingPackage(member);
        Outcome confirm = confirm(pending);

        assertThat(packageSale.status()).as(packageSale.body().toString()).isEqualTo(201);
        assertThat(cardSale.status()).as(cardSale.body().toString()).isEqualTo(201);
        assertThat(confirm.status()).as(confirm.body().toString()).isEqualTo(200);
        assertThat(paymentStatus(pending)).isEqualTo("SUCCESS");
        Ledger after = ledger();
        assertThat(after.successfulPayments()).isEqualTo(3);
        assertThat(after.invoices()).isEqualTo(3);
        assertThat(after.cards()).isEqualTo(1);
        assertThat(after.activeRegistrations()).isEqualTo(2);
    }

    @Test
    void memberRoleStillCannotConfirmPayments() {
        long payment = onlinePendingPackage(member);
        assertThat(http(put("/api/v1/payments/" + payment + "/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(write(confirmBody(payment))), member).status()).isEqualTo(403);
        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
    }

    // ---------------------------------------------------------------- B04-05 deactivation races

    @Test @Timeout(100)
    void b04_05_deactivationCommitsFirst_confirmationWaitsThenIsRejected() throws Exception {
        long payment = onlinePendingPackage(member);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Integer> deactivation = pool.submit(() -> writer().execute(status -> {
                firstSpid.set(count("SELECT @@SPID"));
                int changed = jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE id = ?", member.getId());
                held.countDown(); await(release);
                return changed;
            }));
            await(held);
            Future<Outcome> confirmation = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID"));
                return confirm(payment);
            }));
            awaitBlocked(secondSpid, firstSpid, "user_account");
            release.countDown();

            assertThat(deactivation.get(60, TimeUnit.SECONDS)).isEqualTo(1);
            Outcome rejected = confirmation.get(60, TimeUnit.SECONDS);
            assertThat(rejected.status()).isEqualTo(400);
            assertThat(rejected.code()).isEqualTo("MEMBER_INACTIVE");
        } finally { release.countDown(); stop(pool); }

        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
        assertThat(registrationStatusForPayment(payment)).isEqualTo("PENDING_PAYMENT");
        assertThat(ledger().invoices()).isZero();
    }

    @Test @Timeout(100)
    void b04_05_confirmationCommitsFirst_deactivationWaitsAndTheCommittedOrderIsConsistent() throws Exception {
        long payment = onlinePendingPackage(member);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Outcome> confirmation = pool.submit(() -> inWriter(() -> {
                firstSpid.set(count("SELECT @@SPID"));
                Outcome outcome = confirm(payment);
                held.countDown(); await(release);
                return outcome;
            }));
            await(held);
            Future<Outcome> deactivation = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID"));
                return http(patch("/api/v1/manager/users/" + member.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("status", "INACTIVE"))), manager);
            }));
            awaitBlocked(secondSpid, firstSpid, "user_account");
            release.countDown();

            assertThat(confirmation.get(60, TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(deactivation.get(60, TimeUnit.SECONDS).status()).isEqualTo(200);
        } finally { release.countDown(); stop(pool); }

        // Confirmation serialized before deactivation: one paid order, then an inactive account. No late activation.
        assertThat(paymentStatus(payment)).isEqualTo("SUCCESS");
        assertThat(ledger().invoices()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM user_account WHERE id = ?", String.class, member.getId())).isEqualTo("INACTIVE");
        assertThat(deskPackageSale(member).code()).isEqualTo("MEMBER_INACTIVE");
    }

    // ---------------------------------------------------------------- B04-06..09 direct staff activation (D11)

    @Test
    void b04_06_directPackageActivationIsRejectedForAnInactiveMemberWithoutAnySideEffect() {
        long payment = onlinePendingPackage(member);
        long registration = registrationForPayment(payment);
        deactivate(member);
        Ledger before = ledger();

        Outcome activation = activateRegistration(registration);

        assertThat(activation.status()).as(activation.body().toString()).isEqualTo(400);
        assertThat(activation.code()).isEqualTo("MEMBER_INACTIVE");
        assertThat(ledger()).isEqualTo(before);
        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
        assertThat(registrationStatusForPayment(payment)).isEqualTo("PENDING_PAYMENT");
        assertThat(before.invoices()).isZero();
    }

    @Test
    void b04_07_directCardActivationIsRejectedForAnInactiveMemberWithoutAnySideEffect() {
        long card = onlinePendingCard(member);
        long payment = cardPayment(card);
        deactivate(member);
        Ledger before = ledger();

        Outcome activation = activateCard(card);

        assertThat(activation.status()).as(activation.body().toString()).isEqualTo(400);
        assertThat(activation.code()).isEqualTo("MEMBER_INACTIVE");
        assertThat(ledger()).isEqualTo(before);
        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
        assertThat(cardStatus(card)).isEqualTo("PENDING_PAYMENT");
        assertThat(before.invoices()).isZero();
    }

    @Test
    void b04_08_directActivationStillSucceedsForAnActiveMember() {
        long payment = onlinePendingPackage(member);
        long registration = registrationForPayment(payment);
        long card = onlinePendingCard(member);

        Outcome packageActivation = activateRegistration(registration);
        Outcome cardActivation = activateCard(card);

        assertThat(packageActivation.status()).as(packageActivation.body().toString()).isEqualTo(200);
        assertThat(cardActivation.status()).as(cardActivation.body().toString()).isEqualTo(200);
        assertThat(registrationStatusForPayment(payment)).isEqualTo("ACTIVE");
        assertThat(cardStatus(card)).isEqualTo("ACTIVE");
        Ledger after = ledger();
        assertThat(after.successfulPayments()).isEqualTo(2);
        assertThat(after.invoices()).isEqualTo(2);
    }

    @Test @Timeout(100)
    void b04_09_deactivationCommitsFirst_directActivationWaitsThenIsRejected() throws Exception {
        long payment = onlinePendingPackage(member);
        long registration = registrationForPayment(payment);
        var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var firstSpid = new AtomicInteger(); var secondSpid = new AtomicInteger();
        var pool = newWorkerPool();
        try {
            Future<Integer> deactivation = pool.submit(() -> writer().execute(status -> {
                firstSpid.set(count("SELECT @@SPID"));
                int changed = jdbc.update("UPDATE user_account SET status = 'INACTIVE' WHERE id = ?", member.getId());
                held.countDown(); await(release);
                return changed;
            }));
            await(held);
            Future<Outcome> activation = pool.submit(() -> inWriter(() -> {
                secondSpid.set(count("SELECT @@SPID"));
                return activateRegistration(registration);
            }));
            awaitBlocked(secondSpid, firstSpid, "user_account");
            release.countDown();

            assertThat(deactivation.get(60, TimeUnit.SECONDS)).isEqualTo(1);
            Outcome rejected = activation.get(60, TimeUnit.SECONDS);
            assertThat(rejected.status()).isEqualTo(400);
            assertThat(rejected.code()).isEqualTo("MEMBER_INACTIVE");
        } finally { release.countDown(); stop(pool); }

        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
        assertThat(registrationStatusForPayment(payment)).isEqualTo("PENDING_PAYMENT");
        assertThat(ledger().invoices()).isZero();
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private record Ledger(int registrations, int activeRegistrations, int cards, int payments, int successfulPayments,
                          int invoices, int invoiceLines, int saleAudits) {}
    private record Outcome(int status, JsonNode body) {
        String code() { return body.path("code").asText(null); }
    }

    private Ledger ledger() {
        long id = member.getId();
        return new Ledger(
                count("SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ?", id),
                count("SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ? AND status = 'ACTIVE'", id),
                count("SELECT COUNT(*) FROM member_card WHERE member_id = ?", id),
                count("SELECT COUNT(*) FROM payment WHERE member_id = ?", id),
                count("SELECT COUNT(*) FROM payment WHERE member_id = ? AND payment_status = 'SUCCESS'", id),
                count("SELECT COUNT(*) FROM invoice WHERE member_id = ?", id),
                count("SELECT COUNT(*) FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE member_id = ?)", id),
                count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id IN (SELECT id FROM user_account WHERE email LIKE ?) AND action <> 'USER_STATUS_CHANGED'", marker + "%"));
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
                account.getId(), code(), LocalDate.now());
        return account;
    }
    private void deactivate(UserAccount target) {
        Outcome changed = http(patch("/api/v1/manager/users/" + target.getId() + "/status").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("status", "INACTIVE"))), manager);
        assertThat(changed.status()).as(changed.body().toString()).isEqualTo(200);
    }
    private Outcome deskPackageSale(UserAccount target) {
        return http(post("/api/v1/packages/registrations").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "memberId", target.getId(), "packageId", packageId, "channel", "RECEPTION", "paymentMethod", "CASH",
                "amountReceived", packagePrice, "expectedAmountDue", packagePrice))), receptionist);
    }
    private Outcome deskCardSale(UserAccount target) {
        return http(post("/api/v1/membership-cards/purchase").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "memberId", target.getId(), "tierId", goldTierId, "paymentMethod", "CASH",
                "amountReceived", goldPrice, "expectedAmountDue", goldPrice))), receptionist);
    }
    /** Online order by the member while ACTIVE: PENDING_PAYMENT registration and PENDING payment. */
    private long onlinePendingPackage(UserAccount target) {
        Outcome order = http(post("/api/v1/packages/registrations").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "packageId", packageId, "channel", "ONLINE", "paymentMethod", "BANK_TRANSFER"))), target);
        assertThat(order.status()).as(order.body().toString()).isEqualTo(201);
        long registration = order.body().path("id").asLong();
        Long payment = jdbc.queryForObject("SELECT payment_id FROM sport_package_registration WHERE id = ?", Long.class, registration);
        assertThat(payment).isNotNull();
        assertThat(paymentStatus(payment)).isEqualTo("PENDING");
        return payment;
    }
    /** Online card request by the member while ACTIVE: PENDING_PAYMENT card and PENDING payment. */
    private long onlinePendingCard(UserAccount target) {
        Outcome order = http(post("/api/v1/membership-cards/purchase").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "tierId", goldTierId, "paymentMethod", "BANK_TRANSFER"))), target);
        assertThat(order.status()).as(order.body().toString()).isEqualTo(201);
        long card = order.body().path("id").asLong();
        assertThat(cardStatus(card)).isEqualTo("PENDING_PAYMENT");
        assertThat(paymentStatus(cardPayment(card))).isEqualTo("PENDING");
        return card;
    }
    private long cardPayment(long card) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT payment_id FROM member_card WHERE id = ?", Long.class, card));
    }
    private String cardStatus(long card) {
        return jdbc.queryForObject("SELECT status FROM member_card WHERE id = ?", String.class, card);
    }
    private long registrationForPayment(long payment) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT id FROM sport_package_registration WHERE payment_id = ?", Long.class, payment));
    }
    private Outcome activateRegistration(long registration) {
        return http(put("/api/v1/packages/registrations/" + registration + "/activate").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("paymentMethod", "BANK_TRANSFER"))), receptionist);
    }
    private Outcome activateCard(long card) {
        return http(put("/api/v1/membership-cards/" + card + "/activate").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("paymentMethod", "BANK_TRANSFER"))), receptionist);
    }
    private Map<String, Object> confirmBody(long payment) {
        return Map.of("method", "BANK_TRANSFER", "referenceCode", "FT" + marker.toUpperCase(Locale.ROOT),
                "amountReceived", jdbc.queryForObject("SELECT amount FROM payment WHERE id = ?", BigDecimal.class, payment));
    }
    private Outcome confirm(long payment) {
        return http(put("/api/v1/payments/" + payment + "/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(write(confirmBody(payment))), receptionist);
    }
    private String paymentStatus(long payment) {
        return jdbc.queryForObject("SELECT payment_status FROM payment WHERE id = ?", String.class, payment);
    }
    private String registrationStatusForPayment(long payment) {
        return jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id = ?", String.class, payment);
    }
    private int count(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, args)); }
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
    private TransactionTemplate writer() {
        var template = new TransactionTemplate(transactions);
        template.setTimeout(60);
        return template;
    }
    private Outcome inWriter(java.util.function.Supplier<Outcome> work) {
        return writer().execute(status -> {
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
