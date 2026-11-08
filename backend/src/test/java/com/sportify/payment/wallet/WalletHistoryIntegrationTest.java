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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

/** MW-08 wallet history on real SQL Server: complete own timeline, honest pending states, filters, reconciliation. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.wallet.simulated-topups-enabled=true")
class WalletHistoryIntegrationTest extends AbstractIntegrationTest {
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PlatformTransactionManager transactions;

    private final String marker = "whi" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    private int serial;
    private long member, otherMember, receptionist;

    @BeforeEach
    void fixture() {
        member = member();
        otherMember = member();
        receptionist = account("RECEPTIONIST");
    }

    @Test
    void timelineListsEveryOwnRecordOnceWithHonestStatesAndReconciles() throws Exception {
        long confirmed = topUp(member, 400_000, "ACTUAL");
        confirm(confirmed, 400_000, "FTH" + confirmed);
        confirm(confirmed, 400_000, "FTH" + confirmed);
        long pending = topUp(member, 50_000, "ACTUAL");
        long demo = topUp(member, 30_000, "SIMULATED");
        confirm(demo, 30_000, null);
        mvc.perform(put("/api/v1/member/wallet/payout-account").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankBin\":\"970436\",\"accountNumber\":\"0011001234567\",\"accountHolder\":\"HISTORY TEST\"}")).andExpect(status().isOk());
        long held = withdraw(100_000);
        mvc.perform(post("/api/v1/reception/wallet/withdrawals/" + held + "/approve").with(user(details(receptionist)))).andExpect(status().isOk());
        topUp(otherMember, 70_000, "ACTUAL");

        mvc.perform(get("/api/v1/member/wallet/history").with(user(details(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.reconciled").value(true))
                .andExpect(jsonPath("$.availableBalance").value(300000))
                .andExpect(jsonPath("$.reservedBalance").value(100000))
                .andExpect(jsonPath("$.page.content[?(@.id == " + pending + " && @.type == 'TOPUP')].direction").value("NONE"))
                .andExpect(jsonPath("$.page.content[?(@.id == " + pending + " && @.type == 'TOPUP')].availableAfter").value(org.hamcrest.Matchers.contains((Object) null)))
                .andExpect(jsonPath("$.page.content[?(@.id == " + held + " && @.type == 'WITHDRAWAL')].status").value("APPROVED"))
                .andExpect(jsonPath("$.page.content[?(@.id == " + held + " && @.type == 'WITHDRAWAL')].detail").value("To Vietcombank ****4567"))
                .andExpect(jsonPath("$.page.content[?(@.id == " + demo + " && @.type == 'TOPUP')].pool").value("SIMULATED"));
        String body = mvc.perform(get("/api/v1/member/wallet/history").with(user(details(member)))).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("0011001234567");
    }

    @Test
    void filtersAndPagingWorkAndOtherMembersRowsNeverAppear() throws Exception {
        long a = topUp(member, 20_000, "ACTUAL");
        confirm(a, 20_000, "FTF" + a);
        topUp(member, 25_000, "ACTUAL");
        topUp(otherMember, 99_000, "ACTUAL");
        mvc.perform(get("/api/v1/member/wallet/history?type=TOPUP&status=PENDING").with(user(details(member))))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.content[0].amount").value(25000));
        mvc.perform(get("/api/v1/member/wallet/history?size=1&page=1").with(user(details(member))))
                .andExpect(jsonPath("$.page.content.length()").value(1)).andExpect(jsonPath("$.page.totalPages").value(2));
        mvc.perform(get("/api/v1/member/wallet/history?from=2000-01-01&to=2000-01-02").with(user(details(member))))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get("/api/v1/member/wallet/history?type=BOGUS").with(user(details(member)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/member/wallet/history").with(user(details(otherMember))))
                .andExpect(jsonPath("$.page.content[?(@.amount == 25000)]").isEmpty());
        mvc.perform(get("/api/v1/member/wallet/history").with(user(details(receptionist)))).andExpect(status().isForbidden());
    }

    private long topUp(long memberId, long amount, String mode) throws Exception {
        return id(mvc.perform(post("/api/v1/member/wallet/topups").with(user(details(memberId))).header("Idempotency-Key", marker + ++serial)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + ",\"mode\":\"" + mode + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void confirm(long id, long amount, String reference) throws Exception {
        String ref = reference == null ? "null" : "\"" + reference + "\"";
        mvc.perform(post("/api/v1/reception/wallet/topups/" + id + "/confirm").with(user(details(receptionist)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"receivedAmount\":" + amount + ",\"bankReference\":" + ref + "}"));
    }

    private long withdraw(long amount) throws Exception {
        return id(mvc.perform(post("/api/v1/member/wallet/withdrawals").with(user(details(member))).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"pool\":\"ACTUAL\",\"confirmAccountLast4\":\"4567\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private long id(String json) {
        var m = java.util.regex.Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
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
