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
import org.springframework.test.web.servlet.MockMvc;
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
class ReceptionInboxIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

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
        member = member();
        receptionist = account("RECEPTIONIST");
        otherReceptionist = account("RECEPTIONIST");
    }

    @Test
    void aPendingTopUpGivesOneNoticeThatStaysOpenWhenReadAndResolvesWithTheTask() throws Exception {
        long topUp = createTopUp();
        String key = "WALLET_TOPUP:" + topUp;
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/reception/inbox").with(user(details(receptionist)))).andExpect(status().isOk());
        }
        assertThat(notices(receptionist, key)).isEqualTo(1);
        long notice = jdbc.queryForObject("SELECT id FROM notification WHERE user_id=? AND event_key=?", Long.class, receptionist, key);
        mvc.perform(put("/api/v1/reception/inbox/" + notice + "/read").with(user(details(receptionist))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.sourceStatus").value("PENDING"));
        assertThat(jdbc.queryForObject("SELECT status FROM wallet_topup WHERE id=?", String.class, topUp)).isEqualTo("PENDING");
        mvc.perform(post("/api/v1/reception/wallet/topups/" + topUp + "/confirm").with(user(details(otherReceptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receivedAmount\":30000,\"bankReference\":\"FTINB" + topUp + "\"}"))
                .andExpect(status().isOk());
        var items = inbox.inbox(receptionist, false).items().stream().filter(i -> i.id() == notice).toList();
        assertThat(items).singleElement().satisfies(i -> {
            assertThat(i.open()).isFalse();
            assertThat(i.sourceStatus()).isEqualTo("CONFIRMED");
        });
        assertThat(inbox.inbox(receptionist, true).items()).noneMatch(i -> i.id() == notice);
        inbox.sync(otherReceptionist);
        assertThat(notices(otherReceptionist, key)).isZero();
    }

    @Test
    void concurrentPollingNeverDuplicatesANotice() throws Exception {
        long topUp = createTopUp();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            results.add(pool.submit(() -> { start.await(); inbox.sync(receptionist); return null; }));
        }
        start.countDown();
        for (Future<Object> r : results) r.get();
        pool.shutdown();
        assertThat(notices(receptionist, "WALLET_TOPUP:" + topUp)).isEqualTo(1);
    }

    @Test
    void noticesHoldNoBankAccountAndOnlyReceptionistsReachTheInbox() throws Exception {
        long topUp = createTopUp();
        inbox.sync(receptionist);
        String text = jdbc.queryForObject("SELECT title + ' ' + content FROM notification WHERE user_id=? AND event_key=?",
                String.class, receptionist, "WALLET_TOPUP:" + topUp);
        assertThat(text).contains("30,000").doesNotContainPattern("[0-9]{9,}");
        long notice = jdbc.queryForObject("SELECT id FROM notification WHERE user_id=? AND event_key=?", Long.class,
                receptionist, "WALLET_TOPUP:" + topUp);
        mvc.perform(put("/api/v1/reception/inbox/" + notice + "/read").with(user(details(otherReceptionist)))).andExpect(status().isNotFound());
        for (String role : List.of("MEMBER", "MANAGER", "COACH")) {
            long id = "MEMBER".equals(role) ? member : account(role);
            mvc.perform(get("/api/v1/reception/inbox").with(user(details(id)))).andExpect(status().isForbidden());
        }
        assertThat(count("SELECT COUNT(*) FROM notification WHERE user_id=? AND type='RECEPTION_TASK'", member)).isZero();
    }

    @Test
    void aReceptionistAddedLaterStillSeesTasksThatAreOpenNow() {
        long topUp = createTopUp();
        long later = account("RECEPTIONIST");
        assertThat(inbox.inbox(later, true).items())
                .anyMatch(i -> i.sourceType().equals("WALLET_TOPUP") && i.sourceId() == topUp && !i.read());
    }

    private long createTopUp() {
        try {
            String body = mvc.perform(post("/api/v1/member/wallet/topups").with(user(details(member)))
                    .header("Idempotency-Key", marker + "-" + ++serial).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":30000,\"mode\":\"ACTUAL\"}")).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            var m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(body);
            assertThat(m.find()).isTrue();
            return Long.parseLong(m.group(1));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
