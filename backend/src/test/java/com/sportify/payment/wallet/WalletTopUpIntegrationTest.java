package com.sportify.payment.wallet;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.sportify.payment.service.VietQrBankDirectory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MW-04: Wallet top-up integration test reflecting the retired wallet feature.
 * Verifies that all wallet top-up mutations are rejected with WALLET_FEATURE_RETIRED,
 * role authorization is enforced, historical read-only lists and details remain accessible,
 * member data isolation is preserved, and financial balances remain unaltered.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.wallet.simulated-topups-enabled=true",
        "app.payment.bank-transfer.live-instructions-enabled=true", "app.payment.bank-transfer.bank-bin=" + WalletTopUpIntegrationTest.BIN,
        "app.payment.bank-transfer.account-number=0011009990001", "app.payment.bank-transfer.account-name=SPORTIFYDEMOCENTER"})
class WalletTopUpIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();
    static final String BIN = "970436";
    static final VietQrBankDirectory.Bank BANK = new VietQrBankDirectory.Bank(BIN, "VCB", "Vietcombank", true);

    @MockitoBean VietQrBankDirectory directory;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WalletLedgerService ledger;

    private final String marker = "wtu" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember, receptionist, otherReceptionist, manager;

    @BeforeEach
    void fixture() {
        member = member("@sportify.test");
        otherMember = member("@sportify.test");
        receptionist = account("RECEPTIONIST", "@sportify.test");
        otherReceptionist = account("RECEPTIONIST", "@sportify.test");
        manager = account("MANAGER", "@sportify.test");
        when(directory.supported(BIN)).thenReturn(Optional.of(BANK));
        when(directory.banks()).thenReturn(List.of(BANK));
    }

    @Test
    void allTopUpMutationsAreRejectedWithWalletFeatureRetired() throws Exception {
        // 1. Member create top-up is rejected
        create(member, 200_000, "ACTUAL", key())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet top-up is no longer supported as the Wallet feature has been retired."));

        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
        assertThat(ledger.entryCount(member)).isZero();
        assertThat(count("SELECT COUNT(*) FROM wallet_topup WHERE member_id=?", member)).isZero();

        // Insert historical topup directly for remaining mutation tests
        long topUpId = insertHistoricalTopUp(member, 150_000, "PENDING");

        // 2. Member cancel top-up is rejected
        mvc.perform(post("/api/v1/member/wallet/topups/" + topUpId + "/cancel").with(user(details(member))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet top-up cancellation is no longer supported as the Wallet feature has been retired."));

        // 3. Receptionist confirm top-up is rejected
        confirm(receptionist, topUpId, 150_000, "FT123456")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet top-up confirmation is no longer supported as the Wallet feature has been retired."));

        // 4. Receptionist reject top-up is rejected
        mvc.perform(post("/api/v1/reception/wallet/topups/" + topUpId + "/reject").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No longer valid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet top-up rejection is no longer supported as the Wallet feature has been retired."));

        // Verify status and balance remained unchanged
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_topup WHERE id=?", String.class, topUpId)).isEqualTo("PENDING");
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
        assertThat(ledger.entryCount(member)).isZero();
    }

    @Test
    void roleAuthorizationIsStrictlyEnforcedOnAllTopUpEndpoints() throws Exception {
        long topUpId = insertHistoricalTopUp(member, 100_000, "PENDING");

        // Unauthenticated mutations return 401
        mvc.perform(post("/api/v1/member/wallet/topups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100000,\"mode\":\"ACTUAL\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/member/wallet/topups/" + topUpId + "/cancel")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reception/wallet/topups/" + topUpId + "/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"receivedAmount\":100000,\"bankReference\":\"FT1\"}")).andExpect(status().isUnauthorized());

        // Receptionist cannot invoke member mutation endpoints
        mvc.perform(post("/api/v1/member/wallet/topups").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100000,\"mode\":\"ACTUAL\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/member/wallet/topups/" + topUpId + "/cancel").with(user(details(receptionist))))
                .andExpect(status().isForbidden());

        // Member cannot invoke receptionist endpoints
        confirm(member, topUpId, 100_000, "FT1").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reception/wallet/topups/" + topUpId + "/reject").with(user(details(member)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/wallet/topups").with(user(details(member)))).andExpect(status().isForbidden());

        // Manager cannot confirm receptionist topup (requires RECEPTIONIST)
        confirm(manager, topUpId, 100_000, "FTMGR").andExpect(status().isForbidden());
    }

    @Test
    void readOnlyHistoryAndDetailsRemainAccessibleWithMemberIsolation() throws Exception {
        long ownTopUp = insertHistoricalTopUp(member, 200_000, "PENDING");
        long otherTopUp = insertHistoricalTopUp(otherMember, 300_000, "PENDING");

        // Member reads own topups list
        mvc.perform(get("/api/v1/member/wallet/topups").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topUps[?(@.id == " + ownTopUp + ")].amount").value(200000.0))
                .andExpect(jsonPath("$.topUps[?(@.id == " + otherTopUp + ")]").doesNotExist());

        // Member reads own topup detail
        mvc.perform(get("/api/v1/member/wallet/topups/" + ownTopUp).with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownTopUp))
                .andExpect(jsonPath("$.amount").value(200000.0));

        // Member cannot read another member's topup detail (404)
        mvc.perform(get("/api/v1/member/wallet/topups/" + otherTopUp).with(user(details(member))))
                .andExpect(status().isNotFound());

        // Receptionist reads staff topup queue
        mvc.perform(get("/api/v1/reception/wallet/topups").with(user(details(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + ownTopUp + ")].status").value("PENDING"));

        // Receptionist reads staff topup detail
        mvc.perform(get("/api/v1/reception/wallet/topups/" + ownTopUp).with(user(details(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownTopUp))
                .andExpect(jsonPath("$.amount").value(200000.0));
    }

    @Test
    void databaseConstraintsProtectBeneficiaryIntegrity() {
        ledger.ensureWallet(member);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO wallet_topup (member_id, mode, amount, status, created_at, updated_at)"
                + " VALUES (?, 'ACTUAL', 200000, 'PENDING', SYSDATETIME(), SYSDATETIME())", member))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_wallet_topup_beneficiary");
        assertThat(ledger.entryCount(member)).isZero();
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
    }

    private ResultActions create(long id, long amount, String mode, String key) throws Exception {
        return mvc.perform(post("/api/v1/member/wallet/topups").with(user(details(id))).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + ",\"mode\":\"" + mode + "\"}"));
    }

    private ResultActions confirm(long staff, long id, long amount, String reference) throws Exception {
        String ref = reference == null ? "null" : "\"" + reference + "\"";
        return mvc.perform(post("/api/v1/reception/wallet/topups/" + id + "/confirm").with(user(details(staff)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receivedAmount\":" + amount + ",\"bankReference\":" + ref + "}"));
    }

    private long insertHistoricalTopUp(long memberId, long amount, String status) {
        String key = "hist-tu-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO wallet_topup (member_id, mode, amount, status, beneficiary_bin, beneficiary_account, beneficiary_name, idempotency_key, created_at, updated_at)
                VALUES (?, 'ACTUAL', ?, ?, '970436', '0011009990001', N'SPORTIFYDEMOCENTER', ?, SYSDATETIME(), SYSDATETIME())
                """, memberId, amount, status, key);
        return jdbc.queryForObject("SELECT id FROM wallet_topup WHERE idempotency_key = ?", Long.class, key);
    }

    private String key() { return marker + "-k" + ++serial; }

    private long account(String role, String domain) {
        return users.saveAndFlush(UserAccount.builder().email(marker + ++serial + domain).fullName(role + " topup fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build()).getId();
    }

    private long member(String domain) {
        long id = account("MEMBER", domain);
        jdbc.update("INSERT INTO member_profile(user_id, member_code, current_level, joined_on) VALUES (?, ?, 'BEGINNER', ?)",
                id, marker + ++serial, LocalDate.now());
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
