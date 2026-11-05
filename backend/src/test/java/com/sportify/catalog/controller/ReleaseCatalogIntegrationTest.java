package com.sportify.catalog.controller;

import com.sportify.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ReleaseCatalogIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired com.sportify.identity.repository.UserRepository users;
    String code;
    UserAccount manager;
    Long fixtureSession;
    @Autowired java.time.Clock clock;
    @BeforeEach void fixture() {
        code=("CAT_"+UUID.randomUUID().toString().replace("-","").substring(0,10)).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture','Catalog manager',id,'ACTIVE' FROM role WHERE code='MANAGER'",code+"@release.invalid");
        manager=users.findByEmailIgnoreCase(code+"@release.invalid").orElseThrow();
    }
    @AfterEach void cleanup() {
        if(fixtureSession!=null) jdbc.update("DELETE FROM class_session WHERE id=?",fixtureSession);
        jdbc.update("DELETE FROM activity_log WHERE actor_user_id=?",manager.getId());
        jdbc.update("DELETE FROM facility WHERE code=?",code);
        jdbc.update("DELETE FROM sport WHERE code=?",code);
        jdbc.update("DELETE FROM user_account WHERE id=?",manager.getId());
    }
    RequestPostProcessor manager() { return user(new CustomUserDetails(manager)); }
    String sport(boolean active) throws Exception { return json.writeValueAsString(Map.of("code",code,"name",code,"venueType","INDOOR","displayOrder",90,"active",active)); }
    String facility(long sport,boolean active) throws Exception { return json.writeValueAsString(Map.of("code",code,"name",code,"facilityType","HALL","sportId",sport,"capacity",20,"active",active)); }
    @Test void managerCreatesUpdatesAndDeactivatesCatalogWithPublicReadback() throws Exception {
        var created=mvc.perform(post("/api/v1/manager/catalog/sports").with(manager()).contentType("application/json").content(sport(true))).andExpect(status().isCreated()).andReturn();
        long sport=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/v1/sports")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code == '"+code+"')].name").value(org.hamcrest.Matchers.hasItem(code)));
        var room=mvc.perform(post("/api/v1/manager/catalog/facilities").with(manager()).contentType("application/json").content(facility(sport,true))).andExpect(status().isCreated()).andReturn();
        long facility=json.readTree(room.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(put("/api/v1/manager/catalog/facilities/"+facility).with(manager()).contentType("application/json").content(facility(sport,false))).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/v1/manager/catalog/sports/"+sport).with(manager()).contentType("application/json").content(sport(false))).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE actor_user_id=? AND action IN ('SPORT_CREATED','SPORT_UPDATED','FACILITY_CREATED','FACILITY_UPDATED')",Integer.class,manager.getId())).isEqualTo(4);
    }
    @Test void duplicateAndInvalidCatalogWritesAreRejected() throws Exception {
        mvc.perform(post("/api/v1/manager/catalog/sports").with(manager()).contentType("application/json").content(sport(true))).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/manager/catalog/sports").with(manager()).contentType("application/json").content(sport(true))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/manager/catalog/facilities").with(manager()).contentType("application/json").content(facility(9999999,true))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/manager/catalog/facilities").with(manager()).contentType("application/json").content(facility(1,true).replace("\"capacity\":20","\"capacity\":0"))).andExpect(status().isBadRequest());
    }
    @Test void membersAndStaffCannotMaintainCatalog() throws Exception {
        for(String role:List.of("MEMBER","COACH","RECEPTIONIST")) {
            var actor=UserAccount.builder().id(manager.getId()).email(manager.getEmail()).status("ACTIVE").role(Role.builder().code(role).build()).build();
            mvc.perform(post("/api/v1/manager/catalog/sports").with(user(new CustomUserDetails(actor))).contentType("application/json").content(sport(true))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/manager/catalog/sports")).andExpect(status().isUnauthorized());
    }
    @Test void scheduledFacilityCannotBeDeactivatedReassignedOrShrunk() throws Exception {
        long sport=jdbc.queryForObject("SELECT MIN(id) FROM sport",Long.class);
        var room=mvc.perform(post("/api/v1/manager/catalog/facilities").with(manager()).contentType("application/json").content(facility(sport,true))).andExpect(status().isCreated()).andReturn();
        long id=json.readTree(room.getResponse().getContentAsString()).get("id").asLong();
        var date=java.time.LocalDate.now(clock).plusDays(3);
        String publicCode=com.sportify.support.SessionIdentityFixtures.reserve(jdbc,date,sport);
        jdbc.update("INSERT class_session(sport_id,facility_id,session_date,start_time,end_time,training_type,capacity,status,session_code) VALUES (?,?,?,'09:00','10:00','SELF_TRAINING',20,'PUBLISHED',?)",sport,id,date,publicCode);
        fixtureSession=jdbc.queryForObject("SELECT id FROM class_session WHERE session_code=?",Long.class,publicCode);
        for(String body:List.of(facility(sport,false),facility(sport,true).replace("\"capacity\":20","\"capacity\":10")))
            mvc.perform(put("/api/v1/manager/catalog/facilities/"+id).with(manager()).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT capacity FROM facility WHERE id=?",Integer.class,id)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT is_active FROM facility WHERE id=?",Boolean.class,id)).isTrue();
    }
}
