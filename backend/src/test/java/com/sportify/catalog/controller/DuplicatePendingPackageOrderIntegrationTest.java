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

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * E2E-BUG-06 (Flow 1 + Flow 3): one online pending order per member, package and start date.
 * PROVISIONAL: the expected results follow E2E-D8 key K2 with "identical request returns the existing order (200),
 * incompatible request gets 409", PENDING CODEOWNER approval. No new expiry (E2E-D8b): the existing daily maintenance
 * job already cancels pending registrations older than 7 days. Real SQL Server transactions and HTTP security.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DuplicatePendingPackageOrderIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private final String marker = "b06" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private final List<ExecutorService> workerPools = new ArrayList<>();
    private Sport sport;
    private long packageId;
    private UserAccount receptionist;
    private UserAccount member;

    @BeforeEach void fixture() {
        sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        packageId = packages.saveAndFlush(SportPackage.builder().code(code()).name("B06 package").sport(sport)
                .trainingFormat(TrainingFormat.COACH_LED).durationDays(30).sessionCount(8).sessionMinutes(90)
                .priceAmount(new BigDecimal("300000.00")).isActive(true).build()).getId();
        receptionist = account("RECEPTIONIST");
        member = member();
    }

    @AfterEach void cleanup() {
        assertThat(workerPools).allMatch(ExecutorService::isTerminated, "all concurrent writers stopped before cleanup");
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM notification WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package_registration WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE member_id IN (" + own + "))", marker + "%");
        jdbc.update("DELETE FROM invoice WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM payment WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package WHERE sport_id = ?", sport.getId());
        jdbc.update("DELETE FROM sport WHERE id = ?", sport.getId());
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?", marker + "%");
    }

    @Test
    void b06_01_sequentialIdenticalOrderReturnsTheExistingPendingOrderWith200() {
        Outcome first = order(null);
        Outcome second = order(null);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.body().has("existingPendingOrder")).isFalse();
        assertThat(second.status()).isEqualTo(200);
        assertThat(second.body().path("existingPendingOrder").asBoolean()).isTrue();
        assertThat(second.body().path("id").asLong()).isEqualTo(first.body().path("id").asLong());
        assertThat(pendingOrders()).isEqualTo(1);
        assertThat(payments()).isEqualTo(1);
    }

    @Test @Timeout(120)
    void b06_02_tenSimultaneousIdenticalOrdersCreateExactlyOnePendingOrderAndPayment() throws Exception {
        var start = new CyclicBarrier(10);
        var pool = Executors.newFixedThreadPool(10);
        workerPools.add(pool);
        List<Outcome> outcomes = new ArrayList<>();
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return inWriter(() -> order(null)); }));
            }
            for (var future : futures) outcomes.add(future.get(90, TimeUnit.SECONDS));
        } finally { stop(pool); }

        assertThat(outcomes).extracting(Outcome::status).filteredOn(s -> s == 201).hasSize(1);
        assertThat(outcomes).extracting(Outcome::status).filteredOn(s -> s == 200).hasSize(9);
        assertThat(outcomes).extracting(o -> o.body().path("id").asLong()).containsOnly(outcomes.getFirst().body().path("id").asLong());
        assertThat(pendingOrders()).isEqualTo(1);
        assertThat(payments()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE member_id = ?", member.getId())).isZero();
    }

    @Test
    void b06_03_aDifferentStartDateIsALegitimateSeparateOrder() {
        Outcome today = order(LocalDate.now());
        Outcome nextWeek = order(LocalDate.now().plusDays(7));

        assertThat(today.status()).isEqualTo(201);
        assertThat(nextWeek.status()).isEqualTo(201);
        assertThat(pendingOrders()).isEqualTo(2);
        assertThat(payments()).isEqualTo(2);
    }

    @Test
    void b06_04_aChangedPriceSnapshotIsNotSilentlyReusedAndGets409() {
        Outcome first = order(null);
        jdbc.update("UPDATE sport_package SET price_amount = 350000 WHERE id = ?", packageId);

        Outcome changed = order(null);

        assertThat(first.status()).isEqualTo(201);
        assertThat(changed.status()).isEqualTo(409);
        assertThat(changed.code()).isEqualTo("PENDING_ORDER_EXISTS");
        assertThat(changed.body().path("message").asText()).contains(first.body().path("registrationCode").asText());
        assertThat(pendingOrders()).isEqualTo(1);
        assertThat(payments()).isEqualTo(1);
    }

    @Test
    void b06_05_afterTheWaitingPaymentFailsTheMemberCanOrderAgain() {
        Outcome first = order(null);
        long payment = paymentOf(first);
        Outcome failed = http(put("/api/v1/payments/" + payment + "/fail").contentType(MediaType.APPLICATION_JSON)
                .content(write(Map.of("reason", "Transfer not received"))), receptionist);
        assertThat(failed.status()).as(failed.body().toString()).isEqualTo(200);

        Outcome retry = order(null);

        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.body().path("id").asLong()).isNotEqualTo(first.body().path("id").asLong());
        assertThat(pendingOrders()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM payment WHERE member_id = ? AND payment_status = 'FAILED'", member.getId())).isEqualTo(1);
    }

    @Test
    void b06_06_aRepeatedSubmissionCannotProduceTwoConfirmedPaymentsOrInvoices() {
        Outcome first = order(null);
        Outcome repeat = order(null);
        long payment = paymentOf(first);
        assertThat(paymentOf(repeat)).isEqualTo(payment);

        Outcome confirmed = confirm(payment);
        Outcome again = confirm(payment);

        assertThat(confirmed.status()).as(confirmed.body().toString()).isEqualTo(200);
        assertThat(again.status()).isEqualTo(409);
        assertThat(count("SELECT COUNT(*) FROM payment WHERE member_id = ? AND payment_status = 'SUCCESS'", member.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE member_id = ?", member.getId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ? AND status = 'ACTIVE'", member.getId())).isEqualTo(1);
    }

    @Test
    void deskSalesAreNotAffectedByTheOnlineDuplicateRule() {
        assertThat(order(null).status()).isEqualTo(201);
        BigDecimal price = new BigDecimal("300000.00");
        Outcome desk = http(post("/api/v1/packages/registrations").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "memberId", member.getId(), "packageId", packageId, "channel", "RECEPTION", "paymentMethod", "CASH",
                "amountReceived", price, "expectedAmountDue", price))), receptionist);

        assertThat(desk.status()).as(desk.body().toString()).isEqualTo(201);
        assertThat(pendingOrders()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private record Outcome(int status, JsonNode body) {
        String code() { return body.path("code").asText(null); }
    }

    private Outcome order(LocalDate startDate) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("packageId", packageId, "channel", "ONLINE", "paymentMethod", "BANK_TRANSFER"));
        if (startDate != null) body.put("startDate", startDate.toString());
        return http(post("/api/v1/packages/registrations").contentType(MediaType.APPLICATION_JSON).content(write(body)), member);
    }
    private long paymentOf(Outcome order) {
        return jdbc.queryForObject("SELECT payment_id FROM sport_package_registration WHERE id = ?", Long.class, order.body().path("id").asLong());
    }
    private Outcome confirm(long payment) {
        return http(put("/api/v1/payments/" + payment + "/confirm").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "method", "BANK_TRANSFER", "referenceCode", "FT" + marker.toUpperCase(Locale.ROOT),
                "amountReceived", jdbc.queryForObject("SELECT amount FROM payment WHERE id = ?", BigDecimal.class, payment)))), receptionist);
    }
    private int pendingOrders() {
        return count("SELECT COUNT(*) FROM sport_package_registration WHERE member_id = ? AND status = 'PENDING_PAYMENT' AND channel = 'ONLINE'", member.getId());
    }
    private int payments() { return count("SELECT COUNT(*) FROM payment WHERE member_id = ?", member.getId()); }
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
    private Outcome inWriter(java.util.function.Supplier<Outcome> work) {
        var template = new TransactionTemplate(transactions);
        template.setTimeout(60);
        return template.execute(status -> {
            Outcome outcome = work.get();
            if (outcome.status() >= 400) status.setRollbackOnly();
            return outcome;
        });
    }
    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
