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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.sportify.payment.service.VietQrBankDirectory;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.Mockito.when;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MW-08 wallet history on real SQL Server: complete own timeline, honest pending states, filters, reconciliation. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.wallet.simulated-topups-enabled=true",
        "app.payment.bank-transfer.live-instructions-enabled=true", "app.payment.bank-transfer.bank-bin=970436",
        "app.payment.bank-transfer.account-number=0011009990001", "app.payment.bank-transfer.account-name=SPORTIFYDEMOCENTER"})
class WalletHistoryIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    static final VietQrBankDirectory.Bank BANK = new VietQrBankDirectory.Bank("970436", "VCB", "Vietcombank", true);
    @MockitoBean VietQrBankDirectory directory;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired WalletLedgerService ledger;

    private final String marker = "whi" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember, receptionist;

    @BeforeEach
    void fixture() {
        when(directory.supported("970436")).thenReturn(Optional.of(BANK));
        when(directory.banks()).thenReturn(List.of(BANK));
        member = member();
        otherMember = member();
        receptionist = account("RECEPTIONIST");
    }

    @Test
    void timelineListsEveryOwnRecordOnceWithHonestStatesAndReconciles() throws Exception {
        // 1. Confirmed actual top-up (400,000)
        var credit = ledger.apply(new Operation(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, BigDecimal.valueOf(400_000),
                marker + "-c1", "TOPUP", 1, "Top-up", null));
        long confirmed = insertHistoricalTopUp(member, 400_000, "CONFIRMED", credit.entry().id(), "FTH" + marker, receptionist);

        // 2. Confirmed simulated demo top-up (30,000)
        var demoCredit = ledger.apply(new Operation(member, EntryType.TOPUP_CREDIT, Pool.SIMULATED, BigDecimal.valueOf(30_000),
                marker + "-c2", "TOPUP", 2, "Demo Top-up", null));
        long demo = insertHistoricalDemoTopUp(member, 30_000, "CONFIRMED", demoCredit.entry().id(), receptionist);

        // 3. Approved withdrawal reserving 100,000
        var reserve = ledger.apply(new Operation(member, EntryType.WITHDRAWAL_RESERVE, Pool.ACTUAL, BigDecimal.valueOf(100_000),
                marker + "-r1", "WITHDRAWAL", 3, "Withdrawal reserve", null));
        long held = insertHistoricalWithdrawal(member, 100_000, "APPROVED", reserve.entry().id(), receptionist);

        // Unrelated record for other member
        insertHistoricalTopUp(otherMember, 70_000, "PENDING", null, null, null);

        // Member queries history
        mvc.perform(get("/api/v1/member/wallet/history").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.reconciled").value(true))
                .andExpect(jsonPath("$.availableBalance").value(300000))
                .andExpect(jsonPath("$.reservedBalance").value(100000))
                .andExpect(jsonPath("$.page.content[?(@.id == " + held + " && @.type == 'WITHDRAWAL')].status").value("APPROVED"))
                .andExpect(jsonPath("$.page.content[?(@.id == " + held + " && @.type == 'WITHDRAWAL')].detail").value("To Vietcombank ****4567"))
                .andExpect(jsonPath("$.page.content[?(@.id == " + demo + " && @.type == 'TOPUP')].pool").value("SIMULATED"));

        String body = mvc.perform(get("/api/v1/member/wallet/history").with(user(details(member)))).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("0011001234567");
    }

    @Test
    void filtersAndPagingWorkAndOtherMembersRowsNeverAppear() throws Exception {
        var credit = ledger.apply(new Operation(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, BigDecimal.valueOf(20_000),
                marker + "-f1", "TOPUP", 10, "Top-up", null));
        long a = insertHistoricalTopUp(member, 20_000, "CONFIRMED", credit.entry().id(), "FTF" + marker, receptionist);
        insertHistoricalTopUp(member, 25_000, "PENDING", null, null, null);
        insertHistoricalTopUp(otherMember, 99_000, "ACTUAL_PENDING", null, null, null);

        mvc.perform(get("/api/v1/member/wallet/history?type=TOPUP&status=PENDING").with(user(details(member))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].amount").value(25000));

        mvc.perform(get("/api/v1/member/wallet/history?status=CONFIRMED").with(user(details(member))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].id").value(a));

        mvc.perform(get("/api/v1/member/wallet/history").with(user(details(otherMember))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].amount").value(99000));
    }

    @Test
    void roleAuthorizationIsEnforcedOnWalletHistory() throws Exception {
        // Unauthenticated -> 401
        mvc.perform(get("/api/v1/member/wallet/history"))
                .andExpect(status().isUnauthorized());

        // Receptionist -> 403
        mvc.perform(get("/api/v1/member/wallet/history").with(user(details(receptionist))))
                .andExpect(status().isForbidden());
    }

    private long insertHistoricalTopUp(long memberId, long amount, String status, Long ledgerEntryId, String bankRef, Long reviewedBy) {
        String key = "hist-tu-" + marker + "-" + ++serial;
        String realStatus = status.equals("ACTUAL_PENDING") ? "PENDING" : status;
        jdbc.update("""
                INSERT INTO wallet_topup (member_id, mode, amount, status, beneficiary_bin, beneficiary_account, beneficiary_name,
                                          ledger_entry_id, bank_reference, reviewed_by, reviewed_at, idempotency_key, created_at, updated_at, received_amount)
                VALUES (?, 'ACTUAL', ?, ?, '970436', '0011009990001', N'SPORTIFYDEMOCENTER',
                        ?, ?, ?, CASE WHEN ? IS NULL THEN NULL ELSE SYSDATETIME() END, ?, SYSDATETIME(), SYSDATETIME(),
                        CASE WHEN ? = 'CONFIRMED' THEN ? ELSE NULL END)
                """, memberId, amount, realStatus, ledgerEntryId, bankRef, reviewedBy, reviewedBy, key, realStatus, amount);
        return jdbc.queryForObject("SELECT id FROM wallet_topup WHERE idempotency_key = ?", Long.class, key);
    }

    private long insertHistoricalDemoTopUp(long memberId, long amount, String status, Long ledgerEntryId, Long reviewedBy) {
        String key = "hist-demo-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO wallet_topup (member_id, mode, amount, status, ledger_entry_id, reviewed_by, reviewed_at,
                                          idempotency_key, created_at, updated_at, received_amount)
                VALUES (?, 'SIMULATED', ?, ?, ?, ?, CASE WHEN ? IS NULL THEN NULL ELSE SYSDATETIME() END,
                        ?, SYSDATETIME(), SYSDATETIME(), CASE WHEN ? = 'CONFIRMED' THEN ? ELSE NULL END)
                """, memberId, amount, status, ledgerEntryId, reviewedBy, reviewedBy, key, status, amount);
        return jdbc.queryForObject("SELECT id FROM wallet_topup WHERE idempotency_key = ?", Long.class, key);
    }

    private long insertHistoricalWithdrawal(long memberId, long amount, String status, Long reserveEntryId, Long reviewedBy) {
        String key = "hist-wd-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO wallet_withdrawal (member_id, pool, amount, status, bank_bin, bank_name, account_number, account_holder,
                                               reserve_entry_id, reviewed_by, reviewed_at, idempotency_key, created_at, updated_at)
                VALUES (?, 'ACTUAL', ?, ?, '970436', N'Vietcombank', '0011001234567', N'HISTORY TEST',
                        ?, ?, CASE WHEN ? IS NULL THEN NULL ELSE SYSDATETIME() END, ?, SYSDATETIME(), SYSDATETIME())
                """, memberId, amount, status, reserveEntryId, reviewedBy, reviewedBy, key);
        return jdbc.queryForObject("SELECT id FROM wallet_withdrawal WHERE idempotency_key = ?", Long.class, key);
    }

    private String code() { return marker + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " history fixture")
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
}
