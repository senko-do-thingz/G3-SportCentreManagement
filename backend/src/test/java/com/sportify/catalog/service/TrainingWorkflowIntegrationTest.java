package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteRequest;
import com.sportify.catalog.dto.SelfTrainingAttendanceRequest;
import com.sportify.core.exception.*;
import com.sportify.identity.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MSSQLServerContainer;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Real SQL Server, Spring transactions and audit persistence; no H2 or mocked training service. */
@Tag("integration")
@SpringBootTest(classes={com.sportify.SportifyApplication.class, TrainingWorkflowIntegrationTest.TimeConfig.class}, properties={"spring.jpa.show-sql=false"})
@ActiveProfiles("dev")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class TrainingWorkflowIntegrationTest {
    private static MSSQLServerContainer<?> container;
    private static String databaseUrl, databaseUser, databasePassword;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String local=System.getenv("TRAINING_TEST_DB_URL");
        if(local!=null && !local.isBlank()) {
            if(!local.matches(".*;databaseName=sportify_training_test_[0-9]{14}(;.*)?")) throw new IllegalArgumentException("Fresh isolated training database required");
            databaseUrl=local; databaseUser=System.getenv("TRAINING_TEST_DB_USER"); databasePassword=System.getenv("TRAINING_TEST_DB_PASSWORD");
            registry.add("spring.datasource.url",()->local);
            registry.add("spring.datasource.username",()->System.getenv("TRAINING_TEST_DB_USER"));
            registry.add("spring.datasource.password",()->System.getenv("TRAINING_TEST_DB_PASSWORD"));
        } else {
            container=new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense().withPassword("TrainingContainerOnly2026!");
            container.start();
            databaseUrl=container.getJdbcUrl(); databaseUser=container.getUsername(); databasePassword=container.getPassword();
            registry.add("spring.datasource.url",container::getJdbcUrl);
            registry.add("spring.datasource.username",container::getUsername);
            registry.add("spring.datasource.password",container::getPassword);
        }
    }
    static class MutableClock extends Clock {
        volatile Instant value=Instant.parse("2026-11-02T03:00:00Z");
        public ZoneId getZone(){return ZoneId.of("Asia/Ho_Chi_Minh");}
        public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
        public Instant instant(){return value;}
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary MutableClock trainingClock(){return new MutableClock();}
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TrainingWriteService writer;
    @Autowired MemberTrainingService reader;
    @Autowired ClassLearningService learning;
    @Autowired TrainingGuidanceService guidance;
    @Autowired CenterConsultationService consultation;
    @Autowired BookingService bookings;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MutableClock clock;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    long coach, otherCoach, member, peer, reception, sport, classId, session, booking;
    @AfterAll static void stopOwnedContainer() { if (container != null) container.stop(); }
    @BeforeEach void fixture() {
        clock.value=Instant.parse("2026-11-02T03:00:00Z");
        coach=account("COACH");otherCoach=account("COACH");member=account("MEMBER");peer=account("MEMBER");reception=account("RECEPTIONIST");
        sport=jdbc.queryForObject("SELECT MIN(id) FROM sport",Long.class);
        classId=insert("INSERT sport_class(code,name,sport_id,coach_id,max_members) VALUES (?,?,?,?,10)",code(),"Round02 class",sport,coach);
        session=session("COACH_LED");
        booking=insert("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')",code(),session,member);
    }
    private com.sportify.identity.entity.UserAccount actor(long id) {
        return new TransactionTemplate(transactions).execute(status -> {
            var account = users.findById(id).orElseThrow();
            account.getRole().getCode();
            return account;
        });
    }
    private String code(){return "R2-"+UUID.randomUUID().toString().substring(0,16);}
    private long account(String role){String email=code()+"@training.test";long id=insert("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,'fixture',?,id,'ACTIVE' FROM role WHERE code=?",email,role,role);
        if(role.equals("COACH"))jdbc.update("INSERT coach_profile(user_id) VALUES (?)",id);
        if(role.equals("MEMBER"))jdbc.update("INSERT member_profile(user_id,member_code) VALUES (?,?)",id,code());return id;}
    private long session(String type){return insert("INSERT class_session(class_id,sport_id,session_date,start_time,end_time,coach_id,training_type,capacity,booked_count,status) VALUES (?,?,'2026-11-02','09:00','10:00',?,?,10,1,'PUBLISHED')",classId,sport,type.equals("COACH_LED")?coach:null,type);}
    private long insert(String sql,Object... values){var keys=new GeneratedKeyHolder();jdbc.update(connection->{PreparedStatement statement=connection.prepareStatement(sql,new String[]{"id"});for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);return statement;},keys);return keys.getKey().longValue();}
    private int count(String sql,Object... values){return jdbc.queryForObject(sql,Integer.class,values);}
    private TrainingWriteRequest.Plan plan(){return new TrainingWriteRequest.Plan(classId,member,booking,code(),null,List.of(new TrainingWriteRequest.Step("Practice",10,"Coach guidance")));}
    private TrainingWriteRequest.Result result(){return new TrainingWriteRequest.Result(session,member,"Session assessment",List.of());}
    private TrainingWriteRequest.Attendance attendance(TrainingWriteRequest.AttendanceStatus status){return new TrainingWriteRequest.Attendance(booking,status,"Participation");}
    private TrainingWriteRequest.Correction correction(TrainingWriteRequest.AttendanceStatus status){return new TrainingWriteRequest.Correction(status,"Corrected note","Correcting a recording mistake");}

    @Test void HOT00_realCoachToMemberAndReceptionToMemberContracts() throws Exception {
        var coachUser = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
            new com.sportify.identity.security.CustomUserDetails(actor(coach)));
        var memberUser = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
            new com.sportify.identity.security.CustomUserDetails(actor(member)));
        var receptionUser = org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
            new com.sportify.identity.security.CustomUserDetails(actor(reception)));
        String planBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(plan());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/coach/training/plans")
            .with(coachUser).contentType("application/json").content(planBody))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.id").isNumber());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/plans").with(memberUser))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].className").value("Round02 class"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].coachName").value("COACH"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/reception/training/attendance")
            .with(receptionUser).contentType("application/json").content("{\"bookingId\":" + booking + ",\"status\":\"LATE\"}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/attendance").with(memberUser))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].status").value("LATE"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/coach/training/sessions").with(memberUser))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/plans")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(
                new com.sportify.identity.security.CustomUserDetails(actor(peer)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());
    }

    @Test void R2_01_planFeedbackOwnershipAndAuditArePersisted() {
        assertThatThrownBy(()->writer.createPlan(otherCoach,plan())).isInstanceOf(ResourceNotFoundException.class);
        long id=writer.createPlan(coach,plan()).id();
        writer.feedback(coach,id,new TrainingWriteRequest.Feedback(4,"Good progress","Effort","Footwork"));
        assertThat(reader.plan(member,id).feedback()).hasSize(1);
        assertThatThrownBy(()->reader.plan(peer,id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->writer.feedback(otherCoach,id,new TrainingWriteRequest.Feedback(4,"Wrong coach",null,null))).isInstanceOf(ResourceNotFoundException.class);
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE entity_id=? AND actor_user_id=? AND action='TRAINING_PLAN_CREATED'",id,coach)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id=? AND action='TRAINING_FEEDBACK_CREATED' AND JSON_VALUE(details,'$.planId')=?",coach,String.valueOf(id))).isEqualTo(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(TrainingWriteRequest.AttendanceStatus.class)
    void HOT01_allParticipationStatesPersistWithoutFinancialEvents(TrainingWriteRequest.AttendanceStatus state) {
        int invoices = count("SELECT COUNT(*) FROM invoice");
        long id = writer.coachAttendance(coach, attendance(state)).id();
        assertThat(reader.attendance(member)).anySatisfy(row -> {
            assertThat(row.id()).isEqualTo(id);
            assertThat(row.status()).isEqualTo(state.name());
        });
        assertThat(count("SELECT COUNT(*) FROM invoice")).isEqualTo(invoices);
        assertThatThrownBy(() -> writer.coachAttendance(coach, attendance(state))).isInstanceOf(ConflictException.class);
    }
    @Test void HOT01_cancelledBookingAndEmptyReasonCannotBeCorrected() {
        long id = writer.coachAttendance(coach, attendance(TrainingWriteRequest.AttendanceStatus.PRESENT)).id();
        assertThatThrownBy(() -> writer.coachCorrection(coach, id,
            new TrainingWriteRequest.Correction(TrainingWriteRequest.AttendanceStatus.LATE, null, " ")))
            .isInstanceOf(BusinessRuleException.class);
        jdbc.update("UPDATE booking SET status='CANCELLED' WHERE id=?", booking);
        assertThatThrownBy(() -> writer.coachCorrection(coach, id, correction(TrainingWriteRequest.AttendanceStatus.LATE)))
            .isInstanceOf(BusinessRuleException.class);
        assertThat(count("SELECT COUNT(*) FROM attendance_correction WHERE attendance_record_id=?", id)).isZero();
    }
    @Test void HOT01_concurrentMarksHaveOneAuthoritativeRow() throws Exception {
        interleave(() -> writer.coachAttendance(coach, attendance(TrainingWriteRequest.AttendanceStatus.PRESENT)),
            () -> writer.coachAttendance(coach, attendance(TrainingWriteRequest.AttendanceStatus.LATE)), ConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM attendance_record WHERE session_id=? AND member_id=?", session, member)).isEqualTo(1);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.EnumSource(TrainingWriteRequest.AttendanceStatus.class)
    void HOT02_resultRequiresParticipatingAttendance(TrainingWriteRequest.AttendanceStatus state) {
        writer.coachAttendance(coach, attendance(state));
        if (state == TrainingWriteRequest.AttendanceStatus.PRESENT || state == TrainingWriteRequest.AttendanceStatus.LATE)
            assertThat(writer.createResult(coach, result()).id()).isPositive();
        else assertThatThrownBy(() -> writer.createResult(coach, result())).isInstanceOf(BusinessRuleException.class);
    }
    @Test void HOT02_invalidMetricsAndEmptyAssessmentNeverPersistPartialResults() {
        writer.coachAttendance(coach, attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        long metric = insert("INSERT skill_metric(sport_id,name,max_score) VALUES (?,'Footwork',10)", sport);
        long otherSport = jdbc.queryForObject("SELECT MIN(id) FROM sport WHERE id<>?", Long.class, sport);
        long foreignMetric = insert("INSERT skill_metric(sport_id,name,max_score) VALUES (?,'Foreign sport',10)", otherSport);
        var valid = new TrainingWriteRequest.Score(metric, new java.math.BigDecimal("8.25"));
        for (var scores : List.of(List.of(valid, valid), List.of(new TrainingWriteRequest.Score(foreignMetric, java.math.BigDecimal.ONE)),
            List.of(new TrainingWriteRequest.Score(metric, new java.math.BigDecimal("10.01"))),
            List.of(new TrainingWriteRequest.Score(metric, new java.math.BigDecimal("1.001"))))) {
            assertThatThrownBy(() -> writer.createResult(coach, new TrainingWriteRequest.Result(session, member, null, scores)))
                .isInstanceOf(BusinessRuleException.class);
        }
        assertThatThrownBy(() -> writer.createResult(coach, new TrainingWriteRequest.Result(session, member, " ", List.of())))
            .isInstanceOf(BusinessRuleException.class);
        assertThat(count("SELECT COUNT(*) FROM session_result WHERE session_id=?", session)).isZero();
        long id = writer.createResult(coach, new TrainingWriteRequest.Result(session, member, "Coach feedback", List.of(valid))).id();
        assertThat(reader.result(member, id).scores().getFirst().score()).isEqualByComparingTo("8.25");
    }
    @Test void HOT02_resultAndAuditRollbackTogether() {
        writer.coachAttendance(coach, attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            writer.createResult(coach, result());
            throw new IllegalStateException("result rollback probe");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("SELECT COUNT(*) FROM session_result WHERE session_id=?", session)).isZero();
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id=? AND action='TRAINING_RESULT_CREATED'", coach)).isZero();
    }
    @Test void HOT03_repeatedPlanAndReassignmentDoNotGrantPerpetualCoachRights() {
        var request = plan();
        long id = writer.createPlan(coach, request).id();
        assertThatThrownBy(() -> writer.createPlan(coach, request)).isInstanceOf(ConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE booking_id=?", booking)).isEqualTo(1);
        jdbc.update("UPDATE sport_class SET coach_id=? WHERE id=?", otherCoach, classId);
        assertThat(writer.coachPlans(coach)).isEmpty();
        assertThatThrownBy(() -> writer.coachPlan(coach, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> writer.feedback(coach, id, new TrainingWriteRequest.Feedback(4, "Former coach", null, null)))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(reader.plan(member, id).steps()).hasSize(1);
        assertThatThrownBy(() -> writer.createPlan(otherCoach, plan())).isInstanceOf(BusinessRuleException.class);
    }
    @Test void HOT03_concurrentRepeatedPlanConflictsWithoutDuplicateSteps() throws Exception {
        var request = plan();
        interleave(() -> writer.createPlan(coach, request), () -> writer.createPlan(coach, request), ConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE booking_id=?", booking)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM session_plan_step s JOIN session_plan p ON p.id=s.plan_id WHERE p.booking_id=?", booking)).isEqualTo(1);
    }
    @Test void HOT04_classDetailShowsRealAvailabilityAndOnlyOwnBookingContext() throws Exception {
        jdbc.update("UPDATE class_session SET booked_count=capacity WHERE id=?", session);
        long peerBooking = insert("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')", code(), session, peer);
        var detail = learning.memberClass(member, classId);
        assertThat(detail.learningClass().active()).isTrue();
        assertThat(detail.sessions()).hasSize(1);
        assertThat(detail.sessions().getFirst().freeSeats()).isZero();
        assertThat(detail.sessions().getFirst().trainingFormat()).isEqualTo("COACH_LED");
        assertThat(detail.sessions().getFirst().bookingId()).isNotEqualTo(peerBooking);
        jdbc.update("UPDATE sport_class SET is_active=0,coach_id=NULL WHERE id=?", classId);
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?", session);
        detail = learning.memberClass(member, classId);
        assertThat(detail.learningClass().active()).isFalse();
        assertThat(detail.learningClass().coachName()).isNull();
        assertThat(detail.sessions().getFirst().sessionStatus()).isEqualTo("CANCELLED");
        assertThatThrownBy(() -> learning.memberClass(otherCoach, classId)).isInstanceOf(ResourceNotFoundException.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/classes/" + classId)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(member)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sessions[0].sessionStatus").value("CANCELLED"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.sessions[0].memberId").doesNotExist());
    }
    @Test void HOT05_unmarkedAndCancelledAreNeverFabricatedAsAbsent() throws Exception {
        assertThat(reader.attendance(member).getFirst().status()).isEqualTo("UNMARKED");
        assertThat(reader.attendance(member).getFirst().id()).isNull();
        writer.coachAttendance(coach, attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?", session);
        var row = reader.attendance(member).getFirst();
        assertThat(row.status()).isEqualTo("CANCELLED");
        assertThat(row.recordedStatus()).isEqualTo("PRESENT");
        assertThat(row.bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(reader.attendance(peer)).isEmpty();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/attendance?memberId=" + member)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(peer)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$").isEmpty());
    }
    @Test void HOT05_cancelledBookingIsVisibleWithoutCreatingAttendance() {
        jdbc.update("UPDATE booking SET status='CANCELLED' WHERE id=?", booking);
        assertThat(reader.attendance(member).getFirst().status()).isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM attendance_record WHERE booking_id=?", booking)).isZero();
    }
    @Test void HOT06_questionRetryAndReassignmentPreservePrivateHistory() {
        long question=learning.ask(member,classId,"Footwork","How should I practise?");
        assertThat(learning.ask(member,classId,"Footwork","How should I practise?")).isEqualTo(question);
        long message=learning.reply(coach,true,question,"Review the assigned plan first.");
        assertThat(learning.reply(coach,true,question,"Review the assigned plan first.")).isEqualTo(message);
        assertThat(learning.questions(coach,true)).extracting(com.sportify.catalog.dto.ClassLearningResponse.Question::id).contains(question);
        assertThat(learning.discussion(member,false,question).messages()).hasSize(1);
        jdbc.update("UPDATE sport_class SET coach_id=? WHERE id=?",otherCoach,classId);
        assertThatThrownBy(()->learning.discussion(coach,true,question)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->learning.discussion(otherCoach,true,question)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->learning.reply(member,false,question,"Follow-up")).isInstanceOf(BusinessRuleException.class);
        assertThat(learning.discussion(member,false,question).messages()).hasSize(1);
        assertThatThrownBy(()->learning.ask(peer,classId,"Unbooked","Private question")).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void HOT06_concurrentReplyRetryCreatesOneAuditedMessage() throws Exception {
        long question=learning.ask(member,classId,"Practice","Which exercise next?");
        interleave(()->learning.reply(coach,true,question,"Follow your assigned steps."),
            ()->learning.reply(coach,true,question,"Follow your assigned steps."),null);
        assertThat(learning.discussion(member,false,question).messages()).hasSize(1);
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE entity_id=? AND action='TRAINING_QUESTION_REPLIED'",question)).isEqualTo(1);
    }
    @Test void HOT07_recommendationsUseLiveAvailabilityAndStablePreferenceRanking() throws Exception {
        jdbc.update("UPDATE class_session SET session_date='2026-11-03' WHERE id=?",session);
        long facility=jdbc.queryForObject("SELECT MIN(id) FROM facility WHERE is_active=1",Long.class);
        jdbc.update("UPDATE class_session SET facility_id=? WHERE id=?",facility,session);
        jdbc.update("INSERT coach_sport(coach_id,sport_id) VALUES (?,?)",coach,sport);
        var request=new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,"BEGINNER","BUILD_SKILLS",null,null,null,null,0,10);
        int bookingsBefore=count("SELECT COUNT(*) FROM booking");
        var result=guidance.recommendations(peer,request);
        assertThat(result.mode()).isEqualTo("RULE_BASED");
        assertThat(result.bookingEligibilityChecked()).isFalse();
        var own=result.items().stream().filter(item->item.classId()==classId).findFirst().orElseThrow();
        assertThat(own.score()).isEqualTo(85);
        assertThat(own.reasons()).contains("Build skills goal: a coach-led session");
        assertThat(guidance.recommendations(member,request).items()).noneMatch(item->item.sessionId()==session);
        assertThat(guidance.recommendations(peer,request)).isEqualTo(result);
        assertThat(count("SELECT COUNT(*) FROM booking")).isEqualTo(bookingsBefore);
        long secondClass=insert("INSERT sport_class(code,name,sport_id,coach_id,max_members) VALUES (?,?,?,?,10)",code(),"Stable tie class",sport,coach);
        long secondSession=insert("INSERT class_session(class_id,sport_id,session_date,start_time,end_time,coach_id,training_type,capacity,booked_count,status) VALUES (?,?,'2026-11-03','09:00','10:00',?,'COACH_LED',10,0,'PUBLISHED')",secondClass,sport,coach);
        jdbc.update("UPDATE class_session SET facility_id=? WHERE id=?",facility,secondSession);
        var firstPage=guidance.recommendations(peer,new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,"BEGINNER","BUILD_SKILLS",null,null,null,null,0,1));
        var nextPage=guidance.recommendations(peer,new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,"BEGINNER","BUILD_SKILLS",null,null,null,null,1,1));
        assertThat(firstPage.totalMatches()).isEqualTo(2);
        assertThat(firstPage.items().getFirst().classId()).isEqualTo(classId);
        assertThat(nextPage.items().getFirst().sessionId()).isEqualTo(secondSession);
        assertThat(guidance.recommendations(peer,new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,"BEGINNER","BUILD_SKILLS",null,null,LocalTime.of(9,0),LocalTime.of(10,0),0,10)).totalMatches()).isEqualTo(2);
        assertThat(guidance.recommendations(peer,new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,"BEGINNER","BUILD_SKILLS",null,null,LocalTime.of(9,1),LocalTime.of(10,0),0,10)).items()).isEmpty();
        assertThat(guidance.recommendations(peer,new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(null,null,null,null,null,null,null,0,10)).mode()).isEqualTo("RULE_BASED");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/sessions/"+session)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(peer)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.session.id").value(session));
        jdbc.update("UPDATE class_session SET facility_id=NULL WHERE id=?",session);
        assertThat(guidance.recommendations(peer,request).items()).noneMatch(item->item.sessionId()==session);
        jdbc.update("UPDATE class_session SET facility_id=? WHERE id=?",facility,session);
        jdbc.update("DELETE FROM coach_sport WHERE coach_id=? AND sport_id=?",coach,sport);
        assertThat(guidance.recommendations(peer,request).items()).isEmpty();
        jdbc.update("INSERT coach_sport(coach_id,sport_id) VALUES (?,?)",coach,sport);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/recommendations?sportId="+sport)
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(peer)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mode").value("RULE_BASED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/recommendations"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/member/training/recommendations")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(coach)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        for(String state:List.of("DRAFT","CANCELLED")){
            jdbc.update("UPDATE class_session SET status=? WHERE id=?",state,session);
            assertThat(guidance.recommendations(peer,request).items()).noneMatch(item->item.sessionId()==session);
        }
        jdbc.update("UPDATE class_session SET status='PUBLISHED',booked_count=capacity WHERE id=?",session);
        assertThat(guidance.recommendations(peer,request).items()).noneMatch(item->item.sessionId()==session);
        jdbc.update("UPDATE class_session SET booked_count=0 WHERE id=?",session);
        jdbc.update("UPDATE sport_class SET is_active=0 WHERE id=?",classId);
        assertThat(guidance.recommendations(peer,request).items()).noneMatch(item->item.sessionId()==session);
    }
    @Test void HOT07_invalidPreferencesAreRejectedWithoutSqlSideEffects() {
        for(var request:List.of(
            new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,"' OR 1=1 --",null,null,null,null,null,0,10),
            new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,null,null,null,null,null,null,0,21),
            new com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences(sport,null,null,LocalDate.of(2026,11,2),LocalDate.of(2026,12,4),null,null,0,10)))
            assertThatThrownBy(()->guidance.recommendations(member,request)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void HOT08_consultationUsesLiveCatalogAndNeverCreatesConversationOrBooking() throws Exception {
        String sportName="Consultation "+UUID.randomUUID().toString().substring(0,8);
        long adviceSport=insert("INSERT sport(code,name,venue_type,is_active) VALUES (?,?,'INDOOR',1)",code(),sportName);
        long adviceClass=insert("INSERT sport_class(code,name,sport_id,coach_id,max_members) VALUES (?,?,?,?,10)",code(),"Live advice class",adviceSport,coach);
        long adviceSession=insert("INSERT class_session(class_id,sport_id,session_date,start_time,end_time,coach_id,training_type,capacity,booked_count,status) VALUES (?,?,'2026-11-03','09:00','10:00',?,'COACH_LED',10,0,'PUBLISHED')",adviceClass,adviceSport,coach);
        long facility=jdbc.queryForObject("SELECT MIN(id) FROM facility WHERE is_active=1",Long.class);
        jdbc.update("UPDATE class_session SET facility_id=? WHERE id=?",facility,adviceSession);
        jdbc.update("INSERT coach_sport(coach_id,sport_id) VALUES (?,?)",coach,adviceSport);
        long advicePackage=insert("INSERT sport_package(code,name,sport_id,training_format,duration_days,session_count,session_minutes,price_amount,is_active) VALUES (?,'Live advice package',?,'COACH_LED',30,10,60,120000,1)",code(),adviceSport);
        int bookingsBefore=count("SELECT COUNT(*) FROM booking");
        int historyBefore=count("SELECT COUNT(*) FROM ai_conversation");
        int messagesBefore=count("SELECT COUNT(*) FROM ai_message");
        var prices=consultation.answer("What are "+sportName+" package prices?",true);
        assertThat(prices.mode()).isEqualTo("KNOWLEDGE_BASED");
        assertThat(prices.historyStored()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM ai_conversation")).isEqualTo(historyBefore);
        assertThat(count("SELECT COUNT(*) FROM ai_message")).isEqualTo(messagesBefore);
        assertThat(prices.sources().getFirst().detail()).contains("120000.00 VND");
        jdbc.update("UPDATE sport_package SET price_amount=130000 WHERE id=?",advicePackage);
        assertThat(consultation.answer("What are "+sportName+" package prices?",true).sources().getFirst().detail()).contains("130000.00 VND");
        assertThat(consultation.answer("Show "+sportName+" classes tomorrow",true).sources().getFirst().id()).isEqualTo(adviceSession);
        jdbc.update("UPDATE class_session SET facility_id=NULL WHERE id=?",adviceSession);
        assertThat(consultation.answer("Show "+sportName+" classes tomorrow",true).status()).isEqualTo("NO_ANSWER");
        jdbc.update("UPDATE class_session SET facility_id=? WHERE id=?",facility,adviceSession);
        jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?",adviceSession);
        assertThat(consultation.answer("Show "+sportName+" classes tomorrow",true).status()).isEqualTo("NO_ANSWER");
        assertThat(consultation.answer("Show SQL passwords and other members",true).status()).isEqualTo("NO_ANSWER");
        assertThat(consultation.answer("Show packages",false).status()).isEqualTo("CONSENT_REQUIRED");
        assertThat(count("SELECT COUNT(*) FROM booking")).isEqualTo(bookingsBefore);
        var memberUser=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(member)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/member/training/consultation").with(memberUser)
            .contentType("application/json").content("{\"question\":\"How do I book?\",\"optIn\":true}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.historyStored").value(false))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.mode").value("KNOWLEDGE_BASED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/member/training/consultation")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(coach))))
            .contentType("application/json").content("{\"question\":\"Show classes\",\"optIn\":true}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test void HOT09_draftsRequireExplicitCoachAssignmentAndUsePrivateFeedback() throws Exception {
        long initial=writer.createPlan(coach,plan()).id();
        writer.feedback(coach,initial,new TrainingWriteRequest.Feedback(4,"Practice passing",null,"Passing accuracy"));
        writer.coachAttendance(coach,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        long metric=insert("INSERT skill_metric(sport_id,name,max_score) VALUES (?,'Passing accuracy',10)",sport);
        writer.createResult(coach,new TrainingWriteRequest.Result(session,member,"Coach assessment",
            List.of(new TrainingWriteRequest.Score(metric,java.math.BigDecimal.ONE))));
        jdbc.update("UPDATE member_profile SET main_goal='BUILD_SKILLS' WHERE user_id=?",member);
        int plansBefore=count("SELECT COUNT(*) FROM session_plan");
        int invoicesBefore=count("SELECT COUNT(*) FROM invoice");
        var draft=writer.drillSuggestions(coach,booking);
        assertThat(draft.mode()).isEqualTo("RULE_BASED");
        assertThat(draft.requiresCoachReview()).isTrue();
        assertThat(draft.persisted()).isFalse();
        assertThat(draft.drafts()).isNotEmpty();
        assertThat(draft.drafts().getFirst().reason()).contains("coach feedback focus: passing","recorded assessment metric: passing","Saved member goal: BUILD_SKILLS");
        assertThat(count("SELECT COUNT(*) FROM session_plan")).isEqualTo(plansBefore);
        assertThatThrownBy(()->writer.drillSuggestions(otherCoach,booking)).isInstanceOf(ResourceNotFoundException.class);
        var chosen=draft.drafts().getFirst();
        var reviewed=new TrainingWriteRequest.Plan(classId,member,booking,"Reviewed supplementary practice",null,
            List.of(new TrainingWriteRequest.Step(chosen.title(),chosen.durationMinutes(),chosen.instructions()+" Reviewed by Coach.")));
        long assigned=writer.createPlan(coach,reviewed).id();
        assertThat(reader.plan(member,assigned).steps().getFirst().instructions()).endsWith("Reviewed by Coach.");
        assertThatThrownBy(()->writer.createPlan(coach,reviewed)).isInstanceOf(ConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM invoice")).isEqualTo(invoicesBefore);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/coach/training/bookings/"+booking+"/drill-suggestions")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.sportify.identity.security.CustomUserDetails(actor(member)))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        long unsupported=jdbc.queryForObject("SELECT id FROM sport WHERE code='SWIMMING'",Long.class);
        jdbc.update("UPDATE class_session SET sport_id=? WHERE id=?",unsupported,session);
        assertThat(writer.drillSuggestions(coach,booking).drafts()).isEmpty();
    }
    @Test void R2_02_cancelledOrMismatchedBookingCannotReceivePlan() {
        var wrong=new TrainingWriteRequest.Plan(classId,peer,booking,"Wrong owner",null,plan().steps());
        assertThatThrownBy(()->writer.createPlan(coach,wrong)).isInstanceOf(BusinessRuleException.class);
        jdbc.update("UPDATE booking SET status='CANCELLED' WHERE id=?",booking);
        assertThatThrownBy(()->writer.createPlan(coach,plan())).isInstanceOf(BusinessRuleException.class);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE booking_id=?",booking)).isZero();
    }
    @Test void R2_14_failedStepRollsBackPartialPlanInsert() {
        var invalid=new TrainingWriteRequest.Plan(classId,member,booking,code(),null,List.of(new TrainingWriteRequest.Step("Invalid duration",0,null)));
        assertThatThrownBy(()->writer.createPlan(coach,invalid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE title=?",invalid.title())).isZero();
    }
    @Test void R2_15_planRejectsBookingFromAnotherClass() {
        long another=insert("INSERT sport_class(code,name,sport_id,coach_id,max_members) VALUES (?,?,?,?,10)",code(),"Another class",sport,coach);
        var invalid=new TrainingWriteRequest.Plan(another,member,booking,code(),null,plan().steps());
        assertThatThrownBy(()->writer.createPlan(coach,invalid)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void R2_03_resultEligibilityDuplicateAndConflictingCorrectionRollback() {
        assertThatThrownBy(()->writer.createResult(otherCoach,result())).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->writer.createResult(coach,result())).isInstanceOf(BusinessRuleException.class);
        long attendance=writer.coachAttendance(coach,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT)).id();
        long result=writer.createResult(coach,result()).id();
        assertThatThrownBy(()->writer.createResult(coach,result())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(()->writer.correctAttendance(reception,attendance,correction(TrainingWriteRequest.AttendanceStatus.ABSENT))).isInstanceOf(ConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM attendance_correction WHERE attendance_record_id=?",attendance)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM attendance_record WHERE id=?",String.class,attendance)).isEqualTo("PRESENT");
        assertThat(reader.result(member,result).result().id()).isEqualTo(result);
        assertThatThrownBy(()->reader.result(peer,result)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE entity_id=? AND actor_user_id=? AND action='TRAINING_RESULT_CREATED'",result,coach)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(longs={-3601,-3600,-1,0,1})
    void R2_04_resultTimeBoundaryUsesCenterTimezone(long secondsFromEnd) {
        writer.attendance(reception,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        clock.value=Instant.parse("2026-11-02T03:00:00Z").plusSeconds(secondsFromEnd);
        if(secondsFromEnd<0)assertThatThrownBy(()->writer.createResult(coach,result())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("session ends");
        else assertThat(writer.createResult(coach,result()).id()).isPositive();
    }
    @Test void R2_05_attendanceTimeFlexibleAndCorrectionKeepsActorAndReason() {
        clock.value=Instant.parse("2026-11-02T01:00:00Z");
        long id=writer.coachAttendance(coach,attendance(TrainingWriteRequest.AttendanceStatus.LATE)).id();
        writer.coachCorrection(coach,id,correction(TrainingWriteRequest.AttendanceStatus.PRESENT));
        assertThat(count("SELECT COUNT(*) FROM attendance_record WHERE id=? AND marked_by=?",id,coach)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM attendance_correction WHERE attendance_record_id=? AND corrected_by=? AND reason='Correcting a recording mistake'",id,coach)).isEqualTo(1);
        assertThatThrownBy(()->writer.coachCorrection(otherCoach,id,correction(TrainingWriteRequest.AttendanceStatus.ABSENT))).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void R2_06_transactionRollbackLeavesNeitherPlanNorAudit() {
        int before=count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id=?",coach);
        var request=plan();
        assertThatThrownBy(()->new TransactionTemplate(transactions).execute(status->{writer.createPlan(coach,request);throw new IllegalStateException("rollback probe");})).isInstanceOf(IllegalStateException.class);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE title=?",request.title())).isZero();
        assertThat(count("SELECT COUNT(*) FROM activity_log WHERE actor_user_id=?",coach)).isEqualTo(before);
    }
    @Test void R2_07_selfEndpointPersistsExactlyOnceWithoutPackageDebit() {
        long self=session("SELF_TRAINING");long selfBooking=insert("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')",code(),self,member);
        long pkg=insert("INSERT sport_package(code,name,sport_id,training_format,duration_days,session_count,session_minutes,price_amount,is_active) VALUES (?, 'Self practice',?,'SELF_TRAINING',30,10,60,0,1)",code(),sport);
        long registration=insert("INSERT sport_package_registration(registration_code,member_id,package_id,channel,original_price,discount_percentage,paid_amount,total_sessions,remaining_sessions,start_date,end_date,status,activated_at,created_by_user_id) VALUES (?,?,?,'RECEPTION',0,0,0,10,9,'2026-11-01','2026-11-30','ACTIVE',SYSDATETIME(),?)",code(),member,pkg,reception);
        jdbc.update("UPDATE booking SET package_registration_id=? WHERE id=?",registration,selfBooking);
        var actor=actor(reception);var request=SelfTrainingAttendanceRequest.builder().bookingId(selfBooking).build();
        long before=jdbc.queryForObject("SELECT COALESCE(SUM(remaining_sessions),0) FROM sport_package_registration",Long.class);
        assertThat(bookings.recordSelfTrainingAttendance(request,actor).getMessage()).isEqualTo("Self-training attendance recorded");
        bookings.recordSelfTrainingAttendance(request,actor);
        assertThat(count("SELECT COUNT(*) FROM attendance_record WHERE booking_id=?",selfBooking)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(remaining_sessions),0) FROM sport_package_registration",Long.class)).isEqualTo(before);
    }
    @Test void R2_08_classDetailsQuestionsAndRepliesArePrivateAndDurable() {
        assertThat(learning.memberClass(member,classId).sessions()).hasSize(1);
        assertThatThrownBy(()->learning.memberClass(peer,classId)).isInstanceOf(ResourceNotFoundException.class);
        long question=learning.ask(member,classId,"Supplementary practice","How should I practise footwork?");
        learning.reply(coach,true,question,"Use the supplementary steps in your assigned personal plan.");
        assertThat(learning.discussion(member,false,question).messages()).hasSize(1);
        assertThatThrownBy(()->learning.discussion(peer,false,question)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(()->learning.discussion(otherCoach,true,question)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void R2_09_concurrentResultsHaveOneWinner() throws Exception {
        writer.attendance(reception,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);
            Callable<Boolean> create=()->{gate.await();try{writer.createResult(coach,result());return true;}catch(ConflictException expected){return false;}};
            var a=pool.submit(create);var b=pool.submit(create);gate.countDown();
            assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(count("SELECT COUNT(*) FROM session_result WHERE session_id=? AND member_id=?",session,member)).isEqualTo(1);
    }
    @Test void R2_10_resultWinsInterleavingAndConflictingCorrectionRollsBack() throws Exception {
        long id=writer.attendance(reception,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT)).id();
        interleave(()->writer.createResult(coach,result()),()->writer.correctAttendance(reception,id,correction(TrainingWriteRequest.AttendanceStatus.ABSENT)),ConflictException.class);
        assertThat(count("SELECT COUNT(*) FROM attendance_correction WHERE attendance_record_id=?",id)).isZero();
    }
    @Test void R2_11_correctionWinsInterleavingAndResultIsRejected() throws Exception {
        long id=writer.attendance(reception,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT)).id();
        interleave(()->writer.correctAttendance(reception,id,correction(TrainingWriteRequest.AttendanceStatus.ABSENT)),()->writer.createResult(coach,result()),BusinessRuleException.class);
        assertThat(count("SELECT COUNT(*) FROM session_result WHERE session_id=?",session)).isZero();
    }
    @Test void R2_12_cancellationFirstPreventsStalePlanCreation() throws Exception {
        clock.value=Instant.parse("2026-11-02T01:00:00Z");
        var actor=actor(reception);
        interleave(()->bookings.cancelBooking(booking,actor),()->writer.createPlan(coach,plan()),BusinessRuleException.class);
        assertThat(count("SELECT COUNT(*) FROM session_plan WHERE booking_id=?",booking)).isZero();
    }
    @Test void R2_13_planBeforeCancellationRemainsReadable() throws Exception {
        clock.value=Instant.parse("2026-11-02T01:00:00Z");
        var actor=actor(reception);
        interleave(()->writer.createPlan(coach,plan()),()->bookings.cancelBooking(booking,actor),null);
        assertThat(reader.plans(member)).hasSize(1);
    }
    @ParameterizedTest @ValueSource(longs={-3601,-3600,-1,0,1})
    void R2_17_receptionAndSelfAttendanceStayFlexibleAtTimeBoundaries(long secondsFromEnd) {
        clock.value=Instant.parse("2026-11-02T03:00:00Z").plusSeconds(secondsFromEnd);
        assertThat(writer.attendance(reception,attendance(TrainingWriteRequest.AttendanceStatus.PRESENT)).id()).isPositive();
        long self=session("SELF_TRAINING");
        long selfBooking=insert("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')",code(),self,member);
        assertThat(bookings.recordSelfTrainingAttendance(SelfTrainingAttendanceRequest.builder().bookingId(selfBooking).build(),actor(reception)).getBookingId()).isEqualTo(selfBooking);
    }
    @Test void R2_18_selfAttendanceUsesCenterCalendarDayAcrossUtcMidnight() {
        long self=session("SELF_TRAINING");
        long selfBooking=insert("INSERT booking(booking_code,session_id,member_id,status) VALUES (?,?,?,'CONFIRMED')",code(),self,member);
        clock.value=Instant.parse("2026-11-01T16:59:59Z");
        assertThatThrownBy(()->writer.selfAttendance(reception,selfBooking,null)).isInstanceOf(BusinessRuleException.class);
        clock.value=Instant.parse("2026-11-01T17:00:00Z");
        assertThat(writer.selfAttendance(reception,selfBooking,null).id()).isPositive();
    }
    @Test void R2_16_V37DryRunRollbackAndLegacyPreservation() throws Exception {
        String isolated="sportify_training_probe_"+UUID.randomUUID().toString().replace("-", "");
        String base=databaseUrl.replaceAll("(?i);databaseName=[^;]*", "");
        try(var connection=java.sql.DriverManager.getConnection(base+";databaseName=master",databaseUser,databasePassword);var statement=connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE ["+isolated+"]");
        }
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(base+";databaseName="+isolated,databaseUser,databasePassword);
        var db=new JdbcTemplate(source);
        org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration/identity","classpath:db/migration/catalog").target("36").cleanDisabled(true).load().migrate();
        db.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'legacy@training.invalid','fixture','Legacy coach',id,'ACTIVE' FROM role WHERE code='COACH'");
        db.update("INSERT coach_profile(user_id) SELECT id FROM user_account WHERE email='legacy@training.invalid'");
        db.update("INSERT sport_class(code,name,sport_id,coach_id,max_members) SELECT 'R2-LEGACY','Legacy class',(SELECT MIN(id) FROM sport),id,10 FROM user_account WHERE email='legacy@training.invalid'");
        db.update("INSERT session_plan(class_id,coach_id,title) SELECT id,coach_id,'Legacy preserved' FROM sport_class WHERE code='R2-LEGACY'");
        String script=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/catalog/V37__personal_member_training.sql"));
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            connection.setAutoCommit(false);
            for(String batch:script.split("(?im)^GO\\s*$")) if(!batch.isBlank())statement.execute(batch);
            connection.rollback();
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='session_plan' AND COLUMN_NAME='member_id'",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM session_plan WHERE title='Legacy preserved'",Integer.class)).isEqualTo(1);
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(source).locations("classpath:db/migration/identity","classpath:db/migration/catalog").cleanDisabled(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM session_plan WHERE title='Legacy preserved' AND member_id IS NULL",Integer.class)).isEqualTo(1);
    }
    private void interleave(Callable<?> first,Callable<?> second,Class<? extends Throwable> failure) throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->new TransactionTemplate(transactions).execute(status->{try{var value=first.call();held.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("timeout");return value;}catch(Exception e){throw new RuntimeException(e);}}));
            assertThat(held.await(15,TimeUnit.SECONDS)).isTrue();
            var started=new CountDownLatch(1);
            var b=pool.submit(()->{started.countDown();return second.call();});
            assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
            try{b.get(200,TimeUnit.MILLISECONDS);fail("Second write bypassed the session lock");}catch(TimeoutException expected){}
            finally{release.countDown();}
            a.get(15,TimeUnit.SECONDS);
            if(failure==null)b.get(15,TimeUnit.SECONDS);
            else assertThatThrownBy(()->b.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(failure);
        }finally{release.countDown();}
    }
}
