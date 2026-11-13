package com.sportify.payment;

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

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * PR #329: a Member cancellation racing a settlement must leave exactly one consistent outcome, and a channel checkout
 * that has started (Sportify demo QR scanned and authorised) must block the cancellation. Real SQL Server and HTTP.
 */
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentCancellationRaceIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private final String marker = "pc9" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private Sport sport;
    private long packageId;
    private UserAccount receptionist;
    private UserAccount member;

    @BeforeEach void fixture() {
        sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        packageId = packages.saveAndFlush(SportPackage.builder().code(code()).name("PR329 package").sport(sport)
                .trainingFormat(TrainingFormat.COACH_LED).durationDays(30).sessionCount(8).sessionMinutes(90)
                .priceAmount(new BigDecimal("300000.00")).isActive(true).build()).getId();
        receptionist = account("RECEPTIONIST");
        member = member();
    }

    @AfterEach void cleanup() {
        // QR sessions are audit records a trigger keeps forever, so rows they reference stay in the disposable database.
        String own = "SELECT id FROM user_account WHERE email LIKE ?";
        String keep = " AND NOT EXISTS (SELECT 1 FROM simulated_qr_session s WHERE s.owner_id = user_account.id)";
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM notification WHERE user_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE member_id IN (" + own + "))", marker + "%");
        jdbc.update("DELETE FROM invoice WHERE member_id IN (" + own + ")", marker + "%");
        jdbc.update("DELETE FROM sport_package_registration WHERE member_id IN (" + own + keep + ")", marker + "%");
        jdbc.update("DELETE FROM payment WHERE member_id IN (" + own + keep + ")", marker + "%");
        if (count("SELECT COUNT(*) FROM sport_package_registration WHERE package_id = ?", packageId) == 0) {
            jdbc.update("DELETE FROM sport_package WHERE id = ?", packageId);
            jdbc.update("DELETE FROM sport WHERE id = ?", sport.getId());
        }
        jdbc.update("DELETE FROM member_profile WHERE user_id IN (" + own + keep + ")", marker + "%");
        jdbc.update("DELETE FROM user_account WHERE email LIKE ?" + keep, marker + "%");
    }

    @Test @Timeout(180)
    void cancelRacingReceptionistConfirmationEndsInExactlyOneConsistentOutcome() throws Exception {
        for (int round = 0; round < 5; round++) {
            member = member();
            long payment = paymentOf(order());
            var start = new CyclicBarrier(2);
            var pool = Executors.newFixedThreadPool(2);
            Outcome cancel;
            Outcome confirm;
            try {
                Future<Outcome> cancelling = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return cancel(payment); });
                Future<Outcome> confirming = pool.submit(() -> { start.await(30, TimeUnit.SECONDS); return confirm(payment); });
                cancel = cancelling.get(60, TimeUnit.SECONDS);
                confirm = confirming.get(60, TimeUnit.SECONDS);
            } finally { stop(pool); }

            assertThat(List.of(cancel.status(), confirm.status())).as("round %d: %s / %s", round, cancel.body(), confirm.body())
                    .containsExactlyInAnyOrder(200, 409);
            String status = jdbc.queryForObject("SELECT payment_status FROM payment WHERE id = ?", String.class, payment);
            String item = jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id = ?", String.class, payment);
            int invoices = count("SELECT COUNT(*) FROM invoice WHERE payment_id = ?", payment);
            if (cancel.status() == 200) {
                assertThat(status).isEqualTo("CANCELLED");
                assertThat(item).isEqualTo("CANCELLED");
                assertThat(invoices).isZero();
            } else {
                assertThat(status).isEqualTo("SUCCESS");
                assertThat(item).isEqualTo("ACTIVE");
                assertThat(invoices).isEqualTo(1);
            }
        }
    }

    @Test
    void aSettledPaymentCannotBeCancelledAndACancelledPaymentCannotBeSettled() {
        long settled = paymentOf(order());
        assertThat(confirm(settled).status()).isEqualTo(200);
        assertThat(cancel(settled).status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id = ?", String.class, settled)).isEqualTo("SUCCESS");

        long cancelled = paymentOf(order(LocalDate.now().plusDays(3)));
        assertThat(cancel(cancelled).status()).isEqualTo(200);
        assertThat(confirm(cancelled).status()).isEqualTo(409);
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id = ?", cancelled)).isZero();
    }

    @Test
    void anAuthorisedDemoQrSessionBlocksCancellationAndKeepsTheOrderPending() {
        long payment = paymentOf(order());
        UUID session = qrSession(payment);
        jdbc.update("UPDATE simulated_qr_session SET state='AUTHORIZED_PENDING_SETTLEMENT', version=version+1, "
                + "state_changed_at_utc=SYSUTCDATETIME() WHERE id=?", session.toString());

        Outcome cancel = cancel(payment);

        assertThat(cancel.status()).as(cancel.body().toString()).isEqualTo(409);
        assertThat(cancel.body().path("message").asText()).contains("already being settled");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id = ?", String.class, payment)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id = ?", String.class, payment))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT state FROM simulated_qr_session WHERE id = ?", String.class, session.toString()))
                .isEqualTo("AUTHORIZED_PENDING_SETTLEMENT");
    }

    @Test
    void cancellingClosesADisplayedButUnscannedDemoQr() {
        long payment = paymentOf(order());
        UUID session = qrSession(payment);

        Outcome cancel = cancel(payment);

        assertThat(cancel.status()).as(cancel.body().toString()).isEqualTo(200);
        var row = jdbc.queryForMap("SELECT state, active FROM simulated_qr_session WHERE id = ?", session.toString());
        assertThat(row.get("state")).isEqualTo("CANCELED");
        assertThat(row.get("active")).isEqualTo(false);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id = ?", String.class, payment)).isEqualTo("CANCELLED");
    }

    // ---------------------------------------------------------------- fixtures and helpers

    private record Outcome(int status, JsonNode body) { }

    private Outcome order() { return order(null); }
    private Outcome order(LocalDate startDate) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("packageId", packageId, "channel", "ONLINE", "paymentMethod", "BANK_TRANSFER"));
        if (startDate != null) body.put("startDate", startDate.toString());
        Outcome created = http(post("/api/v1/packages/registrations").contentType(MediaType.APPLICATION_JSON).content(write(body)), member);
        assertThat(created.status()).as(created.body().toString()).isEqualTo(201);
        return created;
    }
    private long paymentOf(Outcome order) {
        return jdbc.queryForObject("SELECT payment_id FROM sport_package_registration WHERE id = ?", Long.class, order.body().path("id").asLong());
    }
    private Outcome cancel(long payment) { return http(post("/api/v1/payments/" + payment + "/cancel"), member); }
    private Outcome confirm(long payment) {
        return http(put("/api/v1/payments/" + payment + "/confirm").contentType(MediaType.APPLICATION_JSON).content(write(Map.of(
                "method", "BANK_TRANSFER", "referenceCode", ("FT" + marker + payment).toUpperCase(Locale.ROOT),
                "amountReceived", jdbc.queryForObject("SELECT amount FROM payment WHERE id = ?", BigDecimal.class, payment)))), receptionist);
    }
    /** A displayed demo QR, inserted as the QR service does (the lifecycle trigger requires active PENDING version 0). */
    private UUID qrSession(long payment) {
        UUID id = UUID.randomUUID();
        byte[] verifier = new byte[32];
        RANDOM.nextBytes(verifier);
        jdbc.update("""
                DECLARE @now DATETIME2(7)=SYSUTCDATETIME();
                INSERT simulated_qr_session(id,payment_id,owner_id,bank_code,amount,currency,token_verifier,request_id,
                created_at_utc,expires_at_utc,state,state_changed_at_utc,active,version)
                SELECT ?,id,member_id,'VIETCOMBANK_DEMO',amount,'VND',?,?,@now,DATEADD(second,600,@now),'PENDING',@now,1,0
                FROM payment WHERE id=?
                """, id.toString(), HexFormat.of().formatHex(verifier), UUID.randomUUID().toString(), payment);
        return id;
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
    private static void stop(ExecutorService pool) throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
