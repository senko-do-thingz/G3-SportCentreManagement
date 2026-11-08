package com.sportify.payment.wallet;

import com.sportify.AbstractIntegrationTest;
import com.sportify.catalog.entity.Sport;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportVenueType;
import com.sportify.catalog.entity.TrainingFormat;
import com.sportify.catalog.repository.SportPackageRepository;
import com.sportify.catalog.repository.SportRepository;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.wallet.WalletLedgerService.EntryType;
import com.sportify.payment.wallet.WalletLedgerService.Operation;
import com.sportify.payment.wallet.WalletLedgerService.Pool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MW-05 wallet settlement of an existing pending package purchase on real SQL Server: atomic debit + activation +
 * invoice, no partial purchase, exactly-once under replay and concurrency, staff channels unchanged.
 */
@AutoConfigureMockMvc
class WalletPaymentIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired SportRepository sports;
    @Autowired SportPackageRepository packages;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WalletLedgerService ledger;

    private final String marker = "wpy" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember, receptionist, packageId;

    @BeforeEach
    void fixture() {
        Sport sport = sports.saveAndFlush(Sport.builder().code(code()).name(code()).venueType(SportVenueType.INDOOR).isActive(true).build());
        packageId = packages.saveAndFlush(SportPackage.builder().code(code()).name("Wallet package " + marker).sport(sport)
                .trainingFormat(TrainingFormat.COACH_LED).durationDays(30).sessionCount(8).sessionMinutes(90)
                .priceAmount(new BigDecimal("300000.00")).isActive(true).build()).getId();
        member = member();
        otherMember = member();
        receptionist = account("RECEPTIONIST");
    }

    @Test
    void walletPaysAPendingPackageOnceActivatesItAndIssuesAnInvoice() throws Exception {
        fund(member, 500_000);
        long payment = pendingPackagePayment(member);
        pay(member, payment).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.method").value("WALLET"))
                .andExpect(jsonPath("$.availableAfter").value(200000))
                .andExpect(jsonPath("$.invoiceNumber").exists())
                .andExpect(jsonPath("$.replay").value(false));
        pay(member, payment).andExpect(status().isOk()).andExpect(jsonPath("$.replay").value(true));
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("200000");
        assertThat(count("SELECT COUNT(*) FROM wallet_ledger_entry e JOIN member_wallet w ON w.id=e.wallet_id"
                + " WHERE w.member_id=? AND e.entry_type='PURCHASE_DEBIT'", member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT r.status FROM sport_package_registration r WHERE r.payment_id=?", String.class, payment))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id=?", payment)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT payment_method + ':' + payment_status FROM payment WHERE id=?", String.class, payment)).isEqualTo("WALLET:SUCCESS");
        assertThat(count("SELECT COUNT(*) FROM payment_wallet_settlement WHERE payment_id=?", payment)).isEqualTo(1);
    }

    @Test
    void insufficientBalanceLeavesNoDebitNoEntitlementAndThePaymentPending() throws Exception {
        fund(member, 299_999);
        long payment = pendingPackagePayment(member);
        pay(member, payment).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_FUNDS"));
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("299999");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, payment)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id=?", String.class, payment))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id=?", payment)).isZero();
        // The existing bank-transfer path still works for the same purchase.
        mvc.perform(put("/api/v1/payments/" + payment + "/confirm").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"BANK_TRANSFER\",\"amountReceived\":300000,\"referenceCode\":\"FT" + payment + "\"}"))
                .andExpect(status().isOk());
        pay(member, payment).andExpect(status().isConflict());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("299999");
    }

    @Test
    void demoFundsCannotPayAndAnotherMembersPaymentIsNotReachable() throws Exception {
        ledger.apply(new Operation(member, EntryType.TOPUP_CREDIT, Pool.SIMULATED, new BigDecimal("900000"), key(), "TEST", 1, "demo", null));
        long payment = pendingPackagePayment(member);
        pay(member, payment).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_FUNDS"));
        fund(otherMember, 900_000);
        pay(otherMember, payment).andExpect(status().isNotFound());
        assertThat(ledger.balance(otherMember).actualAvailable()).isEqualByComparingTo("900000");
        mvc.perform(post("/api/v1/member/wallet/payments/" + payment).with(user(details(receptionist)))).andExpect(status().isForbidden());
    }

    @Test
    void concurrentWalletPaymentsOfOnePurchaseDebitOnce() throws Exception {
        fund(member, 1_000_000);
        long payment = pendingPackagePayment(member);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            results.add(pool.submit(() -> { start.await(); return pay(member, payment).andReturn().getResponse().getStatus(); }));
        }
        start.countDown();
        for (Future<Integer> result : results) assertThat(result.get()).isEqualTo(200);
        pool.shutdown();
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("700000");
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id=?", payment)).isEqualTo(1);
    }

    @Test
    void walletPaymentWaitsWhileAnotherWalletOperationIsInFlight() throws Exception {
        fund(member, 500_000);
        long payment = pendingPackagePayment(member);
        // CODEOWNER rule: one blocking wallet operation per Member (here a pending top-up holds the slot).
        jdbc.update("INSERT INTO wallet_pending_operation (member_id, operation_type, operation_id, created_at) VALUES (?, 'TOPUP', 1, SYSDATETIME())", member);
        pay(member, payment).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("WALLET_OPERATION_IN_FLIGHT"));
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("500000");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, payment)).isEqualTo("PENDING");
        jdbc.update("DELETE FROM wallet_pending_operation WHERE member_id=?", member);
        pay(member, payment).andExpect(status().isOk());
    }

    @Test
    void staffAndMembersCannotPickWalletThroughTheExistingChannels() throws Exception {
        long payment = pendingPackagePayment(member);
        mvc.perform(put("/api/v1/payments/" + payment + "/confirm").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WALLET\",\"amountReceived\":300000,\"referenceCode\":\"X1\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/payments/" + payment + "/method").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"WALLET\"}")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, payment)).isEqualTo("PENDING");
    }

    private long pendingPackagePayment(long memberId) throws Exception {
        String body = mvc.perform(post("/api/v1/packages/registrations").with(user(details(memberId))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"packageId\":" + packageId + ",\"channel\":\"ONLINE\",\"paymentMethod\":\"BANK_TRANSFER\"}"))
                .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        assertThat(body).isNotBlank();
        return jdbc.queryForObject("SELECT TOP 1 payment_id FROM sport_package_registration WHERE member_id=? ORDER BY id DESC", Long.class, memberId);
    }

    private ResultActions pay(long memberId, long payment) throws Exception {
        return mvc.perform(post("/api/v1/member/wallet/payments/" + payment).with(user(details(memberId))));
    }

    private void fund(long memberId, long amount) {
        ledger.apply(new Operation(memberId, EntryType.TOPUP_CREDIT, Pool.ACTUAL, BigDecimal.valueOf(amount), key(), "TEST", 1, "fixture", null));
    }

    private String key() { return marker + "-k" + ++serial; }
    private String code() { return marker + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " wallet pay fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build()).getId();
    }

    private long member() {
        long id = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)", id, code(), LocalDate.now());
        return id;
    }

    private CustomUserDetails details(long id) {
        return new CustomUserDetails(new TransactionTemplate(transactions).execute(s -> {
            UserAccount account = users.findById(id).orElseThrow();
            org.hibernate.Hibernate.initialize(account.getRole());
            return account;
        }));
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
}
