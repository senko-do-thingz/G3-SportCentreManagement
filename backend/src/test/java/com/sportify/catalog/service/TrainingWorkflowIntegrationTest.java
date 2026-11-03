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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, classes={com.sportify.SportifyApplication.class, TrainingWorkflowIntegrationTest.TimeConfig.class}, properties={"spring.jpa.show-sql=false"})
@ActiveProfiles({"dev", "demo"})
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
    @Autowired org.springframework.boot.test.web.client.TestRestTemplate http;
    @Autowired com.sportify.identity.security.JwtService jwt;
    private org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> request(
            long actorId, org.springframework.http.HttpMethod method, String path, Object body) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(jwt.generateToken(new com.sportify.identity.security.CustomUserDetails(actor(actorId))));
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return http.exchange("/api/v1" + path, method,
            new org.springframework.http.HttpEntity<>(body, headers), com.fasterxml.jackson.databind.JsonNode.class);
    }
    @Test void R3_01_coachToMemberUsesRealAuthenticatedHttpAndSql() {
        var created=request(coach, org.springframework.http.HttpMethod.POST, "/coach/training/plans", plan());
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long id=created.getBody().path("id").asLong();
        assertThat(id).isPositive();
        var feedback=request(coach, org.springframework.http.HttpMethod.POST, "/coach/training/plans/"+id+"/feedback",
            new TrainingWriteRequest.Feedback(4,"HTTP progress","Effort","Footwork"));
        assertThat(feedback.getStatusCode().value()).isEqualTo(201);
        var detail=request(member, org.springframework.http.HttpMethod.GET, "/member/training/plans/"+id, null);
        assertThat(detail.getStatusCode().value()).isEqualTo(200);
        assertThat(detail.getBody().path("plan").path("id").asLong()).isEqualTo(id);
        assertThat(detail.getBody().path("feedback").get(0).path("comments").asText()).isEqualTo("HTTP progress");
        assertThat(request(peer, org.springframework.http.HttpMethod.GET, "/member/training/plans/"+id,null)
            .getStatusCode().value()).isEqualTo(404);
        assertThat(request(member, org.springframework.http.HttpMethod.POST, "/coach/training/plans",plan())
            .getStatusCode().value()).isEqualTo(403);
    }
    @Test void R3_02_receptionToMemberAndCoachResultUseRealAuthenticatedHttpAndSql() {
        var marked=request(reception, org.springframework.http.HttpMethod.POST, "/reception/training/attendance",
            attendance(TrainingWriteRequest.AttendanceStatus.PRESENT));
        assertThat(marked.getStatusCode().value()).isEqualTo(201);
        long id=marked.getBody().path("id").asLong();
        var history=request(member, org.springframework.http.HttpMethod.GET, "/member/training/attendance",null);
        assertThat(history.getStatusCode().value()).isEqualTo(200);
        assertThat(history.getBody().get(0).path("id").asLong()).isEqualTo(id);
        assertThat(history.getBody().get(0).path("status").asText()).isEqualTo("PRESENT");
        var peerHistory=request(peer, org.springframework.http.HttpMethod.GET, "/member/training/attendance",null);
        assertThat(peerHistory.getStatusCode().value()).isEqualTo(200);
        assertThat(peerHistory.getBody().size()).isZero();
        var saved=request(coach, org.springframework.http.HttpMethod.POST, "/coach/training/results",result());
        assertThat(saved.getStatusCode().value()).isEqualTo(201);
        var results=request(member, org.springframework.http.HttpMethod.GET, "/member/training/results",null);
        assertThat(results.getStatusCode().value()).isEqualTo(200);
        assertThat(results.getBody().get(0).path("id").asLong()).isEqualTo(saved.getBody().path("id").asLong());
        assertThat(request(reception, org.springframework.http.HttpMethod.PUT, "/reception/training/attendance/"+id,
            correction(TrainingWriteRequest.AttendanceStatus.ABSENT)).getStatusCode().value()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT status FROM attendance_record WHERE id=?",String.class,id)).isEqualTo("PRESENT");
    }
    @Autowired org.flywaydb.core.Flyway flyway;
    @Test void R3_03_freshDemoStartupValidatesCombinedMigrationSequenceAndReplay() {
        flyway.validate();
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('37','38') AND success=1")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM user_account WHERE email IN ('nam.coach@example.com','ha.coach@example.com','quan.coach@example.com') AND avatar_url LIKE '/images/coaches/%.jpg'")).isEqualTo(3);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired TrainingWriteService writer;
    @Autowired MemberTrainingService reader;
    @Autowired ClassLearningService learning;
    @Autowired BookingService bookings;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MutableClock clock;
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
