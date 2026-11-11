package com.sportify.payment.wallet;

import com.sportify.AbstractIntegrationTest;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MW-06: Wallet withdrawal integration test reflecting the retired wallet feature.
 * Verifies that all withdrawal mutations are rejected with WALLET_FEATURE_RETIRED,
 * role authorization is strictly enforced, historical withdrawal records and payout
 * accounts remain queryable in read-only mode, and financial balances remain unaltered.
 */
@AutoConfigureMockMvc
class WalletWithdrawalIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WalletLedgerService ledger;

    private final String marker = "wdr" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember, receptionist, manager;

    @BeforeEach
    void fixture() {
        member = member();
        otherMember = member();
        receptionist = account("RECEPTIONIST");
        manager = account("MANAGER");
    }

    @Test
    void allWithdrawalMutationsAreRejectedWithWalletFeatureRetired() throws Exception {
        fund(member, Pool.ACTUAL, 500_000);

        // 1. Save payout account is rejected
        mvc.perform(put("/api/v1/member/wallet/payout-account").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBin\":\"970436\",\"accountNumber\":\"0011001234567\",\"accountHolder\":\"NGUYEN TEST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet payout account management is no longer supported as the Wallet feature has been retired."));

        // 2. Create withdrawal is rejected
        mvc.perform(post("/api/v1/member/wallet/withdrawals").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":200000,\"pool\":\"ACTUAL\",\"confirmAccountLast4\":\"4567\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet withdrawal is no longer supported as the Wallet feature has been retired."));

        assertThat(count("SELECT COUNT(*) FROM wallet_withdrawal WHERE member_id=?", member)).isZero();

        // Insert historical withdrawal directly for staff mutation tests
        long id = insertHistoricalWithdrawal(member, 200_000, "PENDING");

        // 3. Approve withdrawal is rejected
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/approve").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Approval\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet withdrawal approval is no longer supported as the Wallet feature has been retired."));

        // 4. Reject withdrawal is rejected
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/reject").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Rejection\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet withdrawal rejection is no longer supported as the Wallet feature has been retired."));

        // 5. Complete withdrawal is rejected
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/complete").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"payoutReference\":\"PO123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet withdrawal completion is no longer supported as the Wallet feature has been retired."));

        // 6. Fail withdrawal is rejected
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/fail").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Failed transfer\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"))
                .andExpect(jsonPath("$.message").value("Wallet withdrawal processing is no longer supported as the Wallet feature has been retired."));

        // Balance remains intact
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("500000");
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_withdrawal WHERE id=?", String.class, id)).isEqualTo("PENDING");
    }

    @Test
    void roleAuthorizationIsEnforcedOnAllWithdrawalEndpoints() throws Exception {
        long id = insertHistoricalWithdrawal(member, 100_000, "PENDING");

        // Unauthenticated -> 401
        mvc.perform(put("/api/v1/member/wallet/payout-account").contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBin\":\"970436\",\"accountNumber\":\"0011001234567\",\"accountHolder\":\"NGUYEN TEST\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/member/wallet/withdrawals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":50000,\"pool\":\"ACTUAL\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/approve")).andExpect(status().isUnauthorized());

        // Receptionist cannot access member endpoints -> 403
        mvc.perform(put("/api/v1/member/wallet/payout-account").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBin\":\"970436\",\"accountNumber\":\"0011001234567\",\"accountHolder\":\"NGUYEN TEST\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/member/wallet/withdrawals").with(user(details(receptionist))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":50000,\"pool\":\"ACTUAL\"}")).andExpect(status().isForbidden());

        // Member cannot access receptionist endpoints -> 403
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/approve").with(user(details(member))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/wallet/withdrawals").with(user(details(member))))
                .andExpect(status().isForbidden());

        // Manager cannot approve receptionist withdrawals -> 403
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + id + "/approve").with(user(details(manager))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readOnlyPayoutAccountAndWithdrawalsRemainAccessibleWithMasking() throws Exception {
        // Direct insert of historical payout account
        jdbc.update("""
                INSERT INTO member_payout_account (member_id, bank_bin, bank_name, account_number, account_holder, updated_at)
                VALUES (?, '970436', N'Vietcombank', '0011001234567', N'NGUYEN TEST', SYSDATETIME())
                """, member);

        // Member reads own payout account (masked)
        mvc.perform(get("/api/v1/member/wallet/payout-account").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****4567"))
                .andExpect(jsonPath("$.accountHolder").value("NGUYEN TEST"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());

        // Other member without payout account gets 204
        mvc.perform(get("/api/v1/member/wallet/payout-account").with(user(details(otherMember))))
                .andExpect(status().isNoContent());

        // Insert historical withdrawals
        long id = insertHistoricalWithdrawal(member, 100_000, "PENDING");

        // Member reads own withdrawals
        mvc.perform(get("/api/v1/member/wallet/withdrawals").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].amount").value(100000.0));

        // Other member sees empty list
        mvc.perform(get("/api/v1/member/wallet/withdrawals").with(user(details(otherMember))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Receptionist reads queue and detail
        mvc.perform(get("/api/v1/reception/wallet/withdrawals").with(user(details(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].accountNumber").value("****4567"));

        mvc.perform(get("/api/v1/reception/wallet/withdrawals/" + id).with(user(details(receptionist))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("0011001234567"));
    }

    private long insertHistoricalWithdrawal(long memberId, long amount, String status) {
        String key = "hist-wd-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO wallet_withdrawal (member_id, pool, amount, status, bank_bin, bank_name, account_number, account_holder, idempotency_key, created_at, updated_at)
                VALUES (?, 'ACTUAL', ?, ?, '970436', N'Vietcombank', '0011001234567', N'NGUYEN TEST', ?, SYSDATETIME(), SYSDATETIME())
                """, memberId, amount, status, key);
        return jdbc.queryForObject("SELECT id FROM wallet_withdrawal WHERE idempotency_key = ?", Long.class, key);
    }

    private void fund(long memberId, Pool pool, long amount) {
        ledger.apply(new Operation(memberId, EntryType.TOPUP_CREDIT, pool, BigDecimal.valueOf(amount), marker + "-f" + ++serial, "TEST", 1, "fixture", null));
    }

    private String code() { return marker + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " withdrawal fixture")
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
