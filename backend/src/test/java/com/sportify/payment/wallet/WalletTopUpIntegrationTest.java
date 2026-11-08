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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MW-04 top-ups on real SQL Server: PENDING never credits, staff confirmation credits once, and every refusal is clean. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.wallet.simulated-topups-enabled=true",
        "app.payment.bank-transfer.live-instructions-enabled=true", "app.payment.bank-transfer.bank-bin=" + WalletTopUpIntegrationTest.BIN,
        "app.payment.bank-transfer.account-number=0011009990001", "app.payment.bank-transfer.account-name=SPORTIFYDEMOCENTER"})
class WalletTopUpIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();
    /** Synthetic receiving account; the bank directory is mocked so no real bank or directory freshness is involved. */
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
    void pendingTopUpLeavesTheWalletUnchangedAndCarriesTheVerifiedReceivingAccount() throws Exception {
        long id = create(member, 200_000, "ACTUAL", key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("TU-")))
                .andExpect(jsonPath("$.instructions.payable").value(true))
                .andExpect(jsonPath("$.instructions.bankBin").value(BIN))
                .andExpect(jsonPath("$.instructions.qrPayload").exists())
                .andReturn().getResponse().getContentAsString().transform(this::id);
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
        assertThat(ledger.entryCount(member)).isZero();
        mvc.perform(get("/api/v1/member/wallet/topups").with(user(details(member))))
                .andExpect(jsonPath("$.topUps[0].id").value(id))
                .andExpect(jsonPath("$.topUps[0].status").value("PENDING"));
    }

    @Test
    void withoutAVerifiedReceivingAccountNoRealTopUpCanBeCreatedOrCredited() throws Exception {
        when(directory.supported(BIN)).thenReturn(Optional.empty());
        create(member, 200_000, "ACTUAL", key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPUP_NOT_PAYABLE"));
        mvc.perform(get("/api/v1/member/wallet/topups").with(user(details(member))))
                .andExpect(jsonPath("$.realTopUpsAvailable").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=?", Integer.class, member)).isZero();
        // Even a row written around the service cannot exist without the receiving account it was issued for.
        ledger.ensureWallet(member);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO wallet_topup (member_id, mode, amount, status, created_at, updated_at)"
                + " VALUES (?, 'ACTUAL', 200000, 'PENDING', SYSDATETIME(), SYSDATETIME())", member))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_wallet_topup_beneficiary");
        assertThat(ledger.entryCount(member)).isZero();
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void receptionistConfirmationCreditsExactlyOnceAndRecordsActorTimeAndReference() throws Exception {
        long id = id(create(member, 150_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        confirm(receptionist, id, 150_000, "FT26110812345").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.bankReference").value("FT26110812345"))
                .andExpect(jsonPath("$.reviewedAt").exists());
        confirm(otherReceptionist, id, 150_000, "FT26110812345").andExpect(status().isConflict());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("150000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM wallet_topup WHERE id=?", Long.class, id)).isEqualTo(receptionist);
    }

    @Test
    void concurrentConfirmationsOfOneTopUpCreditOnce() throws Exception {
        long id = id(create(member, 90_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long staff = i % 2 == 0 ? receptionist : otherReceptionist;
            results.add(pool.submit(() -> { start.await(); return confirm(staff, id, 90_000, "FTRACE" + id).andReturn().getResponse().getStatus(); }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) statuses.add(result.get());
        pool.shutdown();
        assertThat(statuses).containsOnlyOnce(200);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(3);
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("90000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
    }

    @Test
    void wrongAmountMissingOrReusedReferenceAndRejectedRequestsChangeNoBalance() throws Exception {
        long first = id(create(member, 100_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        confirm(receptionist, first, 99_000, "FTWRONGAMT").andExpect(status().isConflict());
        confirm(receptionist, first, 100_000, null).andExpect(status().isBadRequest());
        confirm(receptionist, first, 100_000, "FTSAME" + first).andExpect(status().isOk());
        long second = id(create(member, 100_000, "ACTUAL", key()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        confirm(receptionist, second, 100_000, "FTSAME" + first).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_topup WHERE id=?", String.class, second)).isEqualTo("PENDING");
        mvc.perform(post("/api/v1/reception/wallet/topups/" + second + "/reject").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"No matching bank transfer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        confirm(receptionist, second, 100_000, "FTLATE" + second).andExpect(status().isConflict());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("100000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
    }

    @Test
    void demoTopUpCreditsOnlyTheDemoPoolAndNeverTakesABankReference() throws Exception {
        long id = id(create(member, 50_000, "SIMULATED", key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.instructions.notice").value(org.hamcrest.Matchers.startsWith("DEMO TOP-UP")))
                .andReturn().getResponse().getContentAsString());
        confirm(receptionist, id, 50_000, "FTDEMO").andExpect(status().isBadRequest());
        confirm(receptionist, id, 50_000, null).andExpect(status().isOk());
        var balance = ledger.balance(member);
        assertThat(balance.simulatedAvailable()).isEqualByComparingTo("50000");
        assertThat(balance.actualAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void invalidAmountsIdempotentRetriesAndOnlyOnePendingTopUp() throws Exception {
        create(member, 9_999, "ACTUAL", key()).andExpect(status().isBadRequest());
        create(member, 50_000_001, "ACTUAL", key()).andExpect(status().isBadRequest());
        String retry = key();
        long a = id(create(member, 20_000, "ACTUAL", retry).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long b = id(create(member, 20_000, "ACTUAL", retry).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(b).isEqualTo(a);
        create(member, 30_000, "ACTUAL", retry).andExpect(status().isConflict());
        create(member, 20_000, "ACTUAL", key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletPendingOperations.CODE_IN_FLIGHT));
        create(member, 20_000, "SIMULATED", key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletPendingOperations.CODE_IN_FLIGHT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=?", Integer.class, member)).isEqualTo(1);
        mvc.perform(post("/api/v1/member/wallet/topups/" + a + "/cancel").with(user(details(member)))).andExpect(status().isOk());
        create(member, 20_000, "ACTUAL", key()).andExpect(status().isCreated());
        assertThat(slotCount(member)).isEqualTo(1);
    }

    @Test
    void parallelCreatesLeaveExactlyOnePendingTopUp() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String requestKey = key();
            results.add(pool.submit(() -> { start.await(); return create(member, 30_000, "ACTUAL", requestKey).andReturn().getResponse().getStatus(); }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) statuses.add(result.get());
        pool.shutdown();
        assertThat(statuses).containsOnlyOnce(201);
        assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=? AND status='PENDING'", Integer.class, member)).isEqualTo(1);
        assertThat(slotCount(member)).isEqualTo(1);
    }

    @Test
    void anotherInFlightWalletOperationBlocksATopUpAndTheDatabaseRefusesASecondSlot() throws Exception {
        ledger.ensureWallet(member);
        jdbc.update("INSERT INTO wallet_pending_operation (member_id, operation_type, operation_id, created_at)"
                + " VALUES (?, 'WITHDRAWAL', ?, SYSDATETIME())", member, -member);
        try {
            create(member, 20_000, "ACTUAL", key()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(WalletPendingOperations.CODE_IN_FLIGHT))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pending withdrawal")));
            assertThatThrownBy(() -> jdbc.update("INSERT INTO wallet_pending_operation (member_id, operation_type, operation_id, created_at)"
                    + " VALUES (?, 'PURCHASE', ?, SYSDATETIME())", member, -member)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=?", Integer.class, member)).isZero();
        } finally {
            jdbc.update("DELETE FROM wallet_pending_operation WHERE member_id=?", member);
        }
    }

    @Test
    void concurrentCreateConfirmAndCancelNeverDeadlockAndKeepTheLedgerConsistent() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        List<Integer> statuses = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            List<Long> open = jdbc.queryForList("SELECT id FROM wallet_topup WHERE member_id=? AND status='PENDING'", Long.class, member);
            long id = open.isEmpty() ? id(create(member, 10_000, "ACTUAL", key()).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString()) : open.getFirst();
            CountDownLatch start = new CountDownLatch(1);
            String nextKey = key();
            List<Future<Integer>> results = List.of(
                    pool.submit(() -> { start.await(); return confirm(receptionist, id, 10_000, "FTMIX" + id).andReturn().getResponse().getStatus(); }),
                    pool.submit(() -> { start.await(); return mvc.perform(post("/api/v1/member/wallet/topups/" + id + "/cancel")
                            .with(user(details(member)))).andReturn().getResponse().getStatus(); }),
                    pool.submit(() -> { start.await(); return create(member, 10_000, "ACTUAL", nextKey).andReturn().getResponse().getStatus(); }));
            start.countDown();
            for (Future<Integer> result : results) statuses.add(result.get());
        }
        pool.shutdown();
        // 400 is a confirmation that lost to the member's cancel (a late payment needs a note); never a 5xx deadlock victim.
        assertThat(statuses).allMatch(s -> s == 200 || s == 201 || s == 400 || s == 409);
        Integer confirmed = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=? AND status='CONFIRMED'", Integer.class, member);
        assertThat(ledger.entryCount(member)).isEqualTo(confirmed.longValue());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo(BigDecimal.valueOf(10_000L * confirmed));
        Integer pendingRows = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=? AND status='PENDING'", Integer.class, member);
        assertThat(pendingRows).isLessThanOrEqualTo(1);
        assertThat(slotCount(member)).isEqualTo(pendingRows);
    }

    @Test
    void aLatePaymentWaitsWhileAnotherWalletOperationIsInFlight() throws Exception {
        long late = id(create(member, 70_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/member/wallet/topups/" + late + "/cancel").with(user(details(member)))).andExpect(status().isOk());
        long newer = id(create(member, 20_000, "ACTUAL", key()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        confirm(receptionist, late, 70_000, "FTLATEBUSY" + late, "Late transfer").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(WalletPendingOperations.CODE_IN_FLIGHT));
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_topup WHERE id=?", String.class, late)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT operation_id FROM wallet_pending_operation WHERE member_id=?", Long.class, member)).isEqualTo(newer);
        assertThat(ledger.entryCount(member)).isZero();

        // A pending withdrawal (MW-06) blocks it the same way.
        mvc.perform(post("/api/v1/member/wallet/topups/" + newer + "/cancel").with(user(details(member)))).andExpect(status().isOk());
        jdbc.update("INSERT INTO wallet_pending_operation (member_id, operation_type, operation_id, created_at)"
                + " VALUES (?, 'WITHDRAWAL', ?, SYSDATETIME())", member, -member);
        try {
            confirm(receptionist, late, 70_000, "FTLATEBUSY" + late, "Late transfer").andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("WITHDRAWAL")));
        } finally {
            jdbc.update("DELETE FROM wallet_pending_operation WHERE member_id=?", member);
        }
        assertThat(ledger.entryCount(member)).isZero();

        // Once nothing else is in flight, the late payment is confirmed exactly once and takes no slot.
        confirm(receptionist, late, 70_000, "FTLATEBUSY" + late, "Late transfer").andExpect(status().isOk());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("70000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
        assertThat(slotCount(member)).isZero();
    }

    @Test
    void aLateConfirmationRacingANewTopUpNeverCreditsWhileTheNewOneIsPending() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            jdbc.queryForList("SELECT id FROM wallet_topup WHERE member_id=? AND status='PENDING'", Long.class, member)
                    .forEach(open -> {
                        try {
                            mvc.perform(post("/api/v1/member/wallet/topups/" + open + "/cancel").with(user(details(member))));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    });
            long late = id(create(member, 10_000, "ACTUAL", key()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
            mvc.perform(post("/api/v1/member/wallet/topups/" + late + "/cancel").with(user(details(member)))).andExpect(status().isOk());
            CountDownLatch start = new CountDownLatch(1);
            String nextKey = key();
            Future<Integer> lateConfirm = pool.submit(() -> { start.await();
                return confirm(receptionist, late, 10_000, "FTRACELATE" + late, "Late transfer").andReturn().getResponse().getStatus(); });
            Future<Integer> newTopUp = pool.submit(() -> { start.await();
                return create(member, 10_000, "ACTUAL", nextKey).andReturn().getResponse().getStatus(); });
            start.countDown();
            int confirmStatus = lateConfirm.get();
            int createStatus = newTopUp.get();
            outcomes.add(confirmStatus + "/" + createStatus);
            // Either the late payment committed first (then the new request is created after it),
            // or the new request took the slot first and the late payment was refused.
            assertThat(List.of("200/201", "409/201")).contains(confirmStatus + "/" + createStatus);
        }
        pool.shutdown();
        Integer confirmed = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=? AND status='CONFIRMED'", Integer.class, member);
        assertThat(ledger.entryCount(member)).isEqualTo(confirmed.longValue());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo(BigDecimal.valueOf(10_000L * confirmed));
        Integer pendingRows = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=? AND status='PENDING'", Integer.class, member);
        assertThat(pendingRows).isEqualTo(1);
        assertThat(slotCount(member)).isEqualTo(1);
        assertThat(outcomes).hasSize(6);
    }

    @Test
    void aMemberCancelledRealTopUpCanStillBeConfirmedAsALatePaymentWithANote() throws Exception {
        long id = id(create(member, 70_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/v1/member/wallet/topups/" + id + "/cancel").with(user(details(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        confirm(receptionist, id, 70_000, "FTLATEPAY" + id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOPUP_LATE_NOTE_REQUIRED"));
        confirm(receptionist, id, 70_000, "FTLATEPAY" + id, "Transfer arrived after the member cancelled").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        confirm(otherReceptionist, id, 70_000, "FTLATEPAY" + id, "Second attempt").andExpect(status().isConflict());
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("70000");
        assertThat(ledger.entryCount(member)).isEqualTo(1);
        assertThat(slotCount(member)).isZero();
    }

    @Test
    void ownershipAndRolesAreEnforced() throws Exception {
        long id = id(create(member, 40_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/member/wallet/topups/" + id).with(user(details(otherMember)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/member/wallet/topups/" + id + "/cancel").with(user(details(otherMember)))).andExpect(status().isNotFound());
        confirm(member, id, 40_000, "FTSELF" + id).andExpect(status().isForbidden());
        confirm(manager, id, 40_000, "FTMGR" + id).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/wallet/topups").with(user(details(member)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reception/wallet/topups").with(user(details(receptionist))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id == " + id + ")].status").value("PENDING"));
        mvc.perform(post("/api/v1/member/wallet/topups/" + id + "/cancel").with(user(details(member))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        // A cancelled real-money request is only confirmable as a documented late payment (note required).
        confirm(receptionist, id, 40_000, "FTCANCEL" + id).andExpect(status().isBadRequest());
        assertThat(ledger.entryCount(member)).isZero();
    }

    @Test
    void syntheticDemoIdentitiesCannotRequestRealMoneyTopUps() throws Exception {
        long synthetic = member("@unified-demo.invalid");
        create(synthetic, 20_000, "ACTUAL", key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYNTHETIC_OPERATION_DENIED"));
    }

    private ResultActions create(long id, long amount, String mode, String key) throws Exception {
        return mvc.perform(post("/api/v1/member/wallet/topups").with(user(details(id))).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + ",\"mode\":\"" + mode + "\"}"));
    }

    private ResultActions confirm(long staff, long id, long amount, String reference) throws Exception {
        return confirm(staff, id, amount, reference, null);
    }

    private ResultActions confirm(long staff, long id, long amount, String reference, String note) throws Exception {
        String ref = reference == null ? "null" : "\"" + reference + "\"";
        String text = note == null ? "null" : "\"" + note + "\"";
        return mvc.perform(post("/api/v1/reception/wallet/topups/" + id + "/confirm").with(user(details(staff)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receivedAmount\":" + amount + ",\"bankReference\":" + ref
                        + ",\"note\":" + text + "}"));
    }

    private int slotCount(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_pending_operation WHERE member_id=?", Integer.class, memberId);
    }

    private long id(String json) {
        var matcher = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertThat(matcher.find()).isTrue();
        return Long.parseLong(matcher.group(1));
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

    @SuppressWarnings("unused")
    private static BigDecimal vnd(long value) { return BigDecimal.valueOf(value); }
}
