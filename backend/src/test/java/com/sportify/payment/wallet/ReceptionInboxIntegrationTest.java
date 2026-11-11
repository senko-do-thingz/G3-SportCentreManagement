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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.sportify.payment.service.VietQrBankDirectory;
import java.util.Optional;
import static org.mockito.Mockito.when;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

/** MW-07 inbox on real SQL Server: one notice per task and Receptionist, read is not resolved, role scope. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.wallet.simulated-topups-enabled=true",
        "app.payment.bank-transfer.live-instructions-enabled=true", "app.payment.bank-transfer.bank-bin=970436",
        "app.payment.bank-transfer.account-number=0011009990001", "app.payment.bank-transfer.account-name=SPORTIFYDEMOCENTER"})
class ReceptionInboxIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    /** Synthetic receiving account; the bank directory is mocked. */
    static final VietQrBankDirectory.Bank BANK = new VietQrBankDirectory.Bank("970436", "VCB", "Vietcombank", true);
    @MockitoBean VietQrBankDirectory directory;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ReceptionInboxService inbox;

    private final String marker = "inb" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, receptionist, otherReceptionist;

    @BeforeEach
    void fixture() {
        when(directory.supported("970436")).thenReturn(Optional.of(BANK));
        when(directory.banks()).thenReturn(List.of(BANK));
        member = member();
        receptionist = account("RECEPTIONIST");
        otherReceptionist = account("RECEPTIONIST");
    }

    @Test
    void aPendingTaskGivesOneNoticeThatStaysOpenWhenReadAndResolvesWithTheTask() throws Exception {
        long paymentId = createPaymentException();
        String key = "PAYMENT_EXCEPTION:" + paymentId;
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/reception/inbox").with(user(details(receptionist)))).andExpect(status().isOk());
        }
        assertThat(notices(receptionist, key)).isEqualTo(1);
        long notice = jdbc.queryForObject("SELECT id FROM notification WHERE user_id=? AND event_key=?", Long.class, receptionist, key);
        mvc.perform(put("/api/v1/reception/inbox/" + notice + "/read").with(user(details(receptionist))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.sourceStatus").value("PENDING"));
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, paymentId)).isEqualTo("PENDING");

        // Resolve the payment
        jdbc.update("UPDATE payment SET payment_status = 'SUCCESS', payment_time = SYSDATETIME(), notes = N'Resolved by staff' WHERE id = ?", paymentId);

        var items = inbox.inbox(receptionist, false).items().stream().filter(i -> i.id() == notice).toList();
        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.open()).isFalse();
            assertThat(i.sourceStatus()).isEqualTo("SUCCESS");
        });
        assertThat(inbox.inbox(receptionist, true).items()).noneMatch(i -> i.id() == notice);
        inbox.sync(otherReceptionist);
        assertThat(notices(otherReceptionist, key)).isZero();
    }

    @Test
    void concurrentPollingNeverDuplicatesANotice() throws Exception {
        long paymentId = createPaymentException();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(pool.submit(() -> { start.await(); inbox.sync(receptionist); return null; }));
        }
        start.countDown();
        for (Future<Object> r : results) r.get();
        pool.shutdown();
        assertThat(notices(receptionist, "PAYMENT_EXCEPTION:" + paymentId)).isEqualTo(1);
    }

    @Test
    void noticesHoldNoBankAccountAndOnlyReceptionistsReachTheInbox() throws Exception {
        long paymentId = createPaymentException();
        inbox.sync(receptionist);
        long notice = jdbc.queryForObject("SELECT id FROM notification WHERE user_id=? AND event_key=?", Long.class,
                receptionist, "PAYMENT_EXCEPTION:" + paymentId);
        mvc.perform(put("/api/v1/reception/inbox/" + notice + "/read").with(user(details(otherReceptionist)))).andExpect(status().isNotFound());
        for (String role : List.of("MEMBER", "MANAGER", "COACH")) {
            long id = "MEMBER".equals(role) ? member : account(role);
            mvc.perform(get("/api/v1/reception/inbox").with(user(details(id)))).andExpect(status().isForbidden());
        }
        assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id=? AND type='RECEPTION_TASK'", member)).isZero();
    }

    @Test
    void aReceptionistAddedLaterStillSeesTasksThatAreOpenNow() {
        long paymentId = createPaymentException();
        long later = account("RECEPTIONIST");
        assertThat(inbox.inbox(later, true).items())
                .anyMatch(i -> i.sourceType().equals("PAYMENT_EXCEPTION") && i.sourceId() == paymentId && !i.read());
    }

    @Test
    void activeInboxExcludesHistoricalWalletNotificationsWithoutModifyingRecords() {
        // Arrange: insert pre-existing pending wallet topup and withdrawal into database
        long topUpId = insertHistoricalTopUp(member, 50_000, "PENDING");
        long withdrawalId = insertHistoricalWithdrawal(member, 30_000, "PENDING");

        // Insert historical notification records for the receptionist
        String topUpKey = "WALLET_TOPUP:" + topUpId;
        String withdrawalKey = "WALLET_WITHDRAWAL:" + withdrawalId;
        jdbc.update("INSERT INTO notification (user_id, title, content, type, is_read, created_at, event_key) "
                + "VALUES (?, N'Verify wallet top-up TU-000001', N'Member requested 50,000 VND', 'RECEPTION_TASK', 0, SYSDATETIME(), ?)",
                receptionist, topUpKey);
        jdbc.update("INSERT INTO notification (user_id, title, content, type, is_read, created_at, event_key) "
                + "VALUES (?, N'Review wallet withdrawal WD-000001', N'Member asked to withdraw 30,000 VND', 'RECEPTION_TASK', 0, SYSDATETIME(), ?)",
                receptionist, withdrawalKey);

        // Also add an active payment exception task
        long paymentId = createPaymentException();
        String paymentKey = "PAYMENT_EXCEPTION:" + paymentId;

        // Verify stored records exist before query
        assertThat(notices(receptionist, topUpKey)).isEqualTo(1);
        assertThat(notices(receptionist, withdrawalKey)).isEqualTo(1);

        // Act: fetch active inbox (both openOnly=false and openOnly=true)
        var fullInbox = inbox.inbox(receptionist, false);
        var openOnlyInbox = inbox.inbox(receptionist, true);

        // Assert 1: Historical wallet notifications are completely excluded from the active inbox
        assertThat(fullInbox.items()).noneMatch(i -> i.sourceType().startsWith("WALLET_"));
        assertThat(openOnlyInbox.items()).noneMatch(i -> i.sourceType().startsWith("WALLET_"));

        // Assert 2: Active non-wallet tasks are included and counted
        assertThat(fullInbox.items()).anyMatch(i -> i.sourceType().equals("PAYMENT_EXCEPTION") && i.sourceId() == paymentId);
        assertThat(fullInbox.open()).isEqualTo(1);
        assertThat(fullInbox.unreadOpen()).isEqualTo(1);

        // Assert 3: Stored notification and wallet records are preserved in the DB without deletion or modification
        assertThat(notices(receptionist, topUpKey)).isEqualTo(1);
        assertThat(notices(receptionist, withdrawalKey)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_read FROM notification WHERE user_id=? AND event_key=?", Boolean.class, receptionist, topUpKey)).isFalse();
        assertThat(jdbc.queryForObject("SELECT is_read FROM notification WHERE user_id=? AND event_key=?", Boolean.class, receptionist, withdrawalKey)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_topup WHERE id=?", String.class, topUpId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_withdrawal WHERE id=?", String.class, withdrawalId)).isEqualTo("PENDING");
    }

    @Test
    void walletTopUpConfirmationAttemptThrowsWalletFeatureRetired() throws Exception {
        mvc.perform(post("/api/v1/reception/wallet/topups/1/confirm").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receivedAmount\":30000,\"bankReference\":\"FT123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_FEATURE_RETIRED"));
    }

    private long createPaymentException() {
        String code = "PAY-EX-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO payment (payment_code, member_id, amount, payment_method, payment_status, notes, created_at, updated_at)
                VALUES (?, ?, 200000, 'BANK_TRANSFER', 'PENDING', N'Amount mismatch: expected 200000 got 100000', SYSDATETIME(), SYSDATETIME())
                """, code, member);
        return jdbc.queryForObject("SELECT id FROM payment WHERE payment_code = ?", Long.class, code);
    }

    private long insertHistoricalTopUp(long memberId, long amount, String status) {
        String key = "hist-tu-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO wallet_topup (member_id, mode, amount, status, beneficiary_bin, beneficiary_account, beneficiary_name, idempotency_key, created_at, updated_at)
                VALUES (?, 'ACTUAL', ?, ?, '970436', '0011009990001', N'SPORTIFYDEMOCENTER', ?, SYSDATETIME(), SYSDATETIME())
                """, memberId, amount, status, key);
        return jdbc.queryForObject("SELECT id FROM wallet_topup WHERE idempotency_key = ?", Long.class, key);
    }

    private long insertHistoricalWithdrawal(long memberId, long amount, String status) {
        String key = "hist-wd-" + marker + "-" + ++serial;
        jdbc.update("""
                INSERT INTO wallet_withdrawal (member_id, pool, amount, status, bank_bin, bank_name, account_number, account_holder, idempotency_key, created_at, updated_at)
                VALUES (?, 'ACTUAL', ?, ?, '970436', N'Vietcombank', '0011001234567', N'NGUYEN TEST', ?, SYSDATETIME(), SYSDATETIME())
                """, memberId, amount, status, key);
        return jdbc.queryForObject("SELECT id FROM wallet_withdrawal WHERE idempotency_key = ?", Long.class, key);
    }

    private int notices(long userId, String key) {
        return count("SELECT COUNT(*) FROM notification WHERE user_id=? AND event_key=?", userId, key);
    }

    private String code() { return marker + ++serial; }

    private long account(String role) {
        return users.saveAndFlush(UserAccount.builder().email(code() + "@sportify.test").fullName(role + " inbox fixture")
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
