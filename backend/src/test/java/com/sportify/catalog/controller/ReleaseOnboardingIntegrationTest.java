package com.sportify.catalog.controller;

import com.sportify.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ReleaseOnboardingIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.sportify.identity.repository.UserRepository users;
    String fixtureMarker;
    @AfterEach void cleanupOwnedAccounts() {
        if(fixtureMarker==null) return;
        String owners="SELECT id FROM user_account WHERE email IN (?,?)";
        Object[] emails={fixtureMarker+"_desk@release.invalid",fixtureMarker+"@release.invalid"};
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id IN ("+owners+")",emails);
        jdbc.update("DELETE FROM member_profile WHERE user_id IN ("+owners+")",emails);
        jdbc.update("DELETE FROM user_account WHERE email IN (?,?)",emails);
    }
    @Test void receptionCreatesMemberWithDurableAuditLoginAndPrivateCodeReadback() throws Exception {
        String marker=UUID.randomUUID().toString().replace("-", "").substring(0,12);
        fixtureMarker=marker;
        String staff=marker+"_desk@release.invalid", email=marker+"@release.invalid";
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture','Release reception',id,'ACTIVE' FROM role WHERE code='RECEPTIONIST'",staff);
        var receptionist=users.findByEmailIgnoreCase(staff).orElseThrow();
        String phone="09"+String.format("%010d",Math.abs((long)marker.hashCode()));
        String request=json.writeValueAsString(java.util.Map.of("fullName","Release member","email",email,"phone",phone));
        var response=mvc.perform(post("/api/v1/members").with(user(new CustomUserDetails(receptionist))).contentType("application/json").content(request))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        var member=json.readTree(response.getResponse().getContentAsString()); long id=member.get("userId").asLong();
        String code=member.get("memberCode").asText(), password=member.get("temporaryPassword").asText();
        assertThat(code).startsWith("MEM-");
        assertThat(jdbc.queryForObject("SELECT r.code FROM user_account u JOIN role r ON r.id=u.role_id WHERE u.id=?",String.class,id)).isEqualTo("MEMBER");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='USER_CREATED' AND actor_user_id=? AND entity_id=?",Integer.class,receptionist.getId(),id)).isEqualTo(1);
        var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email",email,"password",password))))
                .andExpect(status().isOk()).andReturn();
        var token=json.readTree(login.getResponse().getContentAsString());
        String access=token.has("accessToken")?token.get("accessToken").asText():token.get("access_token").asText();
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+access)).andExpect(status().isOk()).andExpect(jsonPath("$.memberCode").value(code));
        mvc.perform(post("/api/v1/members").with(user(new CustomUserDetails(receptionist))).contentType("application/json").content(request)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/members").header("Authorization","Bearer "+access).contentType("application/json").content(request)).andExpect(status().isForbidden());
    }
}
