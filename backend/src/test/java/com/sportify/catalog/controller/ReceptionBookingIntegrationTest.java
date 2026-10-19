package com.sportify.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.ReceptionBookingDatabase;
import com.sportify.catalog.dto.*;
import com.sportify.catalog.entity.*;
import com.sportify.catalog.service.*;
import com.sportify.identity.entity.*;
import com.sportify.identity.repository.UserRepository;
import com.sportify.identity.security.CustomUserDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real HTTP authorization, real audit writes and independent SQL Server booking transactions. */
@Tag("integration")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@ActiveProfiles(value = {"dev", "test"}, inheritProfiles = false)
@Import(ReceptionBookingIntegrationTest.TimeConfig.class)
@TestPropertySource(properties = {"spring.datasource.hikari.connection-init-sql=SET LOCK_TIMEOUT 5000",
        "spring.datasource.hikari.maximum-pool-size=6", "spring.jpa.properties.hibernate.generate_statistics=true"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReceptionBookingIntegrationTest {
    static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    static final LocalDateTime NOW = TODAY.atTime(10, 0);
    static final String AUDIT_TRIGGER = "trg_be13_audit_failure";
    static final List<String> TABLES = List.of("activity_log", "check_in", "booking", "class_session",
            "sport_package_registration", "sport_class", "coach_sport", "coach_profile", "facility",
            "sport_package", "sport", "payment", "member_profile", "user_account");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { ReceptionBookingDatabase.register(registry); }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock receptionClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired BookingService bookingService;
    @Autowired MemberScheduleService schedule;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ScheduledAnnotationBeanPostProcessor scheduling;
    @Autowired jakarta.persistence.EntityManagerFactory emf;
    @Autowired jakarta.persistence.EntityManager em;
    private final AtomicInteger cases = new AtomicInteger();
    private Map<String, Long> baseline;
    private Map<String, List<Long>> ids;
    private List<ExecutorService> pools;
    private String marker;
    private int serial;
    private long sport, facility, sportClass, coachedPackage, selfPackage;
    private UserAccount memberA, memberB, receptionist, manager, coach;

    private Map<String, Long> counts() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : TABLES) result.put(table, jdbc.queryForObject("SELECT COUNT_BIG(*) FROM " + table, Long.class));
        return result;
    }
    @BeforeAll void baseline() {
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo(ReceptionBookingDatabase.name());
        scheduling.getScheduledTasks().forEach(task -> task.cancel());
        baseline = counts();
        assertThat(baseline.get("sport")).isEqualTo(6); assertThat(baseline.get("sport_package")).isEqualTo(36);
    }
    @BeforeEach void fixture() {
        ids = new LinkedHashMap<>(); pools = new ArrayList<>();
        assertThat(counts()).as("Previous BE-13 invocation cleaned every committed fixture").isEqualTo(baseline);
        marker = "be13_" + cases.incrementAndGet() + "_"; serial = 0;
        sport = insert("sport", "INSERT INTO sport(code,name,venue_type) OUTPUT INSERTED.id VALUES (?,?,'INDOOR')", code(), "BE-13 sport");
        memberA = member(); memberB = member(); receptionist = account("RECEPTIONIST"); manager = account("MANAGER"); coach = account("COACH");
        jdbc.update("INSERT INTO coach_profile(user_id,primary_sport_id,is_public,display_order) VALUES (?,?,1,0)", coach.getId(), sport);
        remember("coach_profile", coach.getId());
        jdbc.update("INSERT INTO coach_sport(coach_id,sport_id) VALUES (?,?)", coach.getId(), sport); remember("coach_sport", coach.getId());
        facility = insert("facility", "INSERT INTO facility(code,name,sport_id,facility_type,is_active) OUTPUT INSERTED.id VALUES (?,?,?,'COURT',1)", code(), "BE-13 court", sport);
        sportClass = insert("sport_class", "INSERT INTO sport_class(code,name,sport_id,max_members,is_active) OUTPUT INSERTED.id VALUES (?,?,?,20,1)", code(), "BE-13 class", sport);
        coachedPackage = pack("COACH_LED"); selfPackage = pack("SELF_TRAINING");
    }
    private String code() { return marker + ++serial; }
    private void remember(String table, long id) { ids.computeIfAbsent(table, ignored -> new ArrayList<>()).add(id); }
    private long insert(String table, String sql, Object... args) {
        if (table.equals("payment") && sql.contains("OUTPUT INSERTED.id")) {
            sql = "SET NOCOUNT ON; DECLARE @cash_fixture_id TABLE(id BIGINT); "
                    + sql.replace("OUTPUT INSERTED.id", "OUTPUT INSERTED.id INTO @cash_fixture_id")
                    + "; SELECT id FROM @cash_fixture_id";
        }
        if (!TABLES.contains(table)) throw new IllegalArgumentException(table);
        long id = Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args)); remember(table, id); return id;
    }
    private UserAccount account(String role) {
        long id = insert("user_account", "INSERT INTO user_account(email,password_hash,full_name,role_id,status,version) OUTPUT INSERTED.id "
                + "VALUES (?,'test-only-non-credential',?,(SELECT id FROM role WHERE code=?),'ACTIVE',0)", code()+"@example.test", "BE-13 " + role, role);
        return new TransactionTemplate(transactions).execute(status -> {
            var actor = users.findById(id).orElseThrow(); actor.getRole().getCode(); return actor;
        });
    }
    private UserAccount member() {
        var member = account("MEMBER");
        jdbc.update("INSERT INTO member_profile(user_id,member_code,joined_on) VALUES (?,?,?)", member.getId(), code(), TODAY);
        remember("member_profile", member.getId()); return member;
    }
    private long pack(String format) {
        return insert("sport_package", "INSERT INTO sport_package(code,name,sport_id,training_format,duration_days,session_count,price_amount,session_minutes) "
                + "OUTPUT INSERTED.id VALUES (?,?,?, ?,60,10,100,60)", code(), "BE-13 " + format, sport, format);
    }
    private long registration(UserAccount member, long pack, int credits) {
        return insert("sport_package_registration", "INSERT INTO sport_package_registration(registration_code,member_id,package_id,channel,original_price,paid_amount,"
                + "total_sessions,remaining_sessions,start_date,end_date,status,created_by_user_id) OUTPUT INSERTED.id VALUES (?,?,?,'RECEPTION',100,100,10,?,?,?,'ACTIVE',?)",
                code(), member.getId(), pack, credits, TODAY.minusDays(2), TODAY.plusDays(40), manager.getId());
    }
    private long registration(UserAccount member) { return registration(member, coachedPackage, 3); }
    private long session(LocalDate date, int capacity, boolean self) {
        return insert("class_session", "INSERT INTO class_session(class_id,sport_id,facility_id,coach_id,training_type,session_date,start_time,end_time,capacity,booked_count,status) "
                + "OUTPUT INSERTED.id VALUES (?,?,?,?,?,?,'12:00','13:00',?,0,'PUBLISHED')", self ? null : sportClass, sport, facility,
                self ? null : coach.getId(), self ? "SELF_TRAINING" : "COACH_LED", date, capacity);
    }
    private long session() { return session(TODAY.plusDays(1), 10, false); }
    private long legacy(long session, UserAccount member, long registration, String status) {
        long id = insert("booking", "INSERT INTO booking(booking_code,session_id,member_id,package_registration_id,status,booked_at) OUTPUT INSERTED.id VALUES (?,?,?,?,?,?)",
                code(), session, member.getId(), registration, status, NOW);
        if (status.equals("CONFIRMED")) jdbc.update("UPDATE class_session SET booked_count=booked_count+1 WHERE id=?", session);
        return id;
    }
    private record Outcome(int status, JsonNode body) { String reason() { return body.path("code").asText(null); } }
    private Outcome http(MockHttpServletRequestBuilder request, UserAccount actor) {
        try {
            if (actor != null) request.with(user(new CustomUserDetails(actor)));
            var response = mvc.perform(request).andReturn().getResponse();
            return new Outcome(response.getStatus(), json.readTree(response.getContentAsString()));
        } catch (Exception failure) { throw new AssertionError(failure); }
    }
    private Outcome create(UserAccount actor, UserAccount member, long session, long registration) {
        String body = "{\"sessionId\":"+session+",\"packageRegistrationId\":"+registration
                +(member == null ? "" : ",\"memberId\":"+member.getId())+"}";
        return http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content(body), actor);
    }
    private int value(String table, String column, long id) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT "+column+" FROM "+table+" WHERE id=?", Integer.class,id));
    }
    private int credits(long registration) { return value("sport_package_registration","remaining_sessions",registration); }
    private int seats(long session) { return value("class_session","booked_count",session); }
    private long bookingCount(long session) { return jdbc.queryForObject("SELECT COUNT_BIG(*) FROM booking WHERE session_id=? AND status='CONFIRMED'",Long.class,session); }
    private JsonNode eligibility(UserAccount actor, UserAccount target, long session) {
        var result = http(get("/api/v1/sessions/"+session+"/eligibility?memberId="+target.getId()),actor);
        assertThat(result.status()).isEqualTo(200); return result.body();
    }
    private ExecutorService pool() { var pool = Executors.newFixedThreadPool(2); pools.add(pool); return pool; }
    private List<Outcome> race(Callable<Outcome> first, Callable<Outcome> second) throws Exception {
        return race(first, second, () -> { });
    }
    private List<Outcome> race(Callable<Outcome> first, Callable<Outcome> second, Runnable beforeSecond) throws Exception {
        var workers = pool();
        CompletableFuture<Void> secondReady=new CompletableFuture<>(),firstWritten=new CompletableFuture<>(),release=new CompletableFuture<>();
        CompletableFuture<Integer> firstConnection=new CompletableFuture<>(),secondConnection=new CompletableFuture<>();
        var a=workers.submit(() -> {
            try {
                return new TransactionTemplate(transactions).execute(status -> {
                    await(secondReady);
                    firstConnection.complete(jdbc.queryForObject("SELECT @@SPID",Integer.class));
                    Outcome result=invoke(first,status);
                    assertThat(result.status()).isEqualTo(201);
                    firstWritten.complete(null); await(release); return result;
                });
            } catch(Throwable failure) { firstWritten.completeExceptionally(failure); firstConnection.completeExceptionally(failure); throw failure; }
        });
        var b=workers.submit(() -> {
            try {
                return new TransactionTemplate(transactions).execute(status -> {
                    beforeSecond.run(); secondReady.complete(null); await(firstWritten);
                    secondConnection.complete(jdbc.queryForObject("SELECT @@SPID",Integer.class));
                    return invoke(second,status);
                });
            } catch(Throwable failure) { secondReady.completeExceptionally(failure); secondConnection.completeExceptionally(failure); throw failure; }
        });
        try {
            int blocker=firstConnection.get(15,TimeUnit.SECONDS),waiter=secondConnection.get(15,TimeUnit.SECONDS);
            assertThat(waiter).isNotEqualTo(blocker);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3); boolean blocked=false;
            while(System.nanoTime()<deadline) {
                blocked=jdbc.queryForObject("SELECT COUNT(*) FROM sys.dm_exec_requests WHERE session_id=? AND blocking_session_id=?",
                        Integer.class,waiter,blocker)>0;
                if(blocked) break;
                Thread.sleep(20);
            }
            assertThat(blocked).as("The exact second SQL Server connection must wait for the first transaction").isTrue();
        } finally { release.complete(null); }
        return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
    }
    private static Outcome invoke(Callable<Outcome> work,TransactionStatus status) {
        try {
            Outcome outcome=work.call();
            // HTTP advice consumes service exceptions; the enclosing test transaction must also roll back.
            if(outcome.status()>=400) status.setRollbackOnly();
            return outcome;
        } catch(RuntimeException|Error failure) { throw failure; }
        catch(Exception failure) { throw new IllegalStateException(failure); }
    }
    private static void await(CompletableFuture<Void> signal) {
        try { signal.get(15,TimeUnit.SECONDS); }
        catch(InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        catch(ExecutionException|TimeoutException failure) { throw new IllegalStateException("BE-13 race coordination failed",failure); }
    }
    private void discover(String table, String foreignKey, String parentTable) {
        var parents = ids.getOrDefault(parentTable,List.of()); if (parents.isEmpty()) return;
        String placeholders = String.join(",",Collections.nCopies(parents.size(),"?"));
        jdbc.queryForList("SELECT id FROM "+table+" WHERE "+foreignKey+" IN ("+placeholders+")",Long.class,parents.toArray())
                .forEach(id -> remember(table,id));
    }
    @AfterEach void cleanup() {
        List<Throwable> failures = new ArrayList<>(); boolean interrupted = false;
        for (var pool : pools) pool.shutdownNow();
        for (var pool : pools) {
            try { if (!pool.awaitTermination(15,TimeUnit.SECONDS)) failures.add(new AssertionError("BE-13 worker did not terminate")); }
            catch (InterruptedException failure) { interrupted = true; failures.add(failure); }
        }
        attempt(failures, () -> jdbc.execute("IF OBJECT_ID('"+AUDIT_TRIGGER+"','TR') IS NOT NULL DROP TRIGGER "+AUDIT_TRIGGER));
        attempt(failures, () -> discover("activity_log","actor_user_id","user_account"));
        attempt(failures, () -> discover("booking","member_id","member_profile"));
        for (String table : TABLES) attempt(failures, () -> {
            var rows = ids.getOrDefault(table,List.of()); if (rows.isEmpty()) return;
            String column = switch (table) { case "member_profile", "coach_profile" -> "user_id"; case "coach_sport" -> "coach_id"; default -> "id"; };
            jdbc.update("DELETE FROM "+table+" WHERE "+column+" IN ("+String.join(",",Collections.nCopies(rows.size(),"?"))+")",rows.toArray());
        });
        attempt(failures, () -> assertThat(counts()).as("Private database returned to canonical baseline").isEqualTo(baseline));
        if (interrupted) Thread.currentThread().interrupt();
        if (!failures.isEmpty()) { var failure = new AssertionError("BE-13 cleanup failed; every cleanup step was attempted"); failures.forEach(failure::addSuppressed); throw failure; }
    }
    private static void attempt(List<Throwable> failures, Runnable work) { try { work.run(); } catch (Throwable failure) { failures.add(failure); } }

    @ParameterizedTest @CsvSource({"RECEPTIONIST,,", "MANAGER,UPCOMING,CONFIRMED", "RECEPTIONIST,PAST,CANCELLED",
            "MANAGER,UPCOMING,", "MANAGER,PAST,", "RECEPTIONIST,,CANCELLED", "MANAGER,UPCOMING,CANCELLED"})
    void historyFiltersAreMemberScopedAndMatchOwnShape(String role, BookingWhen when, BookingStatus status) {
        long regA=registration(memberA),regB=registration(memberB);
        long future=session(),past=session(TODAY.minusDays(1),10,false);
        long a=legacy(future,memberA,regA,"CONFIRMED"), b=legacy(future,memberA,regA,"CANCELLED"), c=legacy(past,memberA,regA,"CONFIRMED");
        legacy(future,memberB,regB,"CONFIRMED");
        String query=(when==null?"":"&when="+when)+(status==null?"":"&status="+status);
        var result=http(get("/api/v1/bookings/member/"+memberA.getId()+"?"+query),role.equals("MANAGER")?manager:receptionist);
        assertThat(result.status()).isEqualTo(200); assertThat(result.body().isArray()).isTrue();
        Set<Long> expected=new HashSet<>();
        if ((when==null||when==BookingWhen.UPCOMING)&&(status==null||status==BookingStatus.CONFIRMED)) expected.add(a);
        if ((when==null||when==BookingWhen.PAST)&&(status==null||status==BookingStatus.CANCELLED)) expected.add(b);
        if ((when==null||when==BookingWhen.PAST)&&(status==null||status==BookingStatus.CONFIRMED)) expected.add(c);
        List<Long> actual=new ArrayList<>();
        result.body().forEach(row -> { actual.add(row.path("id").asLong()); assertThat(row.path("memberId").asLong()).isEqualTo(memberA.getId());
            assertThat(row.path("packageRegistrationId").asLong()).isEqualTo(regA); assertThat(row.has("passwordHash")).isFalse(); });
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        if (when==null&&status==null) assertThat(result.body()).isEqualTo(http(get("/api/v1/bookings/my"),memberA).body());
    }

    @Test void historyPaginationHasExactCountsAndDeterministicTieBreakWithoutNPlusOne() {
        long reg=registration(memberA);
        List<Long> expected=new ArrayList<>(); for(int i=0;i<5;i++) expected.add(legacy(session(),memberA,reg,"CONFIRMED"));
        Collections.reverse(expected);
        var stats=emf.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        for(int page=0;page<3;page++) {
            stats.clear(); var result=http(get("/api/v1/bookings/member/"+memberA.getId()+"?page="+page+"&size=2"),receptionist);
            assertThat(result.status()).isEqualTo(200); assertThat(result.body().path("totalElements").asLong()).isEqualTo(5);
            assertThat(result.body().path("totalPages").asInt()).isEqualTo(3);
            List<Long> actual=new ArrayList<>(); result.body().path("content").forEach(row -> actual.add(row.path("id").asLong()));
            assertThat(actual).containsExactlyElementsOf(expected.subList(page*2,Math.min(5,page*2+2)));
            assertThat(stats.getPrepareStatementCount()).as("Bounded profile/account/content/count reads, no per-booking fetch").isLessThanOrEqualTo(7);
        }
        assertThat(http(get("/api/v1/bookings/member/"+memberB.getId()+"?page=0&size=2"),manager).body().path("totalElements").asLong()).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"MEMBER","COACH","ANONYMOUS"})
    void forbiddenHistoryAndEligibilityDiscloseNoMemberInformation(String role) {
        var actor=role.equals("MEMBER")?memberB:role.equals("COACH")?coach:null;
        for(String path:List.of("/api/v1/bookings/member/"+memberA.getId(),"/api/v1/sessions/1/eligibility?memberId="+memberA.getId())) {
            var result=http(get(path),actor); assertThat(result.status()).isEqualTo(actor==null?401:403);
            assertThat(result.body().has("suitableRegistrations")).isFalse(); assertThat(result.body().has("memberId")).isFalse();
        }
    }

    @ParameterizedTest @ValueSource(strings={"RECEPTIONIST","MANAGER"})
    void staffEligibilityEqualsBE11AndLeavesAllRowsUnchanged(String role) {
        long session=session(),regA=registration(memberA); registration(memberB);
        var before=counts(); int balance=credits(regA),seats=seats(session);
        var actor=role.equals("MANAGER")?manager:receptionist;
        var assisted=eligibility(actor,memberA,session);
        assertThat(assisted).isEqualTo(http(get("/api/v1/sessions/"+session),memberA).body());
        assertThat(assisted.path("eligible").asBoolean()).isTrue();
        assertThat(assisted.path("suitableRegistrations").size()).isEqualTo(1);
        assertThat(assisted.path("suitableRegistrations").get(0).path("registrationId").asLong()).isEqualTo(regA);
        assertThat(counts()).isEqualTo(before); assertThat(credits(regA)).isEqualTo(balance); assertThat(seats(session)).isEqualTo(seats);
        long regB=jdbc.queryForObject("SELECT id FROM sport_package_registration WHERE member_id=?",Long.class,memberB.getId());
        assertThat(eligibility(actor,memberB,session).path("suitableRegistrations").get(0).path("registrationId").asLong()).isEqualTo(regB);
    }

    @ParameterizedTest @CsvSource({"CREDITS,NO_SESSIONS_LEFT", "EXPIRED,PACKAGE_EXPIRED_BY_THEN", "NOT_STARTED,PACKAGE_NOT_STARTED_BY_THEN",
            "REFUNDED,NO_PACKAGE", "CANCELLED,NO_PACKAGE", "FORMAT,NO_PACKAGE", "SPORT,NO_PACKAGE", "DUPLICATE,ALREADY_BOOKED", "FULL,FULL"})
    void eligibilityRetainsBE11ReasonCodes(String scenario,String reason) {
        long session=session(TODAY.plusDays(1),1,false),reg=registration(memberA);
        switch(scenario) {
            case "CREDITS" -> jdbc.update("UPDATE sport_package_registration SET remaining_sessions=0 WHERE id=?",reg);
            case "EXPIRED" -> jdbc.update("UPDATE sport_package_registration SET status='EXPIRED' WHERE id=?",reg);
            case "NOT_STARTED" -> jdbc.update("UPDATE sport_package_registration SET start_date=? WHERE id=?",TODAY.plusDays(2),reg);
            case "REFUNDED", "CANCELLED" -> jdbc.update("UPDATE sport_package_registration SET status=? WHERE id=?",scenario,reg);
            case "FORMAT" -> jdbc.update("UPDATE sport_package_registration SET package_id=? WHERE id=?",selfPackage,reg);
            case "SPORT" -> jdbc.update("UPDATE sport_package SET sport_id=(SELECT TOP 1 id FROM sport WHERE id<>? ORDER BY id) WHERE id=?",sport,coachedPackage);
            case "DUPLICATE" -> legacy(session,memberA,reg,"CONFIRMED");
            case "FULL" -> legacy(session,memberB,registration(memberB),"CONFIRMED");
            default -> throw new IllegalArgumentException(scenario);
        }
        var result=eligibility(receptionist,memberA,session); assertThat(result.path("eligible").asBoolean()).isFalse();
        assertThat(result.path("reason").asText()).isEqualTo(reason);
        assertThat(result).isEqualTo(http(get("/api/v1/sessions/"+session),memberA).body());
    }

    @ParameterizedTest @ValueSource(strings={"RECEPTIONIST","MANAGER"})
    void staffBookingPersistsBeneficiaryPackageCountersAndActualActor(String role) throws Exception {
        long session=session(),reg=registration(memberA); var actor=role.equals("MANAGER")?manager:receptionist;
        var result=create(actor,memberA,session,reg); assertThat(result.status()).isEqualTo(201);
        long id=result.body().path("id").asLong();
        assertThat(result.body().path("memberId").asLong()).isEqualTo(memberA.getId());
        assertThat(result.body().path("packageRegistrationId").asLong()).isEqualTo(reg);
        assertThat(seats(session)).isEqualTo(1); assertThat(bookingCount(session)).isEqualTo(1); assertThat(credits(reg)).isEqualTo(2);
        var log=jdbc.queryForMap("SELECT actor_user_id,details,entity_type FROM activity_log WHERE action='BOOKING_CREATED' AND entity_id=?",id);
        assertThat(((Number)log.get("actor_user_id")).longValue()).isEqualTo(actor.getId());
        var details=json.readTree((String)log.get("details")); assertThat(details.path("memberId").asLong()).isEqualTo(memberA.getId());
        assertThat(details.path("packageRegistrationId").asLong()).isEqualTo(reg); assertThat(details.has("passwordHash")).isFalse();
        assertThat(log.get("entity_type")).isEqualTo("BOOKING");
    }

    @ParameterizedTest @CsvSource({"OWNER,400,NO_PACKAGE", "EXPIRED,400,PACKAGE_EXPIRED_BY_THEN", "REFUNDED,400,NO_PACKAGE",
            "CREDITS,400,NO_SESSIONS_LEFT", "FORMAT,400,NO_PACKAGE", "DUPLICATE,409,ALREADY_BOOKED", "FULL,409,FULL",
            "SPORT,400,NO_PACKAGE", "NOT_STARTED,400,PACKAGE_NOT_STARTED_BY_THEN", "ENDED,400,PACKAGE_EXPIRED_BY_THEN",
            "PENDING_PAYMENT,400,NO_PACKAGE", "CANCELLED,400,NO_PACKAGE"})
    void staffBookingRechecksEligibilityAndRejectsWithoutPartialWrites(String scenario,int status,String reason) {
        long session=session(TODAY.plusDays(1),1,false),reg=registration(memberA);
        switch(scenario) {
            case "OWNER" -> jdbc.update("UPDATE sport_package_registration SET member_id=? WHERE id=?",memberB.getId(),reg);
            case "EXPIRED", "REFUNDED", "PENDING_PAYMENT", "CANCELLED" -> jdbc.update("UPDATE sport_package_registration SET status=? WHERE id=?",scenario,reg);
            case "CREDITS" -> jdbc.update("UPDATE sport_package_registration SET remaining_sessions=0 WHERE id=?",reg);
            case "FORMAT" -> jdbc.update("UPDATE sport_package_registration SET package_id=? WHERE id=?",selfPackage,reg);
            case "SPORT" -> jdbc.update("UPDATE sport_package SET sport_id=(SELECT TOP 1 id FROM sport WHERE id<>? ORDER BY id) WHERE id=?",sport,coachedPackage);
            case "NOT_STARTED" -> jdbc.update("UPDATE sport_package_registration SET start_date=? WHERE id=?",TODAY.plusDays(2),reg);
            case "ENDED" -> jdbc.update("UPDATE sport_package_registration SET end_date=? WHERE id=?",TODAY,reg);
            case "DUPLICATE" -> legacy(session,memberA,reg,"CONFIRMED");
            case "FULL" -> legacy(session,memberB,registration(memberB),"CONFIRMED");
            default -> throw new IllegalArgumentException(scenario);
        }
        var before=counts(); int credits=credits(reg),seats=seats(session);
        var result=create(receptionist,memberA,session,reg); assertThat(result.status()).isEqualTo(status); assertThat(result.reason()).isEqualTo(reason);
        assertThat(credits(reg)).isEqualTo(credits); assertThat(seats(session)).isEqualTo(seats); assertThat(counts()).isEqualTo(before);
    }

    @Test void memberForeignBeneficiaryAndUnrelatedRolesCannotCreateStaffBooking() {
        long session=session(),reg=registration(memberA); var before=counts();
        for(var actor:List.of(memberB,coach)) assertThat(create(actor,memberA,session,reg).status()).isEqualTo(403);
        assertThat(create(null,memberA,session,reg).status()).isEqualTo(401);
        assertThat(counts()).isEqualTo(before); assertThat(seats(session)).isZero(); assertThat(credits(reg)).isEqualTo(3);
    }

    @Test void managerCanReadMemberBAndCombinedPagedFiltersHaveIdenticalTotals() {
        long a=registration(memberA),b=registration(memberB),future=session(),past=session(TODAY.minusDays(1),10,false);
        legacy(future,memberA,a,"CONFIRMED");
        long wanted=legacy(future,memberB,b,"CONFIRMED"); legacy(past,memberB,b,"CONFIRMED"); legacy(future,memberB,b,"CANCELLED");
        var result=http(get("/api/v1/bookings/member/"+memberB.getId()+"?when=UPCOMING&status=CONFIRMED&page=0&size=1"),manager);
        assertThat(result.status()).isEqualTo(200); assertThat(result.body().path("totalElements").asLong()).isEqualTo(1);
        assertThat(result.body().path("totalPages").asInt()).isEqualTo(1);
        assertThat(result.body().path("content").size()).isEqualTo(1);
        assertThat(result.body().path("content").get(0).path("id").asLong()).isEqualTo(wanted);
        assertThat(result.body().path("content").get(0).path("memberId").asLong()).isEqualTo(memberB.getId());
    }

    @Test void missingTargetSelectionAndUnknownResourcesUseApprovedErrors() {
        long session=session(),reg=registration(memberA);
        assertThat(create(receptionist,null,session,reg).status()).isEqualTo(400);
        assertThat(create(receptionist,memberA,session,Long.MAX_VALUE).status()).isEqualTo(404);
        assertThat(create(receptionist,memberA,Long.MAX_VALUE,reg).status()).isEqualTo(404);
        assertThat(http(get("/api/v1/bookings/member/"+Long.MAX_VALUE),manager).status()).isEqualTo(404);
        assertThat(http(get("/api/v1/bookings/member/"+coach.getId()),manager).status()).isEqualTo(404);
        assertThat(http(get("/api/v1/sessions/"+session+"/eligibility?memberId="+coach.getId()),manager).status()).isEqualTo(404);
    }

    @Test void selfTrainingAndExistingMemberBookingAndCancellationRemainCompatible() {
        long session=session(TODAY.plusDays(1),10,true),reg=registration(memberA,selfPackage,3);
        var result=create(receptionist,memberA,session,reg); assertThat(result.status()).isEqualTo(201);
        assertThat(result.body().path("coachId").isNull()).isTrue(); assertThat(result.body().path("classId").isNull()).isTrue();
        long id=result.body().path("id").asLong();
        assertThat(http(delete("/api/v1/bookings/"+id),memberA).status()).isEqualTo(200);
        assertThat(seats(session)).isZero(); assertThat(credits(reg)).isEqualTo(3);
        assertThat(create(memberA,null,session,reg).status()).isEqualTo(201);
        assertThat(credits(reg)).isEqualTo(2); assertThat(seats(session)).isEqualTo(1);
    }

    @Test void realAuditFailureRollsBackBookingSeatCreditAndAudit() {
        long session=session(),reg=registration(memberA); var before=counts();
        jdbc.execute("CREATE TRIGGER "+AUDIT_TRIGGER+" ON activity_log AFTER INSERT AS BEGIN SET NOCOUNT ON; "
                + "IF EXISTS(SELECT 1 FROM inserted WHERE action='BOOKING_CREATED' AND actor_user_id="+receptionist.getId()+") "
                + "THROW 51013, 'Synthetic BE-13 audit failure', 1; END;");
        try { assertThat(create(receptionist,memberA,session,reg).status()).isEqualTo(500); }
        finally { jdbc.execute("DROP TRIGGER "+AUDIT_TRIGGER); }
        assertThat(seats(session)).isZero(); assertThat(credits(reg)).isEqualTo(3); assertThat(bookingCount(session)).isZero();
        assertThat(counts()).isEqualTo(before);
    }

    @Test void twoStaffCompetingForLastSeatProduceOneCreatedOneConflict() throws Exception {
        long session=session(TODAY.plusDays(1),1,false),a=registration(memberA),b=registration(memberB);
        var results=race(() -> create(receptionist,memberA,session,a),() -> create(manager,memberB,session,b));
        assertThat(results).extracting(Outcome::status).containsExactlyInAnyOrder(201,409);
        assertThat(results.stream().filter(r -> r.status()==409).findFirst().orElseThrow().reason()).isEqualTo("FULL");
        assertThat(seats(session)).isEqualTo(1); assertThat(bookingCount(session)).isEqualTo(1); assertThat(credits(a)+credits(b)).isEqualTo(5);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void staffAndMemberOrTwoStaffCannotDuplicateBeneficiaryBooking(boolean twoStaff) throws Exception {
        long session=session(),reg=registration(memberA);
        var results=race(() -> create(receptionist,memberA,session,reg),
                () -> twoStaff?create(manager,memberA,session,reg):create(memberA,null,session,reg));
        assertThat(results).extracting(Outcome::status).containsExactlyInAnyOrder(201,409);
        assertThat(results.stream().filter(r -> r.status()==409).findFirst().orElseThrow().reason()).isEqualTo("ALREADY_BOOKED");
        assertThat(seats(session)).isEqualTo(1); assertThat(bookingCount(session)).isEqualTo(1); assertThat(credits(reg)).isEqualTo(2);
    }

    @Test void differentSessionsCompetingForOnePackageCreditCannotBothSucceed() throws Exception {
        long first=session(),second=session(TODAY.plusDays(2),10,false),reg=registration(memberA,coachedPackage,1);
        var results=race(() -> create(receptionist,memberA,first,reg),() -> create(manager,memberA,second,reg),
                () -> assertThat(em.find(SportPackageRegistration.class,reg).getRemainingSessions()).isEqualTo(1));
        assertThat(results).extracting(Outcome::status).containsExactlyInAnyOrder(201,400);
        assertThat(results.stream().filter(r -> r.status()==400).findFirst().orElseThrow().reason()).isEqualTo("NO_SESSIONS_LEFT");
        assertThat(credits(reg)).isZero(); assertThat(seats(first)+seats(second)).isEqualTo(1);
        assertThat(bookingCount(first)+bookingCount(second)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"DRAFT","CANCELLED","COMPLETED","STARTED"})
    void hiddenSessionsKeepBE11NotFoundAndCannotBeBooked(String state) {
        long session=session(),reg=registration(memberA);
        if(state.equals("STARTED")) jdbc.update("UPDATE class_session SET session_date=?,start_time=? WHERE id=?",TODAY,NOW.toLocalTime(),session);
        else jdbc.update("UPDATE class_session SET status=? WHERE id=?",state,session);
        assertThat(http(get("/api/v1/sessions/"+session+"/eligibility?memberId="+memberA.getId()),manager).status()).isEqualTo(404);
        assertThat(create(receptionist,memberA,session,reg).status()).isEqualTo(400);
        assertThat(seats(session)).isZero(); assertThat(credits(reg)).isEqualTo(3);
    }

    @ParameterizedTest @ValueSource(strings={"when=OTHER","status=OTHER","page=-1","size=0","size=101","page=2147483647&size=100"})
    void invalidHistoryFiltersAndPaginationReturn400(String query) {
        assertThat(http(get("/api/v1/bookings/member/"+memberA.getId()+"?"+query),manager).status()).isEqualTo(400);
    }

    @Test void clientActorFieldsCannotChangePersistedStaffAttribution() {
        long session=session(),reg=registration(memberA);
        var result=http(post("/api/v1/bookings").contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":"+memberA.getId()
                +",\"sessionId\":"+session+",\"packageRegistrationId\":"+reg+",\"actorId\":"+manager.getId()
                +",\"staffId\":"+manager.getId()+",\"role\":\"MANAGER\"}"),receptionist);
        assertThat(result.status()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT actor_user_id FROM activity_log WHERE action='BOOKING_CREATED' AND entity_id=?",Long.class,
                result.body().path("id").asLong())).isEqualTo(receptionist.getId());
    }

    @Test void eligibilitySnapshotCannotBypassLaterRefundOrExpirationAndUnrelatedRowsArePreserved() {
        long session=session(),reg=registration(memberA),unrelatedSession=session(),unrelatedReg=registration(memberB);
        long unrelatedBooking=legacy(unrelatedSession,memberB,unrelatedReg,"CONFIRMED");
        long payment=insert("payment","INSERT INTO payment(payment_code,member_id,amount,payment_method,payment_status) OUTPUT INSERTED.id VALUES (?,?,100,'CASH','PENDING')",
                code(),memberB.getId());
        var beforeBooking=jdbc.queryForMap("SELECT * FROM booking WHERE id=?",unrelatedBooking);
        var beforePayment=jdbc.queryForMap("SELECT * FROM payment WHERE id=?",payment);
        assertThat(eligibility(receptionist,memberA,session).path("eligible").asBoolean()).isTrue();
        for(String state:List.of("REFUNDED","EXPIRED")) {
            jdbc.update("UPDATE sport_package_registration SET status=? WHERE id=?",state,reg);
            assertThat(create(receptionist,memberA,session,reg).status()).isEqualTo(400);
            assertThat(seats(session)).isZero(); assertThat(credits(reg)).isEqualTo(3);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM booking WHERE id=?",unrelatedBooking)).isEqualTo(beforeBooking);
        assertThat(jdbc.queryForMap("SELECT * FROM payment WHERE id=?",payment)).isEqualTo(beforePayment);
    }

    @ParameterizedTest @ValueSource(strings={"REFUND","CANCELLATION"})
    void staffBookingSerializedBehindCommittedRefundOrSessionCancellationCannotUseStaleEligibility(String operation) throws Exception {
        long session=session(),reg=registration(memberA);
        assertThat(eligibility(receptionist,memberA,session).path("eligible").asBoolean()).isTrue();
        CompletableFuture<Void> locked=new CompletableFuture<>(),attempted=new CompletableFuture<>();
        var workers=pool();
        var mutation=workers.submit(() -> {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    // Same first lock as BE-10/BE-21, before either status or package changes.
                    jdbc.queryForObject("SELECT id FROM class_session WITH (UPDLOCK,ROWLOCK) WHERE id=?",Long.class,session);
                    if(operation.equals("REFUND")) jdbc.update("UPDATE sport_package_registration SET status='REFUNDED' WHERE id=?",reg);
                    else jdbc.update("UPDATE class_session SET status='CANCELLED' WHERE id=?",session);
                    locked.complete(null);
                    try { attempted.get(15,TimeUnit.SECONDS); } catch(Exception failure) { throw new IllegalStateException(failure); }
                });
            } catch(Throwable failure) { locked.completeExceptionally(failure); throw failure; }
        });
        var booking=workers.submit(() -> {
            locked.get(15,TimeUnit.SECONDS);
            attempted.complete(null); // Before trying to acquire the already-held session lock.
            return create(receptionist,memberA,session,reg);
        });
        mutation.get(30,TimeUnit.SECONDS);
        assertThat(booking.get(30,TimeUnit.SECONDS).status()).isEqualTo(400);
        assertThat(seats(session)).isZero(); assertThat(credits(reg)).isEqualTo(3); assertThat(bookingCount(session)).isZero();
    }
}
