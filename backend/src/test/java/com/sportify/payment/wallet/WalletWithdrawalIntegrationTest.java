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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MW-06 withdrawals on real SQL Server: atomic reserve, approval is not payout, single release or payout, masking. */
@AutoConfigureMockMvc
class WalletWithdrawalIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();
    static final String ACCOUNT = "{\"bankBin\":\"970436\",\"accountNumber\":\"0011001234567\",\"accountHolder\":\"nguyen test\"}";

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
    void payoutAccountIsValidatedStoredAndMaskedForTheMember() throws Exception {
        mvc.perform(put("/api/v1/member/wallet/payout-account").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBin\":\"123456\",\"accountNumber\":\"0011001234567\",\"accountHolder\":\"NGUYEN TEST\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/member/wallet/payout-account").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBin\":\"970436\",\"accountNumber\":\"12ab\",\"accountHolder\":\"NGUYEN TEST\"}"))
                .andExpect(status().isBadRequest());
        saveAccount(member).andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedAccountNumber").value("****4567"))
                .andExpect(jsonPath("$.accountHolder").value("NGUYEN TEST"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist());
        mvc.perform(get("/api/v1/member/wallet/payout-account").with(user(details(otherMember)))).andExpect(status().isNoContent());
    }

    @Test
    void requestReservesAtOnceApprovalMovesNothingAndCompletionPaysOutOnce() throws Exception {
        fund(member, Pool.ACTUAL, 500_000);
        saveAccount(member);
        long id = id(create(member, 200_000, "ACTUAL", "4567").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.accountNumber").value("****4567")).andReturn().getResponse().getContentAsString());
        assertBalances(member, "300000", "200000");
        decide(receptionist, id, "approve", "{\"note\":\"Checked\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertBalances(member, "300000", "200000");
        mvc.perform(get("/api/v1/reception/wallet/withdrawals/" + id).with(user(details(receptionist))))
                .andExpect(jsonPath("$.accountNumber").value("0011001234567"));
        decide(receptionist, id, "complete", "{}").andExpect(status().isBadRequest());
        decide(receptionist, id, "complete", "{\"payoutReference\":\"PO2611081\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        decide(receptionist, id, "complete", "{\"payoutReference\":\"PO2611081\"}").andExpect(status().isConflict());
        decide(receptionist, id, "reject", "{\"note\":\"late\"}").andExpect(status().isConflict());
        assertBalances(member, "300000", "0");
        assertThat(count("SELECT COUNT(*) FROM wallet_pending_operation WHERE member_id=?", member)).isZero();
    }

    @Test
    void rejectAndFailureReleaseTheReservationAndTheSlotExactlyOnce() throws Exception {
        fund(member, Pool.ACTUAL, 300_000);
        saveAccount(member);
        long a = id(create(member, 100_000, "ACTUAL", "4567").andReturn().getResponse().getContentAsString());
        assertBalances(member, "200000", "100000");
        // CODEOWNER rule: one blocking wallet operation per Member, so a second request waits for the first.
        create(member, 100_000, "ACTUAL", "4567").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_OPERATION_IN_FLIGHT"));
        decide(receptionist, a, "reject", "{}").andExpect(status().isBadRequest());
        decide(receptionist, a, "reject", "{\"note\":\"Account holder mismatch\"}").andExpect(status().isOk());
        decide(receptionist, a, "reject", "{\"note\":\"again\"}").andExpect(status().isConflict());
        long b = id(create(member, 100_000, "ACTUAL", "4567").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        decide(receptionist, b, "approve", null).andExpect(status().isOk());
        decide(receptionist, b, "fail", "{\"note\":\"Bank returned the transfer\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));
        assertBalances(member, "300000", "0");
        assertThat(count("SELECT COUNT(*) FROM wallet_ledger_entry e JOIN member_wallet w ON w.id=e.wallet_id WHERE w.member_id=?"
                + " AND e.entry_type='WITHDRAWAL_RELEASE'", member)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM wallet_pending_operation WHERE member_id=?", member)).isZero();
    }

    @Test
    void excessMissingAccountWrongConfirmationAndConcurrentRequestsAreRefused() throws Exception {
        fund(member, Pool.ACTUAL, 250_000);
        create(member, 100_000, "ACTUAL", "4567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_ACCOUNT_REQUIRED"));
        saveAccount(member);
        create(member, 100_000, "ACTUAL", "9999").andExpect(status().isBadRequest());
        create(member, 300_000, "ACTUAL", "4567").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_INSUFFICIENT_FUNDS"));
        assertThat(count("SELECT COUNT(*) FROM wallet_withdrawal WHERE member_id=?", member)).isZero();
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(pool.submit(() -> { start.await(); return create(member, 100_000, "ACTUAL", "4567").andReturn().getResponse().getStatus(); }));
        }
        start.countDown();
        int created = 0;
        for (Future<Integer> r : results) if (r.get() == 201) created++;
        pool.shutdown();
        assertThat(created).isEqualTo(1);
        assertBalances(member, "150000", "100000");
    }

    @Test
    void demoFundsNeverBecomeARealBankPayout() throws Exception {
        fund(member, Pool.SIMULATED, 120_000);
        create(member, 50_000, "ACTUAL", "4567").andExpect(status().isBadRequest());
        long id = id(create(member, 50_000, "SIMULATED", null).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        decide(receptionist, id, "approve", null).andExpect(status().isOk());
        decide(receptionist, id, "complete", "{\"payoutReference\":\"PO-FAKE\"}").andExpect(status().isBadRequest());
        decide(receptionist, id, "complete", "{}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SIMULATED_COMPLETED"))
                .andExpect(jsonPath("$.payoutReference").doesNotExist());
        var balance = ledger.balance(member);
        assertThat(balance.simulatedAvailable()).isEqualByComparingTo("70000");
        assertThat(balance.actualAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void onlyReceptionistsDecideAndMembersSeeOnlyTheirOwnRequests() throws Exception {
        fund(member, Pool.ACTUAL, 100_000);
        saveAccount(member);
        long id = id(create(member, 50_000, "ACTUAL", "4567").andReturn().getResponse().getContentAsString());
        decide(member, id, "approve", null).andExpect(status().isForbidden());
        decide(manager, id, "approve", null).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/member/wallet/withdrawals").with(user(details(otherMember)))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/reception/wallet/withdrawals").with(user(details(receptionist))))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].accountNumber").value("****4567"));
        assertThat(count("SELECT COUNT(*) FROM refund_request WHERE member_id=?", member)).isZero();
    }

    private void assertBalances(long memberId, String available, String reserved) {
        var b = ledger.balance(memberId);
        assertThat(b.actualAvailable()).isEqualByComparingTo(available);
        assertThat(b.actualReserved()).isEqualByComparingTo(reserved);
    }

    private ResultActions saveAccount(long memberId) throws Exception {
        return mvc.perform(put("/api/v1/member/wallet/payout-account").with(user(details(memberId)))
                .contentType(MediaType.APPLICATION_JSON).content(ACCOUNT));
    }

    private ResultActions create(long memberId, long amount, String pool, String last4) throws Exception {
        String confirm = last4 == null ? "null" : "\"" + last4 + "\"";
        return mvc.perform(post("/api/v1/member/wallet/withdrawals").with(user(details(memberId))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"pool\":\"" + pool + "\",\"confirmAccountLast4\":" + confirm + "}"));
    }

    private ResultActions decide(long staff, long id, String action, String body) throws Exception {
        var request = post("/api/v1/reception/wallet/withdrawals/" + id + "/" + action).with(user(details(staff)));
        if (body != null) request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request);
    }

    private void fund(long memberId, Pool pool, long amount) {
        ledger.apply(new Operation(memberId, EntryType.TOPUP_CREDIT, pool, BigDecimal.valueOf(amount), marker + "-f" + ++serial, "TEST", 1, "fixture", null));
    }

    private long id(String json) {
        var m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
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
