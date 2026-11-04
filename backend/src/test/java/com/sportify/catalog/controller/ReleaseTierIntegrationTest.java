package com.sportify.catalog.controller;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** New owned SQL fixtures and real authorization, service, audit and transaction boundaries. */
@AutoConfigureMockMvc
class ReleaseTierIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    long actor;
    String code;
    @AfterEach void cleanupOwnedRows() {
        String owners="SELECT id FROM user_account WHERE email IN (?,?)";
        Object[] emails={code+"@release.invalid",code+"_member@release.invalid"};
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN ("+owners+")",emails);
        jdbc.update("DELETE FROM member_card WHERE member_id IN ("+owners+")",emails);
        jdbc.update("DELETE FROM member_profile WHERE user_id IN ("+owners+")",emails);
        jdbc.update("DELETE FROM membership_card_tier WHERE code LIKE ?",code+"%");
        jdbc.update("DELETE FROM user_account WHERE email IN (?,?)",emails);
    }
    @BeforeEach void fixture() {
        code = ("REL_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture','Release manager',id,'ACTIVE' FROM role WHERE code='MANAGER'", code + "@release.invalid");
        actor = jdbc.queryForObject("SELECT id FROM user_account WHERE email=?", Long.class, code + "@release.invalid");
    }
    RequestPostProcessor as(String role) {
        return user(new CustomUserDetails(UserAccount.builder().id(actor).email(code + "@release.invalid")
                .fullName("Release actor").status("ACTIVE").version(0).role(Role.builder().code(role).build()).build()));
    }
    int rank() { return jdbc.queryForObject("SELECT MAX(tier_rank)+1 FROM membership_card_tier", Integer.class); }
    String body(String c, String name, int rank) {
        return "{\"code\":\""+c+"\",\"name\":\""+name+"\",\"tierRank\":"+rank+",\"price\":90000000,\"durationMonths\":12,\"discountPercentage\":15,\"active\":false}";
    }
    @Test void managerCreatesReadsAndAuditsNewTierWithoutChangingIssuedCards() throws Exception {
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture','Card owner',id,'ACTIVE' FROM role WHERE code='MEMBER'",code+"_member@release.invalid");
        long member=jdbc.queryForObject("SELECT id FROM user_account WHERE email=?",Long.class,code+"_member@release.invalid");
        jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,?)",member,code);
        jdbc.update("INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid) SELECT ?,?,id,CAST(GETDATE() AS date),'ACTIVE',price FROM membership_card_tier WHERE code='STANDARD'",code,member);
        var cards = jdbc.queryForList("SELECT id,tier_id,price_paid,status FROM member_card ORDER BY id");
        assertThat(cards).isNotEmpty();
        int rank = rank();
        mvc.perform(post("/api/v1/manager/card-tiers").with(as("MANAGER")).contentType("application/json").content(body(code,code,rank)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(code)).andExpect(jsonPath("$.tierRank").value(rank));
        mvc.perform(get("/api/v1/manager/card-tiers").with(as("MANAGER"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT duration_months FROM membership_card_tier WHERE code=?",Integer.class,code)).isEqualTo(12);
        assertThat(jdbc.queryForList("SELECT id,tier_id,price_paid,status FROM member_card ORDER BY id")).isEqualTo(cards);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='CARD_TIER_CREATED' AND actor_user_id=? AND entity_code=?",Integer.class,actor,code)).isEqualTo(1);
    }
    @Test void duplicateCodeNameAndRankConflict() throws Exception {
        int r=rank(); String first=body(code,code,r);
        mvc.perform(post("/api/v1/manager/card-tiers").with(as("MANAGER")).contentType("application/json").content(first)).andExpect(status().isCreated());
        for(String duplicate:List.of(body(code.toLowerCase(Locale.ROOT),code+"_2",r+1),body(code+"_2",code.toLowerCase(Locale.ROOT),r+1),body(code+"_3",code+"_3",r))) {
            mvc.perform(post("/api/v1/manager/card-tiers").with(as("MANAGER")).contentType("application/json").content(duplicate)).andExpect(status().isConflict());
        }
    }
    @Test void unauthorizedRolesCannotCreate() throws Exception {
        for(String role:List.of("COACH","MEMBER","RECEPTIONIST"))
            mvc.perform(post("/api/v1/manager/card-tiers").with(as(role)).contentType("application/json").content(body(code,code,rank()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/manager/card-tiers").contentType("application/json").content(body(code,code,rank()))).andExpect(status().isUnauthorized());
    }
    @Test void invalidValuesAndReservedStandardAreRejected() throws Exception {
        String valid=body(code,code,rank());
        for(String invalid:List.of(valid.replace("90000000","-1"),valid.replace("\"durationMonths\":12","\"durationMonths\":0"),valid.replace("\"discountPercentage\":15","\"discountPercentage\":101"),valid.replace("\"name\":\""+code+"\"","\"name\":\" \"")))
            mvc.perform(post("/api/v1/manager/card-tiers").with(as("MANAGER")).contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/manager/card-tiers").with(as("MANAGER")).contentType("application/json").content(body("STANDARD",code,rank()))).andExpect(status().isConflict());
    }
    @Test void concurrentCreateHasExactlyOneWinner() throws Exception {
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1); String payload=body(code,code,rank());
        try {
            Callable<Integer> call=()->{ start.await(); return mvc.perform(post("/api/v1/manager/card-tiers").with(as("MANAGER")).contentType("application/json").content(payload)).andReturn().getResponse().getStatus(); };
            var a=pool.submit(call); var b=pool.submit(call); start.countDown();
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM membership_card_tier WHERE code=?",Integer.class,code)).isEqualTo(1);
        } finally { pool.shutdownNow(); assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
}
