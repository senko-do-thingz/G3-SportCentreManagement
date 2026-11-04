package com.sportify.payment;

import com.sportify.AbstractIntegrationTest;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ReleaseVietQrIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.sportify.identity.repository.UserRepository users;
    String marker="VQR"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
    List<Long> accounts=new ArrayList<>();
    UserAccount account(String role) {
        String email=marker+accounts.size()+"@release.invalid";
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture','VietQR fixture',id,'ACTIVE' FROM role WHERE code=?",email,role);
        var account=users.findByEmailIgnoreCase(email).orElseThrow(); accounts.add(account.getId());
        if(role.equals("MEMBER")) jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,?)",account.getId(),marker+accounts.size());
        return account;
    }
    @AfterEach void cleanup() {
        for(long id:accounts) {
            jdbc.update("DELETE FROM activity_log WHERE actor_user_id=?",id);
            jdbc.update("DELETE FROM payment WHERE member_id=?",id);
            jdbc.update("DELETE FROM member_profile WHERE user_id=?",id);
            jdbc.update("DELETE FROM user_account WHERE id=?",id);
        }
    }
    @Test void ownedInstructionsRemainNonPayablePendingAndPrivate() throws Exception {
        var member=account("MEMBER"); var peer=account("MEMBER"); var coach=account("COACH");
        jdbc.update("INSERT payment(payment_code,member_id,amount,payment_method,payment_status) VALUES (?,?,79000,'BANK_TRANSFER','PENDING')",marker,member.getId());
        long id=jdbc.queryForObject("SELECT id FROM payment WHERE payment_code=?",Long.class,marker);
        String path="/api/v1/payments/"+id+"/vietqr-instructions";
        mvc.perform(get(path).with(user(new CustomUserDetails(member)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.payable").value(false)).andExpect(jsonPath("$.qrPayload").isEmpty())
                .andExpect(jsonPath("$.notice").value(org.hamcrest.Matchers.containsString("DEMO NON-PAYABLE")));
        mvc.perform(get(path).with(user(new CustomUserDetails(peer)))).andExpect(status().isNotFound());
        mvc.perform(get(path).with(user(new CustomUserDetails(coach)))).andExpect(status().isForbidden());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/"+id).with(user(new CustomUserDetails(member)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.bankTransferInstructions.accountNumber").value("NOT CONFIGURED"));
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?",String.class,id)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE payment_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_event WHERE payment_id=?",Integer.class,id)).isZero();
    }
}
