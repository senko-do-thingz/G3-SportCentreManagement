package com.sportify.catalog.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MSSQLServerContainer;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("dev")
class SessionIdentityIntegrationTest {
    private static final AtomicInteger DAY = new AtomicInteger();
    private static MSSQLServerContainer<?> container;
    @Autowired JdbcTemplate jdbc;
    @Autowired SessionCodeAllocator allocator;
    @Autowired PlatformTransactionManager transactions;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.sportify.identity.repository.UserRepository users;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (System.getenv("UNIFY_TEST_SQL_PORT") != null) {
            var fixture = com.sportify.support.DisposableUnifyDatabase.create();
            registry.add("spring.datasource.url", fixture::url);
            registry.add("spring.datasource.username", fixture::user);
            registry.add("spring.datasource.password", fixture::password);
            return;
        }
        String local = System.getenv("ID_TEST_DB_URL");
        if (local != null && !local.isBlank()) {
            if (!local.matches("jdbc:sqlserver://[^;]+;databaseName=sportify_id_test_[0-9]{14}(;.*)?"))
                throw new IllegalArgumentException("A fresh isolated ID test database is required");
            registry.add("spring.datasource.url", () -> local);
            registry.add("spring.datasource.username", () -> System.getenv("ID_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("ID_TEST_DB_PASSWORD"));
        } else {
            if (container == null) {
                container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
                container.start();
            }
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
    }
    private LocalDate date() { return LocalDate.of(2080,1,1).plusDays(DAY.incrementAndGet()); }
    private String reserve(LocalDate date,String sport) {
        return new TransactionTemplate(transactions).execute(tx -> allocator.reserve(date,sport));
    }
    private long insert(LocalDate date,String code,String status) {
        jdbc.update("INSERT class_session(sport_id,session_date,start_time,end_time,training_type,capacity,booked_count,status,session_code,facility_id) "
            + "SELECT s.id,?,'08:00','09:00','SELF_TRAINING',10,0,?,?,(SELECT MIN(id) FROM facility WHERE sport_id=s.id AND is_active=1) FROM sport s WHERE code='FOOTBALL'",date,status,code);
        return jdbc.queryForObject("SELECT id FROM class_session WHERE session_code=?",Long.class,code);
    }
    @Test void allSportsAndDatesAllocateIndependentSequences() {
        LocalDate d=date();
        for(String sport:List.of("FOOTBALL","SWIMMING","BADMINTON","VOLLEYBALL","BASKETBALL","TENNIS")) {
            assertThat(reserve(d,sport)).isEqualTo(SessionCodeAllocator.format(d,sport,1));
            assertThat(reserve(d,sport)).isEqualTo(SessionCodeAllocator.format(d,sport,2));
            assertThat(reserve(d.plusYears(1),sport)).isEqualTo(SessionCodeAllocator.format(d.plusYears(1),sport,1));
        }
    }
    @Test void concurrentTransactionsCannotIssueDuplicateCodes() throws Exception {
        LocalDate d=date();
        try(var pool=Executors.newFixedThreadPool(8)) {
            var start=new CountDownLatch(1);
            var futures=new ArrayList<java.util.concurrent.Future<String>>();
            for(int i=0;i<8;i++) futures.add(pool.submit(()->{start.await();return reserve(d,"BADMINTON");}));
            start.countDown();
            var values=new HashSet<String>();
            for(var f:futures) values.add(f.get(30,TimeUnit.SECONDS));
            assertThat(values).hasSize(8).contains(SessionCodeAllocator.format(d,"BADMINTON",1),SessionCodeAllocator.format(d,"BADMINTON",8));
        }
    }
    @Test void cancellationAndPhysicalRecreationNeverReuseAnIssuedCode() {
        LocalDate d=date();String first=reserve(d,"FOOTBALL");
        long id=insert(d,first,"PUBLISHED");
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?",id);
        assertThat(reserve(d,"FOOTBALL")).endsWith("02");
        jdbc.update("DELETE FROM class_session WHERE id=?",id);
        assertThat(reserve(d,"FOOTBALL")).endsWith("03");
    }
    @Test void allocationLimitRejectsAtomicallyAndKeeps99Reserved() {
        LocalDate d=date();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{for(int i=1;i<=99;i++)allocator.reserve(d,"TENNIS");});
        assertThatThrownBy(()->reserve(d,"TENNIS")).hasMessageContaining("99");
        assertThat(jdbc.queryForObject("SELECT last_value FROM session_code_counter WHERE session_date=? AND sport_code='TE'",Integer.class,d)).isEqualTo(99);
    }
    @Test void failedMultiDateBatchRollsBackAllReservationsAndRows() {
        LocalDate d=date();
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{
            insert(d,allocator.reserve(d,"FOOTBALL"),"PUBLISHED");
            allocator.reserve(d.plusDays(1),"SWIMMING");
            throw new IllegalStateException("Isolated batch failure");
        })).hasMessageContaining("batch failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM class_session WHERE session_date=?",Integer.class,d)).isZero();
        assertThat(reserve(d,"FOOTBALL")).endsWith("01");
    }
    @Test void databaseRejectsDuplicateInvalidAndMismatchedDateIdentities() {
        LocalDate d=date();String code=reserve(d,"FOOTBALL");insert(d,code,"PUBLISHED");
        assertThatThrownBy(()->insert(d,code,"PUBLISHED")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->insert(d,"not-a-code","PUBLISHED")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->insert(d.plusDays(1),code,"PUBLISHED")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='class_session' AND COLUMN_NAME='session_code'",String.class)).isEqualTo("NO");
    }

    private com.sportify.identity.security.CustomUserDetails actor(String role) {
        String email="id-api-"+java.util.UUID.randomUUID()+"@sportify.test";
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture',?,id,'ACTIVE' FROM role WHERE code=?",email,"Identity "+role,role);
        long id=jdbc.queryForObject("SELECT id FROM user_account WHERE email=?",Long.class,email);
        if(role.equals("MEMBER"))jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,?)",id,"ID-"+id);
        if(role.equals("COACH")) {
            jdbc.update("INSERT coach_profile(user_id) VALUES (?)",id);
            jdbc.update("INSERT coach_sport(coach_id,sport_id) SELECT ?,id FROM sport WHERE code='FOOTBALL'",id);
        }
        return new TransactionTemplate(transactions).execute(tx->{var user=users.findById(id).orElseThrow();user.getRole().getCode();return new com.sportify.identity.security.CustomUserDetails(user);});
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder get(String path,com.sportify.identity.security.CustomUserDetails actor) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(actor));
    }
    @Test void publicLookupMatchesLegacyAndRejectsMalformedHiddenAndUnauthorizedRequests() throws Exception {
        LocalDate d=date();String code=reserve(d,"FOOTBALL");long id=insert(d,code,"PUBLISHED");
        var manager=actor("MANAGER");var member=actor("MEMBER");var reception=actor("RECEPTIONIST");
        for(var actor:List.of(manager,member,reception)) {
            mvc.perform(get("/api/v1/sessions/by-code/"+code,actor))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.session.sessionId").value(code))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.session.id").value(id));
        }
        mvc.perform(get("/api/v1/sessions/"+id,manager)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(get("/api/v1/sessions/by-code/310226FB01",manager)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(get("/api/v1/sessions/by-code/010100TE99",manager)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(get("/api/v1/sessions/by-code/"+code,actor("COACH"))).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/sessions/by-code/"+code)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?",id);
        mvc.perform(get("/api/v1/sessions/by-code/"+code,member)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(get("/api/v1/manager/sessions/by-code/"+code,manager)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sessionId").value(code));
    }
    @Test void roleProjectionsAndRostersUsePersistedIdentityWithoutLeakingAnotherCoachsMembers() throws Exception {
        LocalDate d=date();String code=reserve(d,"FOOTBALL");long id=insert(d,code,"PUBLISHED");
        var coach=actor("COACH");var other=actor("COACH");var member=actor("MEMBER");var reception=actor("RECEPTIONIST");
        long memberId=member.getUserAccount().getId();
        jdbc.update("UPDATE class_session SET coach_id=?,training_type='COACH_LED',booked_count=1 WHERE id=?",coach.getUserAccount().getId(),id);
        jdbc.update("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')","ID-"+java.util.UUID.randomUUID().toString().substring(0,16),id,memberId);
        mvc.perform(get("/api/v1/coach/training/sessions/by-code/"+code+"/members",coach)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].memberId").value(memberId));
        mvc.perform(get("/api/v1/coach/training/sessions/by-code/"+code+"/members",other)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(get("/api/v1/reception/training/sessions/by-code/"+code+"/members",member)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(get("/api/v1/coach/training/sessions",coach)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].sessionId").value(code))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].bookedCount").value(1))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].markedCount").value(0));
        mvc.perform(get("/api/v1/coach/sessions?from="+d+"&to="+d,coach)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].sessionId").value(code));
        mvc.perform(get("/api/v1/coach/sessions/by-code/"+code+"/roster",other)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(get("/api/v1/reception/training/sessions/by-code/"+code+"/members",reception)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(get("/api/v1/member/training/attendance",member)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].session.sessionId").value(code));
    }
    @Test void codeBasedResultRetainsAttendanceRulesAndAuditsTheSamePublicIdentity() throws Exception {
        LocalDate d=LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
        String code=reserve(d,"FOOTBALL");long id=insert(d,code,"PUBLISHED");
        var coach=actor("COACH");var member=actor("MEMBER");var other=actor("COACH");
        long memberId=member.getUserAccount().getId();long coachId=coach.getUserAccount().getId();
        jdbc.update("UPDATE class_session SET coach_id=?,training_type='COACH_LED' WHERE id=?",coachId,id);
        jdbc.update("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')","ID-R-"+java.util.UUID.randomUUID().toString().substring(0,15),id,memberId);
        String body="{\"memberId\":"+memberId+",\"notes\":\"Verified session result\",\"scores\":[]}";
        String path="/api/v1/coach/training/sessions/by-code/"+code+"/results";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(coach)).contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        jdbc.update("INSERT attendance_record(session_id,member_id,status,marked_by) VALUES (?,?,'PRESENT',?)",id,memberId,coachId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(other)).contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(coach)).contentType("application/json").content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='TRAINING_RESULT_CREATED' AND JSON_VALUE(details,'$.sessionCode')=?",Integer.class,code)).isEqualTo(1);
        mvc.perform(get("/api/v1/member/training/results",member)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].session.sessionId").value(code));
    }
}
