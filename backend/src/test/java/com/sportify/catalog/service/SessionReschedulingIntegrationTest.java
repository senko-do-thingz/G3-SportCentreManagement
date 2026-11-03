package com.sportify.catalog.service;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.repository.*;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SQL locking, registry, role and transaction regression on owned disposable targets only. */
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import(SessionReschedulingIntegrationTest.Time.class)
class SessionReschedulingIntegrationTest {
    @TestConfiguration static class Time {
        @Bean @Primary Clock reschedulingClock() {
            return Clock.fixed(Instant.parse("2026-11-03T01:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        SessionIdentityIntegrationTest.database(registry);
    }
    private static final AtomicInteger NEXT = new AtomicInteger();
    @Autowired ManagerSessionService service;
    @Autowired ClassSessionRepository sessions;
    @Autowired SportClassRepository classes;
    @Autowired SportRepository sports;
    @Autowired FacilityRepository facilities;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @Autowired SessionCodeAllocator allocator;
    @MockitoSpyBean AuditService audit;
    private UserAccount manager;
    private SportClass sportClass;
    private Facility facility;
    private LocalDate source;
    private String marker;

    @BeforeEach void fixture() {
        marker="rs-"+UUID.randomUUID().toString().substring(0,12);
        source=LocalDate.of(2085,1,1).plusDays(NEXT.incrementAndGet()*3L);
        manager=actor("MANAGER");
        Sport sport=sports.findAll().stream().filter(s->s.getCode().equals("SWIMMING")).findFirst().orElseThrow();
        sportClass=classes.saveAndFlush(SportClass.builder().code(marker).name("Rescheduling fixture")
                .sport(sport).maxMembers(20).isActive(true).build());
        facility=newFacility(marker);
    }
    private UserAccount actor(String role) {
        String email=UUID.randomUUID()+"@sportify.test";
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture','Rescheduling fixture',id,'ACTIVE' FROM role WHERE code=?",email,role);
        long id=jdbc.queryForObject("SELECT id FROM user_account WHERE email=?",Long.class,email);
        return new TransactionTemplate(transactions).execute(tx->{var u=users.findById(id).orElseThrow();u.getRole().getCode();return u;});
    }
    private Facility newFacility(String code) {
        return facilities.saveAndFlush(Facility.builder().code(code).name("Isolated pool")
                .facilityType(FacilityType.POOL).sport(sportClass.getSport()).capacity(20).isActive(true).build());
    }
    private SessionCreateRequest create(LocalDate date, long resource) {
        return new SessionCreateRequest(date,null,null,null,LocalTime.of(10,0),LocalTime.of(11,0),
                resource,null,16,TrainingFormat.SELF_TRAINING,SessionStatus.PUBLISHED);
    }
    private ManagerSessionResponse create(LocalDate date) {
        return service.createSessions(sportClass.getId(),create(date,facility.getId()),manager).getFirst();
    }
    private SessionUpdateRequest move(ManagerSessionResponse current, LocalDate target) {
        return new SessionUpdateRequest(target,LocalTime.of(10,0),LocalTime.of(11,0),current.facility().id(),null,16,current.scheduleRevision(),"Pool maintenance");
    }
    private int count(String sql,Object... args) {return jdbc.queryForObject(sql,Integer.class,args);}
    private String canonical(long id) {return jdbc.queryForObject("SELECT session_code FROM class_session WHERE id=?",String.class,id);}
    private long booking(long id, LocalDate from, LocalDate to) {
        var member=actor("MEMBER");
        jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,?)",member.getId(),"RS-"+member.getId());
        jdbc.update("INSERT sport_package(code,name,sport_id,training_format,duration_days,session_count,price_amount,session_minutes) VALUES (?, 'Fixture package',?,'SELF_TRAINING',30,10,100,60)",marker,sportClass.getSport().getId());
        long pack=jdbc.queryForObject("SELECT id FROM sport_package WHERE code=?",Long.class,marker);
        jdbc.update("INSERT sport_package_registration(registration_code,member_id,package_id,channel,original_price,paid_amount,total_sessions,remaining_sessions,start_date,end_date,status,created_by_user_id) VALUES (?,?,?,'RECEPTION',100,100,10,9,?,?,'ACTIVE',?)",marker,member.getId(),pack,from,to,manager.getId());
        long reg=jdbc.queryForObject("SELECT id FROM sport_package_registration WHERE registration_code=?",Long.class,marker);
        jdbc.update("INSERT booking(booking_code,session_id,member_id,package_registration_id,status) VALUES (?,?,?,?,'CONFIRMED')",marker,id,member.getId(),reg);
        jdbc.update("UPDATE class_session SET booked_count=1 WHERE id=?",id);
        return jdbc.queryForObject("SELECT id FROM booking WHERE booking_code=?",Long.class,marker);
    }

    @Test void illustratedMoveToTodayUsesNewDateAndNeverRenumbersTheSurrogate() {
        var current=create(LocalDate.of(2026,11,6));
        assertThat(current.sessionId()).isEqualTo("061126SW01");
        LocalDate target=LocalDate.of(2026,11,3);
        int before=count("SELECT COALESCE(MAX(last_value),0) FROM session_code_counter WHERE session_date=? AND sport_code='SW'",target);
        assertThat(before).isZero();
        var saved=service.updateSession(current.id(),move(current,target),manager);
        assertThat(saved.id()).isEqualTo(current.id());
        assertThat(saved.sessionId()).isEqualTo(SessionCodeAllocator.format(target,"SWIMMING",before+1));
        assertThat(saved.date()).isEqualTo(target);
        assertThat(service.getSessionByCode(current.sessionId()).sessionId()).isEqualTo(saved.sessionId());
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE entity_id=? AND action='SESSION_RESCHEDULED' AND actor_user_id=? AND JSON_VALUE(details,'$.before.sessionId')=? AND JSON_VALUE(details,'$.after.sessionId')=?",current.id(),manager.getId(),current.sessionId(),saved.sessionId())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM session_code_history WHERE session_code=? AND retired_at_utc IS NOT NULL AND retired_by_user_id=? AND retirement_reason='Pool maintenance'",current.sessionId(),manager.getId())).isEqualTo(1);
    }

