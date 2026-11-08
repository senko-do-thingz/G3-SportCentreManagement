package com.sportify.payment.wallet;

import com.sportify.AbstractIntegrationTest;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.RoleRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.wallet.WalletLedgerService.EntryType;
import com.sportify.payment.wallet.WalletLedgerService.Operation;
import com.sportify.payment.wallet.WalletLedgerService.Pool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MW-03 wallet ledger on real SQL Server: zero start, exactly-once operations, no negative balance under concurrency,
 * pool separation, append-only history, reconciliation and Member-only access. Ledger rows are append-only, so the
 * fixture leaves its uniquely marked members in place and every assertion is scoped to them.
 */
@AutoConfigureMockMvc
class WalletLedgerIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired WalletLedgerService ledger;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;

    private final String marker = "wal" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember;

    @BeforeEach
    void fixture() {
        member = member();
        otherMember = member();
    }

    @Test
    void newWalletStartsAtZeroWithNoActivityAndIsCreatedOnce() throws Exception {
        mvc.perform(get("/api/v1/member/wallet").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableBalance").value(0))
                .andExpect(jsonPath("$.reservedBalance").value(0))
                .andExpect(jsonPath("$.demoAvailableBalance").value(0))
                .andExpect(jsonPath("$.activityCount").value(0))
                .andExpect(jsonPath("$.recentActivity.length()").value(0));
        mvc.perform(get("/api/v1/member/wallet").with(user(details(member)))).andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM member_wallet WHERE member_id=?", member)).isEqualTo(1);
    }

    @Test
    void creditAndDebitMoveMoneyOnceAndReplayReturnsTheSameEntry() {
        var credit = ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 500_000, key("topup")));
        var replay = ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 500_000, keyOf(credit)));
        assertThat(replay.replay()).isTrue();
        assertThat(replay.entry().id()).isEqualTo(credit.entry().id());
        ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 200_000, key("buy")));
        var balance = ledger.balance(member);
        assertThat(balance.actualAvailable()).isEqualByComparingTo("300000");
        assertThat(count("SELECT COUNT(*) FROM wallet_ledger_entry e JOIN member_wallet w ON w.id=e.wallet_id WHERE w.member_id=?", member)).isEqualTo(2);
        assertThat(ledger.balance(otherMember).actualAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void replayWithDifferentDetailsIsRejectedAndMovesNothing() {
        String key = key("topup");
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 100_000, key));
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 999_000, key)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> ledger.apply(op(otherMember, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 100_000, key)))
                .isInstanceOf(ConflictException.class);
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("100000");
        assertThat(ledger.balance(otherMember).actualAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void compensationMustNameAnEarlierDebitOfTheSameWalletAndNeverExceedIt() {
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 300_000, key("topup")));
        long debit = ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 200_000, key("buy"))).entry().id();
        ledger.apply(op(otherMember, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 100_000, key("other-topup")));
        long otherDebit = ledger.apply(op(otherMember, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 100_000, key("other-buy"))).entry().id();
        // Not tied to a debit, tied to a non-debit entry, or tied to another member's debit: refused.
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.COMPENSATION_CREDIT, Pool.ACTUAL, 50_000, key("free"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("must name the wallet debit");
        assertThatThrownBy(() -> ledger.apply(compensation(member, debit + 999_999, 50_000, key("missing"))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ledger.apply(compensation(member, otherDebit, 50_000, key("cross"))))
                .isInstanceOf(BusinessRuleException.class);
        // Partial compensations up to the debit succeed; one more VND is refused; a replay moves nothing.
        var first = ledger.apply(compensation(member, debit, 150_000, key("comp")));
        ledger.apply(compensation(member, debit, 50_000, key("comp")));
        assertThatThrownBy(() -> ledger.apply(compensation(member, debit, 1, key("over"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("exceed");
        assertThat(ledger.apply(compensation(member, debit, 150_000, keyOf(first))).replay()).isTrue();
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("300000");
        assertThat(ledger.balance(otherMember).actualAvailable()).isEqualByComparingTo("0");
        assertReconciles(member);
    }

    @Test
    void insufficientFundsLeavesBalanceAndLedgerUnchanged() {
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 50_000, key("topup")));
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 50_001, key("buy"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Not enough");
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("50000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.5", "100000001"})
    void invalidAmountsAreRejected(String amount) {
        assertThatThrownBy(() -> ledger.apply(new Operation(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, new BigDecimal(amount),
                key("bad"), "TEST", 1, "Invalid amount", null))).isInstanceOf(BusinessRuleException.class);
        assertThat(ledger.entryCount(member)).isZero();
    }

    @Test
    void demoFundsNeverPayForARealPurchaseAndPoolsStaySeparate() {
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.SIMULATED, 300_000, key("demo")));
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.SIMULATED, 100_000, key("buy"))))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 100_000, key("buy2"))))
                .isInstanceOf(BusinessRuleException.class);
        var balance = ledger.balance(member);
        assertThat(balance.simulatedAvailable()).isEqualByComparingTo("300000");
        assertThat(balance.actualAvailable()).isEqualByComparingTo("0");
        // The database refuses it too, even for a write that skips the service.
        long wallet = balance.walletId();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO wallet_ledger_entry(wallet_id, operation_key, entry_type, pool, amount,"
                + " available_after, reserved_after, source_type, source_id, description, created_at)"
                + " VALUES (?, ?, 'PURCHASE_DEBIT', 'SIMULATED', 1, 0, 0, 'TEST', 1, 'x', SYSDATETIME())", wallet, key("raw")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void reserveReleaseAndPayoutKeepAvailablePlusReservedConsistent() {
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 400_000, key("topup")));
        ledger.apply(op(member, EntryType.WITHDRAWAL_RESERVE, Pool.ACTUAL, 150_000, key("reserve1")));
        ledger.apply(op(member, EntryType.WITHDRAWAL_RESERVE, Pool.ACTUAL, 100_000, key("reserve2")));
        var held = ledger.balance(member);
        assertThat(held.actualAvailable()).isEqualByComparingTo("150000");
        assertThat(held.actualReserved()).isEqualByComparingTo("250000");
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 200_000, key("buy"))))
                .isInstanceOf(BusinessRuleException.class);
        ledger.apply(op(member, EntryType.WITHDRAWAL_RELEASE, Pool.ACTUAL, 150_000, key("release1")));
        ledger.apply(op(member, EntryType.WITHDRAWAL_PAYOUT, Pool.ACTUAL, 100_000, key("payout2")));
        var after = ledger.balance(member);
        assertThat(after.actualAvailable()).isEqualByComparingTo("300000");
        assertThat(after.actualReserved()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> ledger.apply(op(member, EntryType.WITHDRAWAL_PAYOUT, Pool.ACTUAL, 1, key("payout-none"))))
                .isInstanceOf(IllegalStateException.class);
        assertReconciles(member);
    }

    @Test
    void concurrentDebitsNeverOverspendTheBalance() throws Exception {
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 500_000, key("topup")));
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String key = key("race");
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    ledger.apply(op(member, EntryType.PURCHASE_DEBIT, Pool.ACTUAL, 100_000, key));
                    return true;
                } catch (BusinessRuleException insufficient) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) succeeded++;
        }
        pool.shutdown();
        assertThat(succeeded).isEqualTo(5);
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
        assertReconciles(member);
    }

    @Test
    void concurrentReplaysOfOneOperationCreditExactlyOnce() throws Exception {
        String key = key("topup");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> { start.await(); return ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 70_000, key)); }));
        }
        start.countDown();
        for (Future<Object> result : results) result.get();
        pool.shutdown();
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("70000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
    }

    @Test
    void ledgerRowsAreAppendOnly() {
        var applied = ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 10_000, key("topup")));
        assertThatThrownBy(() -> jdbc.update("UPDATE wallet_ledger_entry SET amount=999999 WHERE id=?", applied.entry().id()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM wallet_ledger_entry WHERE id=?", applied.entry().id()))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT amount FROM wallet_ledger_entry WHERE id=?", BigDecimal.class, applied.entry().id()))
                .isEqualByComparingTo("10000");
    }

    @Test
    void walletActivityCreatesNoPaymentInvoiceOrCashEvent() {
        int payments = count("SELECT COUNT(*) FROM payment WHERE member_id=?", member);
        int cash = count("SELECT COUNT(*) FROM cash_event");
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 80_000, key("topup")));
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.SIMULATED, 80_000, key("demo")));
        assertThat(count("SELECT COUNT(*) FROM payment WHERE member_id=?", member)).isEqualTo(payments);
        assertThat(count("SELECT COUNT(*) FROM invoice WHERE member_id=?", member)).isZero();
        assertThat(count("SELECT COUNT(*) FROM cash_event")).isEqualTo(cash);
    }

    @Test
    void ledgerApiIsPagedOwnOnlyAndStaffAreDenied() throws Exception {
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 10_000, key("a")));
        ledger.apply(op(member, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 20_000, key("b")));
        ledger.apply(op(otherMember, EntryType.TOPUP_CREDIT, Pool.ACTUAL, 99_000, key("c")));
        mvc.perform(get("/api/v1/member/wallet/ledger?page=0&size=1").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(20000))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/member/wallet").with(user(details(member))))
                .andExpect(jsonPath("$.availableBalance").value(30000));
        mvc.perform(get("/api/v1/member/wallet/ledger?size=500").with(user(details(member)))).andExpect(status().isBadRequest());
        for (String role : List.of("RECEPTIONIST", "MANAGER", "COACH")) {
            mvc.perform(get("/api/v1/member/wallet").with(user(details(account(role))))).andExpect(status().isForbidden());
        }
    }

    private void assertReconciles(long memberId) {
        var b = ledger.balance(memberId);
        BigDecimal credits = sum(memberId, "'TOPUP_CREDIT','COMPENSATION_CREDIT','WITHDRAWAL_RELEASE'");
        BigDecimal debits = sum(memberId, "'PURCHASE_DEBIT','WITHDRAWAL_RESERVE'");
        BigDecimal reserves = sum(memberId, "'WITHDRAWAL_RESERVE'");
        BigDecimal releasesAndPayouts = sum(memberId, "'WITHDRAWAL_RELEASE','WITHDRAWAL_PAYOUT'");
        assertThat(b.actualAvailable()).isEqualByComparingTo(credits.subtract(debits));
        assertThat(b.actualReserved()).isEqualByComparingTo(reserves.subtract(releasesAndPayouts));
    }

    private BigDecimal sum(long memberId, String types) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(e.amount),0) FROM wallet_ledger_entry e JOIN member_wallet w ON w.id=e.wallet_id"
                + " WHERE w.member_id=? AND e.pool='ACTUAL' AND e.entry_type IN (" + types + ")", BigDecimal.class, memberId);
    }

    private Operation op(long memberId, EntryType type, Pool pool, long amount, String key) {
        return new Operation(memberId, type, pool, BigDecimal.valueOf(amount), key, "TEST", 1, "Test " + type, null);
    }

    private Operation compensation(long memberId, long debitEntryId, long amount, String key) {
        return new Operation(memberId, EntryType.COMPENSATION_CREDIT, Pool.ACTUAL, BigDecimal.valueOf(amount), key,
                WalletLedgerService.COMPENSATION_SOURCE, debitEntryId, "Test compensation", null);
    }

    private String keyOf(WalletLedgerService.Applied applied) {
        return jdbc.queryForObject("SELECT operation_key FROM wallet_ledger_entry WHERE id=?", String.class, applied.entry().id());
    }

    private String key(String label) { return marker + "-" + label + "-" + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(marker + ++serial + "@sportify.test").fullName(role + " wallet fixture")
                .phone(Long.toString(RANDOM.nextLong(100_000_000_000L, 1_000_000_000_000L)))
                .passwordHash("test-only-hash").role(roles.findByCode(role).orElseThrow()).status("ACTIVE").build()).getId();
    }

    private long member() {
        long id = account("MEMBER");
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
