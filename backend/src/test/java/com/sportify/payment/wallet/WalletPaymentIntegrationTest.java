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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MW-05: Wallet payment integration test reflecting the retired wallet feature.
 * Verifies that wallet payment mutations are rejected with WALLET_FEATURE_RETIRED,
 * role authorization is enforced, financial records remain un-mutated, and
 * standard payment methods (Bank Transfer, Cash) remain fully operational.
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
    void payingWithWalletIsRejectedWithWalletFeatureRetiredAndLeavesPaymentPending() throws Exception {
        fund(member, 500_000);
        long payment = pendingPackagePayment(member);

        // Attempting to pay with wallet must fail with WALLET_FEATURE_RETIRED
        pay(member, payment)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Sportify Wallet has been retired. Please pay using Bank Transfer, Cash, or QR Code."));

        // Verify no financial mutation occurred
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("500000");
        assertThat(count("SELECT COUNT(*) FROM wallet_ledger_entry e JOIN member_wallet w ON w.id=e.wallet_id"
                + " WHERE w.member_id=? AND e.entry_type='PURCHASE_DEBIT'", member)).isZero();
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, payment))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id=?", String.class, payment))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id=?", payment)).isZero();
        assertThat(count("SELECT COUNT(*) FROM payment_wallet_settlement WHERE payment_id=?", payment)).isZero();
    }

    @Test
    void roleAuthorizationIsEnforcedOnWalletPaymentEndpoint() throws Exception {
        long payment = pendingPackagePayment(member);

        // Unauthenticated -> 401
        mvc.perform(post("/api/v1/member/wallet/payments/" + payment))
                .andExpect(status().isUnauthorized());

        // Receptionist -> 403 (requires MEMBER role)
        mvc.perform(post("/api/v1/member/wallet/payments/" + payment).with(user(details(receptionist))))
                .andExpect(status().isForbidden());

        // Coach -> 403
        long coach = account("COACH");
        mvc.perform(post("/api/v1/member/wallet/payments/" + payment).with(user(details(coach))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unchangedBankTransferAndCashPaymentBehavior() throws Exception {
        // Pending purchase can be paid via Bank Transfer
        long payment = pendingPackagePayment(member);
        mvc.perform(put("/api/v1/payments/" + payment + "/confirm").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"BANK_TRANSFER\",\"amountReceived\":300000,\"referenceCode\":\"FT" + payment + "\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, payment))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id=?", String.class, payment))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id=?", payment)).isEqualTo(1);

        // Another purchase can be paid via Cash
        long paymentCash = pendingPackagePayment(otherMember);
        mvc.perform(put("/api/v1/payments/" + paymentCash + "/confirm").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"CASH\",\"amountReceived\":300000}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, paymentCash))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM sport_package_registration WHERE payment_id=?", String.class, paymentCash))
                .isEqualTo("ACTIVE");
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE payment_id=?", paymentCash)).isEqualTo(1);
    }

    @Test
    void staffAndMembersCannotPickWalletThroughTheExistingChannels() throws Exception {
        long payment = pendingPackagePayment(member);
        // Staff cannot confirm with WALLET method
        mvc.perform(put("/api/v1/payments/" + payment + "/confirm").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"method\":\"WALLET\",\"amountReceived\":300000,\"referenceCode\":\"X1\"}")).andExpect(status().isBadRequest());

        // Member cannot switch method to WALLET
        mvc.perform(put("/api/v1/payments/" + payment + "/method").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"WALLET\"}")).andExpect(status().isBadRequest());

        // But member can switch method to supported methods (e.g. BANK_TRANSFER)
        mvc.perform(put("/api/v1/payments/" + payment + "/method").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethod\":\"BANK_TRANSFER\"}")).andExpect(status().isOk());

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
