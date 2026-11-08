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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** MW-04 top-ups on real SQL Server: PENDING never credits, staff confirmation credits once, and every refusal is clean. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.wallet.simulated-topups-enabled=true")
class WalletTopUpIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

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
    }

    @Test
    void pendingTopUpLeavesTheWalletUnchangedAndShowsNoPayableQrWithoutAVerifiedAccount() throws Exception {
        long id = create(member, 200_000, "ACTUAL", key()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("TU-")))
                .andExpect(jsonPath("$.instructions.payable").value(false))
                .andExpect(jsonPath("$.instructions.qrPayload").doesNotExist())
                .andReturn().getResponse().getContentAsString().transform(this::id);
        assertThat(ledger.balance(member).actualAvailable()).isEqualByComparingTo("0");
        assertThat(ledger.entryCount(member)).isZero();
        mvc.perform(get("/api/v1/member/wallet/topups").with(user(details(member))))
                .andExpect(jsonPath("$.topUps[0].id").value(id))
                .andExpect(jsonPath("$.topUps[0].status").value("PENDING"));
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
        long second = id(create(member, 100_000, "ACTUAL", key()).andReturn().getResponse().getContentAsString());
        confirm(receptionist, first, 99_000, "FTWRONGAMT").andExpect(status().isConflict());
        confirm(receptionist, first, 100_000, null).andExpect(status().isBadRequest());
        confirm(receptionist, first, 100_000, "FTSAME" + first).andExpect(status().isOk());
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
    void invalidAmountsIdempotentRetriesAndTheOpenRequestLimit() throws Exception {
        create(member, 9_999, "ACTUAL", key()).andExpect(status().isBadRequest());
        create(member, 50_000_001, "ACTUAL", key()).andExpect(status().isBadRequest());
        String retry = key();
        long a = id(create(member, 20_000, "ACTUAL", retry).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long b = id(create(member, 20_000, "ACTUAL", retry).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(b).isEqualTo(a);
        create(member, 30_000, "ACTUAL", retry).andExpect(status().isConflict());
        create(member, 20_000, "ACTUAL", key()).andExpect(status().isCreated());
        create(member, 20_000, "ACTUAL", key()).andExpect(status().isCreated());
        create(member, 20_000, "ACTUAL", key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("pending top-ups")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_topup WHERE member_id=?", Integer.class, member)).isEqualTo(3);
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
        confirm(receptionist, id, 40_000, "FTCANCEL" + id).andExpect(status().isConflict());
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
        String ref = reference == null ? "null" : "\"" + reference + "\"";
        return mvc.perform(post("/api/v1/reception/wallet/topups/" + id + "/confirm").with(user(details(staff)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receivedAmount\":" + amount + ",\"bankReference\":" + ref + "}"));
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
