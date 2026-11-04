package com.sportify.demoqr;

import com.sportify.catalog.dto.PackageRegistrationRequest;
import com.sportify.catalog.dto.PackageRegistrationResponse;
import com.sportify.core.exception.ConflictException;
import com.sportify.demoqr.dto.LocalDemoCapabilityResponse;
import com.sportify.demoqr.dto.LocalDemoSettlementResponse;
import com.sportify.identity.dto.AuthRequest;
import com.sportify.identity.dto.AuthResponse;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.repository.FinanceReportRepository;
import com.sportify.payment.service.PaymentRefundService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared real HTTP/SQL contract for the legacy and separately guarded unified modes. */
abstract class LocalDemoWorkflow {
    @Autowired TestRestTemplate restTemplate;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired FinanceReportRepository financeReportRepository;
    @Autowired PaymentRefundService paymentRefundService;
    @Autowired PlatformTransactionManager transactionManager;

    private static final String DEMO_PASSWORD = "SportifyDemo2026!";

    protected String fixtureEmail(String email) { return email; }

    protected String login(String email) {
        email = fixtureEmail(email);
        var authReq = new AuthRequest(email, DEMO_PASSWORD);
        var response = restTemplate.postForEntity("/api/v1/auth/login", authReq, AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().getAccessToken();
    }

    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void executeCompleteLocalDemoWorkflowAgainstDisposableDatabase() throws Exception {
        // 1. Authenticate Member owner (an.gold@example.com) and another Member (mai.standard@example.com)
        String ownerToken = login("an.gold@example.com");
        String otherToken = login("mai.standard@example.com");

        // 2. Verify capability endpoint
        var capResponse = restTemplate.exchange(
                "/api/v1/payments/local-demo/capability",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)),
                LocalDemoCapabilityResponse.class
        );
        assertThat(capResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(capResponse.getBody()).isNotNull();
        assertThat(capResponse.getBody().enabled()).isTrue();
        assertThat(capResponse.getBody().mode()).isEqualTo("LOCAL_DEMO");
        assertThat(capResponse.getBody().contractVersion()).isEqualTo(1);

        // 3. Create real PENDING payment through official package registration workflow
        PackageRegistrationRequest regRequest = PackageRegistrationRequest.builder()
                .packageId(1L)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        var regResponse = restTemplate.exchange(
                "/api/v1/packages/registrations",
                HttpMethod.POST,
                new HttpEntity<>(regRequest, authHeaders(ownerToken)),
                PackageRegistrationResponse.class
        );

        assertThat(regResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regResponse.getBody()).isNotNull();
        Long paymentId = regResponse.getBody().getPaymentId();
        assertThat(paymentId).isNotNull().isGreaterThan(0L);

        // Baseline financial metrics before confirmation
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime monthEnd = monthStart.plusMonths(1);

        FinanceReportRepository.Totals simulatedBefore = financeReportRepository.simulatedTotals(monthStart, monthEnd);
        FinanceReportRepository.Totals paidBefore = financeReportRepository.paidTotals(monthStart, monthEnd);
        Integer cashEventsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.cash_event WHERE payment_id = ?",
                Integer.class,
                paymentId
        );
        assertThat(cashEventsBefore).isEqualTo(0);

        // 4. Negative test: Unauthenticated POST must be rejected (401)
        var unauthResponse = restTemplate.postForEntity(
                "/api/v1/payments/" + paymentId + "/demo-confirm",
                null,
                String.class
        );
        assertThat(unauthResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 5. Negative test: Another Member must not confirm this payment (404 / unavailable)
        var wrongOwnerResponse = restTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/demo-confirm",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(otherToken)),
                String.class
        );
        assertThat(wrongOwnerResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify payment is still PENDING after negative tests
        String statusStillPending = jdbcTemplate.queryForObject(
                "SELECT payment_status FROM dbo.payment WHERE id = ?",
                String.class,
                paymentId
        );
        assertThat(statusStillPending).isEqualTo("PENDING");

        // 6. Owner confirmation with Concurrency Test (SQL-002 / QR-MVP-006)
        // Two concurrent threads firing confirmation simultaneously on the same pending payment
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ResponseEntity<LocalDemoSettlementResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return restTemplate.exchange(
                        "/api/v1/payments/" + paymentId + "/demo-confirm",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("amount", 1, "memberId", -1, "settlementType", "ACTUAL"), authHeaders(ownerToken)),
                        LocalDemoSettlementResponse.class
                );
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Trigger both threads simultaneously

        List<ResponseEntity<LocalDemoSettlementResponse>> responses = new ArrayList<>();
        for (var f : futures) {
            responses.add(f.get(15, TimeUnit.SECONDS));
        }
        executor.shutdown();

        // Both requests must complete cleanly with HTTP 200 OK (one settles, the other idempotent replay)
        for (var res : responses) {
            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(res.getBody()).isNotNull();
            assertThat(res.getBody().paymentId()).isEqualTo(paymentId);
            assertThat(res.getBody().status()).isEqualTo("SUCCESS");
            assertThat(res.getBody().settlementType()).isEqualTo("SIMULATED");
        }

        LocalDemoSettlementResponse outcome = responses.get(0).getBody();
        assertThat(outcome.simulatedTransactionId()).isNotNull().isGreaterThan(0L);
        assertThat(outcome.referenceCode()).isNotNull().startsWith("SIM-QR-" + paymentId);
        assertThat(outcome.invoiceId()).isNotNull().isGreaterThan(0L);
        assertThat(outcome.invoiceNumber()).isNotNull().isNotBlank();