    @Test void occupiedTargetAndMoveBackNeverReuseOldCodesOrMoveBookingsAndCredits() {
        var current=create(source);
        long booking=booking(current.id(),source.minusDays(10),source.plusDays(10));
        var second=newFacility(marker+"-2");
        var occupied=service.createSessions(sportClass.getId(),create(source.plusDays(1),second.getId()),manager).getFirst();
        var saved=service.updateSession(current.id(),move(current,source.plusDays(1)),manager);
        assertThat(occupied.sessionId()).endsWith("SW01");
        assertThat(saved.sessionId()).endsWith("SW02");
        var movedBack=service.updateSession(current.id(),move(saved,source),manager);
        assertThat(movedBack.sessionId()).endsWith("SW02");
        assertThat(service.getSessionByCode(current.sessionId()).id()).isEqualTo(current.id());
        assertThat(service.getSessionByCode(saved.sessionId()).sessionId()).isEqualTo(movedBack.sessionId());
        assertThat(count("SELECT COUNT(*) FROM booking WHERE id=? AND session_id=? AND status='CONFIRMED'",booking,current.id())).isEqualTo(1);
        assertThat(count("SELECT remaining_sessions FROM sport_package_registration WHERE registration_code=?",marker)).isEqualTo(9);
        assertThat(count("SELECT booked_count FROM class_session WHERE id=?",current.id())).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("UPDATE class_session SET session_code=? WHERE id=?",current.sessionId(),current.id())).hasStackTraceContaining("can never be reassigned");
    }

    @Test void packageWindowFailurePreservesIdentityHistoryBookingAndCounter() {
        var current=create(source); booking(current.id(),source,source);
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("validity period");
        assertThat(canonical(current.id())).isEqualTo(current.sessionId());
        assertThat(count("SELECT COUNT(*) FROM session_code_history WHERE session_id=?",current.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM session_code_counter WHERE session_date=? AND sport_code='SW'",source.plusDays(1))).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"attendance_record","session_result","check_in"})
    void recordedActivityPreventsTimingMoves(String table) {
        var current=create(source); long booking=booking(current.id(),source,source.plusDays(3));
        long member=jdbc.queryForObject("SELECT member_id FROM booking WHERE id=?",Long.class,booking);
        if (table.equals("attendance_record")) jdbc.update("INSERT attendance_record(session_id,member_id,status,marked_by) VALUES (?,?,'PRESENT',?)",current.id(),member,manager.getId());
        else if (table.equals("session_result")) jdbc.update("INSERT session_result(session_id,member_id,notes) VALUES (?,?,'Saved result')",current.id(),member);
        else jdbc.update("INSERT check_in(member_id,recorded_by,booking_id,result) VALUES (?,?,?,'ALLOWED')",member,manager.getId(),booking);
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot be rescheduled");
        assertThat(canonical(current.id())).isEqualTo(current.sessionId());
    }

    @Test void auditFailureRollsBackDateCodeRevisionRegistryAndCounter() {
        var current=create(source);
        doThrow(new IllegalStateException("Isolated audit failure")).when(audit).record(argThat((AuditEvent e)->e!=null&&"SESSION_RESCHEDULED".equals(e.getAction())));
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager)).hasMessageContaining("audit failure");
        assertThat(canonical(current.id())).isEqualTo(current.sessionId());
        assertThat(count("SELECT schedule_revision FROM class_session WHERE id=?",current.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM session_code_history WHERE session_id=? AND retired_at_utc IS NULL",current.id())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM session_code_counter WHERE session_date=? AND sport_code='SW'",source.plusDays(1))).isZero();
    }

    @Test void occupiedFacilityRejectsTheMoveWithoutAllocatingAnIdentity() {
        var current=create(source);
        create(source.plusDays(1));
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager))
                .isInstanceOf(com.sportify.catalog.exception.ScheduleConflictException.class);
        assertThat(canonical(current.id())).isEqualTo(current.sessionId());
        assertThat(count("SELECT last_value FROM session_code_counter WHERE session_date=? AND sport_code='SW'",source.plusDays(1))).isEqualTo(1);
    }

    @Test void exhaustedTargetSequenceLeavesOriginalIdentityAndRevisionUntouched() {
        var current=create(source);
        new TransactionTemplate(transactions).executeWithoutResult(tx->{
            for(int i=0;i<99;i++) allocator.reserve(source.plusDays(1),"SWIMMING");
        });
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("99");
        assertThat(canonical(current.id())).isEqualTo(current.sessionId());
        assertThat(count("SELECT schedule_revision FROM class_session WHERE id=?",current.id())).isZero();
        assertThat(count("SELECT COUNT(*) FROM session_code_history WHERE session_id=?",current.id())).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"CANCELLED","COMPLETED"})
    void ineligibleStatusCannotMove(String status) {
        var current=create(source); jdbc.update("UPDATE class_session SET status=? WHERE id=?",status,current.id());
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager)).isInstanceOf(BusinessRuleException.class);
        assertThat(canonical(current.id())).isEqualTo(current.sessionId());
    }

    @Test void pastStartedAndNonFutureTargetAreRejected() {
        var current=create(source);
        for(LocalDate target:List.of(LocalDate.of(2026,11,2),LocalDate.of(1999,1,1)))
            assertThatThrownBy(()->service.updateSession(current.id(),move(current,target),manager)).isInstanceOf(BusinessRuleException.class);
        var today=new SessionUpdateRequest(LocalDate.of(2026,11,3),LocalTime.of(8,0),LocalTime.of(9,0),facility.getId(),null,16,0L,null);
        assertThatThrownBy(()->service.updateSession(current.id(),today,manager)).isInstanceOf(BusinessRuleException.class);
        String past=SessionCodeAllocator.format(LocalDate.of(2026,10,1),"SWIMMING",1);
        jdbc.update("UPDATE class_session SET session_date='2026-10-01',session_code=? WHERE id=?",past,current.id());
        assertThatThrownBy(()->service.updateSession(current.id(),move(current,source.plusDays(1)),manager)).hasMessageContaining("already started");
    }

    @Test void concurrentEditsCannotLoseAnUpdateAndCreateCannotDuplicateAnAllocatedCode() throws Exception {
        var current=create(source); var second=newFacility(marker+"-2");
        LocalDate target=source.plusDays(1); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            Callable<Object> edit=()->{start.await();try{return service.updateSession(current.id(),move(current,target),manager);}catch(ConflictException ex){return ex;}};
            var a=pool.submit(edit);var b=pool.submit(edit);
            var c=pool.submit(()->{start.await();return service.createSessions(sportClass.getId(),create(target,second.getId()),manager).getFirst();});
            start.countDown();Object first=a.get(30,TimeUnit.SECONDS),other=b.get(30,TimeUnit.SECONDS);var created=c.get(30,TimeUnit.SECONDS);
            assertThat(List.of(first,other).stream().filter(ConflictException.class::isInstance).count()).isEqualTo(1);
            assertThat(created.sessionId()).isNotEqualTo(canonical(current.id()));
            assertThat(count("SELECT schedule_revision FROM class_session WHERE id=?",current.id())).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM session_code_history WHERE issued_session_date=?",target)).isEqualTo(2);
        }
    }

    @Test void aliasesKeepRoleScopeAndCanonicalResponsesAndRejectStaleWrites() throws Exception {
        var current=create(source); var saved=service.updateSession(current.id(),move(current,source.plusDays(1)),manager);
        String path="/api/v1/manager/sessions/by-code/"+current.sessionId();
        mvc.perform(get(path).with(user(new CustomUserDetails(manager)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(current.id())).andExpect(jsonPath("$.sessionId").value(saved.sessionId()));
        String staleBody="{\"date\":\""+source+"\",\"startTime\":\"10:00:00\",\"endTime\":\"11:00:00\",\"facilityId\":"+facility.getId()+",\"capacity\":16,\"expectedRevision\":0}";
        for(String role:List.of("MEMBER","COACH","RECEPTIONIST"))
            mvc.perform(put(path).with(user(new CustomUserDetails(actor(role)))).contentType("application/json").content(staleBody))
                    .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/sessions/by-code/"+current.sessionId()).with(user(new CustomUserDetails(manager))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.sessionId").value(saved.sessionId()));
        mvc.perform(put(path).with(user(new CustomUserDetails(manager))).contentType("application/json")
                .content("{\"date\":\""+source+"\",\"startTime\":\"10:00:00\",\"endTime\":\"11:00:00\",\"facilityId\":"+facility.getId()+",\"capacity\":16,\"expectedRevision\":0}"))
                .andExpect(status().isConflict());
    }

    @Test void deletedSessionsLeaveNonReusableTombstonesInsteadOfUnsafeAliases() {
        var current=create(source); var saved=service.updateSession(current.id(),move(current,source.plusDays(1)),manager);
        jdbc.update("DELETE FROM class_session WHERE id=?",current.id());
        assertThat(sessions.findBySessionCode(current.sessionId())).isEmpty();
        assertThat(sessions.findBySessionCode(saved.sessionId())).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM session_code_history WHERE original_session_id=? AND session_id IS NULL",current.id())).isEqualTo(2);
        assertThat(create(source).sessionId()).endsWith("SW02");
    }
}