        // 7. Additional Idempotent sequential replay check
        var repeatResponse = restTemplate.exchange(
                "/api/v1/payments/" + paymentId + "/demo-confirm",
                HttpMethod.POST,
                new HttpEntity<>(authHeaders(ownerToken)),
                LocalDemoSettlementResponse.class
        );
        assertThat(repeatResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        LocalDemoSettlementResponse repeatOutcome = repeatResponse.getBody();
        assertThat(repeatOutcome).isNotNull();
        assertThat(repeatOutcome.simulatedTransactionId()).isEqualTo(outcome.simulatedTransactionId());
        assertThat(repeatOutcome.invoiceId()).isEqualTo(outcome.invoiceId());
        assertThat(repeatOutcome.invoiceNumber()).isEqualTo(outcome.invoiceNumber());

        var refreshed = restTemplate.exchange("/api/v1/payments/" + paymentId, HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), PaymentResponse.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody()).isNotNull();
        assertThat(refreshed.getBody().getInvoiceId()).isEqualTo(outcome.invoiceId());
        assertThat(refreshed.getBody().getInvoiceNumber()).isEqualTo(outcome.invoiceNumber());
        assertThat(refreshed.getBody().getSettlementType()).isEqualTo("SIMULATED");

        var receipt = restTemplate.exchange("/api/v1/invoices/" + outcome.invoiceId(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(ownerToken)), String.class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 8. SQL Server database invariants verification
        // 8.1 Payment table
        var paymentRow = jdbcTemplate.queryForMap(
                "SELECT payment_status, settlement_type, settlement_provider, simulated_transaction_id, amount "
                        + "FROM dbo.payment WHERE id = ?",
                paymentId
        );
        assertThat(paymentRow.get("payment_status")).isEqualTo("SUCCESS");
        assertThat(paymentRow.get("settlement_type")).isEqualTo("SIMULATED");
        assertThat(paymentRow.get("settlement_provider")).isEqualTo("SPORTIFY_SIMULATOR");
        assertThat(paymentRow.get("simulated_transaction_id")).isEqualTo(outcome.simulatedTransactionId());

        // 8.2 Exactly one simulated transaction
        Integer txCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.simulated_payment_transaction WHERE payment_id = ?",
                Integer.class,
                paymentId
        );
        assertThat(txCount).isEqualTo(1);

        // 8.3 Matching session exists and is SETTLED
        var sessionRow = jdbcTemplate.queryForMap(
                "SELECT state, active FROM dbo.simulated_qr_session WHERE payment_id = ? AND state = 'SETTLED'",
                paymentId
        );
        assertThat(sessionRow.get("state")).isEqualTo("SETTLED");
        assertThat(sessionRow.get("active")).isEqualTo(false);

        // 8.4 Entitlement activated
        String regStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM dbo.sport_package_registration WHERE payment_id = ?",
                String.class,
                paymentId
        );
        assertThat(regStatus).isEqualTo("ACTIVE");

        // 8.5 Paid invoice generated
        Integer invoiceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.invoice WHERE payment_id = ? AND status = 'PAID'",
                Integer.class,
                paymentId
        );
        assertThat(invoiceCount).isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.activity_log WHERE action = 'PAYMENT_CONFIRMED' AND entity_id = ?",
                Integer.class, paymentId)).isEqualTo(1);

        // 8.6 Financial separation: no cash_event created!
        Integer cashEventsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dbo.cash_event WHERE payment_id = ?",
                Integer.class,
                paymentId
        );
        assertThat(cashEventsAfter).isEqualTo(0);

        // 8.7 Real paid revenue in vw_paid_payment did not increase
        FinanceReportRepository.Totals paidAfter = financeReportRepository.paidTotals(monthStart, monthEnd);
        assertThat(paidAfter.count()).isEqualTo(paidBefore.count());
        assertThat(paidAfter.amount()).isEqualByComparingTo(paidBefore.amount());

        // 8.8 Simulated totals reflect the demo payment separately
        FinanceReportRepository.Totals simulatedAfter = financeReportRepository.simulatedTotals(monthStart, monthEnd);
        assertThat(simulatedAfter.count()).isEqualTo(simulatedBefore.count() + 1);
        BigDecimal expectedNewSimulatedTotal = simulatedBefore.amount().add((BigDecimal) paymentRow.get("amount"));
        assertThat(simulatedAfter.amount()).isEqualByComparingTo(expectedNewSimulatedTotal);

        // 8.9 FIN-001 / QR-MVP-021: Attempting to refund a simulated payment as real money MUST be rejected with ConflictException
        assertThatThrownBy(() ->
                new TransactionTemplate(transactionManager).execute(txStatus ->
                        paymentRefundService.markRefunded(paymentId, "REF-ILLEGAL-01", (BigDecimal) paymentRow.get("amount"))
                )
        ).isInstanceOf(ConflictException.class)
                .hasMessageContaining("was settled via QR simulation and cannot be refunded as real money");

        // Verify status remains SUCCESS, not REFUNDED
        String statusAfterRefundAttempt = jdbcTemplate.queryForObject(
                "SELECT payment_status FROM dbo.payment WHERE id = ?",
                String.class,
                paymentId
        );
        assertThat(statusAfterRefundAttempt).isEqualTo("SUCCESS");
    }
}
