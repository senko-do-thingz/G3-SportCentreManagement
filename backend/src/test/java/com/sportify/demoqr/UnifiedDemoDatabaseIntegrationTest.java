package com.sportify.demoqr;

import com.sportify.support.DisposableUnifyDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = {"dev", "demo", "unified-demo", "unified-demo-test"}, inheritProfiles = false)
@TestPropertySource(properties = {"app.payment.unified-demo.enabled=true", "app.payment.unified-demo.test-enabled=true",
        "server.address=127.0.0.1", "app.cors.allowed-origins=http://127.0.0.1:15173", "spring.jpa.show-sql=false"})
class UnifiedDemoDatabaseIntegrationTest extends LocalDemoWorkflow {
    private static DisposableUnifyDatabase fixture;
    @Autowired ApplicationContext context;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        fixture = DisposableUnifyDatabase.create(); var jdbc = fixture.jdbc();
        Flyway.configure().dataSource(fixture.source()).locations("classpath:db/migration/identity", "classpath:db/migration/catalog", "classpath:db/demo")
                .cleanDisabled(true).load().migrate();
        // These are new, owned fixtures, never an existing user's identities.
        jdbc.update("UPDATE user_account SET email=REPLACE(email,'@example.com','@unified-demo.invalid') WHERE email IN ('an.gold@example.com','mai.standard@example.com')");
        for (String local : new String[]{"rollback", "unsupported", "ordinary", "ops", "fault0", "fault1", "fault2", "fault3", "fault4", "fault5", "fault6", "fault7", "fault8", "life0", "life1", "life2", "life3", "life4", "life5", "life6", "life7", "life8"}) {
            jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,password_hash,'Synthetic unify fixture',role_id,'ACTIVE' FROM user_account WHERE email='an.gold@unified-demo.invalid'", local + "@unified-demo.invalid");
            jdbc.update("INSERT member_profile(user_id,member_code) SELECT id,? FROM user_account WHERE email=?", "UNIFY-" + local, local + "@unified-demo.invalid");
        }
        String ids = String.join(",", jdbc.queryForList("SELECT CONVERT(varchar(20),id) FROM user_account WHERE email LIKE '%@unified-demo.invalid' ORDER BY id", String.class));
        jdbc.update("UPDATE user_account SET email='ordinary@example.test' WHERE email='ordinary@unified-demo.invalid'");
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'live@example.test',password_hash,'Real paid fixture',role_id,'ACTIVE' FROM user_account WHERE email='an.gold@unified-demo.invalid'");
        jdbc.update("INSERT member_profile(user_id,member_code) SELECT id,'UNIFY-LIVE' FROM user_account WHERE email='live@example.test'");
        jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT 'control@example.test',password_hash,'Unquarantined paid fixture',role_id,'ACTIVE' FROM user_account WHERE email='an.gold@unified-demo.invalid'");
        jdbc.update("INSERT member_profile(user_id,member_code) SELECT id,'UNIFY-CONTROL' FROM user_account WHERE email='control@example.test'");
        registry.add("spring.datasource.url", fixture::url);
        registry.add("spring.datasource.username", fixture::user);
        registry.add("spring.datasource.password", fixture::password);
        registry.add("app.payment.unified-demo.member-ids", () -> ids);
    }

    @Override protected String fixtureEmail(String email) {
        return email.replace("an.gold@example.com", "an.gold@unified-demo.invalid")
                .replace("mai.standard@example.com", "mai.standard@unified-demo.invalid");
    }

    @Test void exactlyOneDatasourceServesFlywayBusinessAndQrWithBackfillDisabled() {
        assertThat(context.getBeansOfType(DataSource.class)).hasSize(1);
        DataSource source = context.getBean(DataSource.class);
        assertThat(jdbcTemplate.getDataSource()).isSameAs(source);
        assertThat(context.getBean(Flyway.class).getConfiguration().getDataSource()).isSameAs(source);
        assertThat(context.getBeansOfType(jakarta.persistence.EntityManagerFactory.class)).hasSize(1);
        var factory = context.getBean(jakarta.persistence.EntityManagerFactory.class);
        assertThat(factory.getMetamodel().getEntities()).extracting(entity -> entity.getJavaType().getSimpleName())
                .contains("UserAccount", "MemberProfile", "MemberCard", "SportPackageRegistration", "Payment", "Invoice");
        assertThat(context.getBeansOfType(org.springframework.transaction.PlatformTransactionManager.class)).hasSize(1);
        var manager = context.getBean(org.springframework.orm.jpa.JpaTransactionManager.class);
        assertThat(manager.getDataSource()).isSameAs(source);
        assertThat(manager.getEntityManagerFactory()).isSameAs(factory);
        context.getBeansOfType(org.springframework.jdbc.core.JdbcTemplate.class).values()
                .forEach(template -> assertThat(template.getDataSource()).isSameAs(source));
        context.getBeansOfType(org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate.class).values()
                .forEach(template -> assertThat(template.getJdbcTemplate().getDataSource()).isSameAs(source));
        var entityManager = factory.createEntityManager();
        try {
            assertThat(entityManager.createNativeQuery("SELECT DB_NAME()").getSingleResult()).isEqualTo(fixture.name());
        } finally {
            entityManager.close();
        }
        assertThat(context.getBean(Flyway.class).getConfiguration().getIgnoreMigrationPatterns()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo(fixture.name());
        assertThat(context.getEnvironment().getProperty("app.training.attendance-backfill.enabled")).isEqualTo("false");
        assertThat(context.getBeansOfType(com.sportify.demoqr.service.DemoQrService.class)).isEmpty();
    }

    private long pending(String email, String method) {
        long member = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE email=?", Long.class, email);
        String code = "UNIFY-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0,20);
        jdbcTemplate.update("INSERT payment(payment_code,member_id,amount,payment_method,payment_status) VALUES (?,?,100,?,'PENDING')", code, member, method);
        long payment = jdbcTemplate.queryForObject("SELECT id FROM payment WHERE payment_code=?", Long.class, code);
        jdbcTemplate.update("INSERT member_card(card_code,member_id,tier_id,start_date,status,price_paid,payment_id) SELECT ?,?,MIN(id),CAST(SYSUTCDATETIME() AS DATE),'PENDING_PAYMENT',100,? FROM membership_card_tier", code, member, payment);
        return payment;
    }

    private ResponseEntity<String> confirm(long payment, String email) {
        return restTemplate.exchange("/api/v1/payments/" + payment + "/demo-confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of("amount", 1, "memberId", -1), authHeaders(login(email))), String.class);
    }

    @Test void ordinaryIdentityUnsupportedMethodAndStaffCannotSimulate() {
        long ordinary = pending("ordinary@example.test", "BANK_TRANSFER");
        assertThat(confirm(ordinary, "ordinary@example.test").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        long cash = pending("unsupported@unified-demo.invalid", "CASH");
        assertThat(confirm(cash, "unsupported@unified-demo.invalid").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (String email : new String[]{"lan.reception@example.com", "quan.coach@example.com"}) {
            assertThat(confirm(cash, email).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM simulated_payment_transaction WHERE payment_id IN (?,?)", Integer.class, ordinary, cash)).isZero();
    }

    @Test void sqlFailureRollsBackSettlementAndRetryRecoversWithoutDuplicates() {
        long payment = pending("rollback@unified-demo.invalid", "BANK_TRANSFER");
        String token = login("rollback@unified-demo.invalid");
        jdbcTemplate.execute("CREATE TRIGGER dbo.tr_unify_injected_audit_failure ON dbo.activity_log AFTER INSERT AS BEGIN SET NOCOUNT ON; IF EXISTS(SELECT 1 FROM inserted WHERE action='PAYMENT_CONFIRMED' AND entity_id=" + payment + ") THROW 51990,'Injected disposable audit failure',1; END");
        var failed = restTemplate.exchange("/api/v1/payments/" + payment + "/demo-confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of(), authHeaders(token)), String.class);
        assertThat(failed.getStatusCode().is5xxServerError()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, payment)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM member_card WHERE payment_id=?", String.class, payment)).isEqualTo("PENDING_PAYMENT");
        for (String table : new String[]{"simulated_qr_session", "simulated_payment_transaction", "invoice", "cash_event"})
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE payment_id=?", Integer.class, payment)).isZero();
        jdbcTemplate.execute("ALTER TRIGGER dbo.tr_unify_injected_audit_failure ON dbo.activity_log AFTER INSERT AS BEGIN SET NOCOUNT ON; RETURN; END");
        var recovered = restTemplate.exchange("/api/v1/payments/" + payment + "/demo-confirm", HttpMethod.POST,
                new HttpEntity<>(Map.of(), authHeaders(token)), String.class);
        assertThat(recovered.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (String table : new String[]{"simulated_payment_transaction", "invoice"})
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE payment_id=?", Integer.class, payment)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_log WHERE action='PAYMENT_CONFIRMED' AND entity_id=?", Integer.class, payment)).isEqualTo(1);
    }

    private ResponseEntity<String> write(String path, HttpMethod method, Object body, String token) {
        return restTemplate.exchange(path, method, new HttpEntity<>(body, authHeaders(token)), String.class);
    }

    private com.sportify.catalog.dto.PackageRegistrationResponse purchase(String token) {
        var response = restTemplate.exchange("/api/v1/packages/registrations", HttpMethod.POST,
                new HttpEntity<>(Map.of("packageId", jdbcTemplate.queryForObject("SELECT MIN(p.id) FROM sport_package p JOIN sport s ON s.id=p.sport_id WHERE s.code='BADMINTON' AND p.training_format='COACH_LED' AND p.duration_days>=14 AND p.price_amount>0",Long.class), "paymentMethod", "BANK_TRANSFER"), authHeaders(token)),
                com.sportify.catalog.dto.PackageRegistrationResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Map<String,Object> operationalSnapshot(long session, long realRegistration) {
        return jdbcTemplate.queryForMap("""
                SELECT s.booked_count, r.remaining_sessions,
                  (SELECT COUNT(*) FROM booking WHERE session_id=s.id) bookings,
                  (SELECT COUNT(*) FROM check_in) checkins,
                  (SELECT COUNT(*) FROM attendance_record) attendance,
                  (SELECT COUNT(*) FROM session_result) results,
                  (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM booking WHERE session_id=s.id) roster_checksum,
                  (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM check_in) checkin_checksum,
                  (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM attendance_record) attendance_checksum,
                  (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM session_result) result_checksum,
                  (SELECT CHECKSUM_AGG(BINARY_CHECKSUM(*)) FROM sport_package_registration WHERE id=r.id) real_entitlement_checksum,
                  (SELECT COUNT(*) FROM cash_event) cash,
                  (SELECT SUM(amount) FROM payment WHERE payment_status='SUCCESS'
                    AND ISNULL(settlement_type,'ACTUAL')<>'SIMULATED') actual_paid
                FROM class_session s CROSS JOIN sport_package_registration r WHERE s.id=? AND r.id=?
                """, session, realRegistration);
    }

    @Test void syntheticSettlementCannotConsumeRealCapacityOrWriteOperationalRecords() throws Exception {
        String synthetic = login("ops@unified-demo.invalid"), real = login("live@example.test");
        String reception = login("lan.reception@example.com"), coach = login("quan.coach@example.com");
        var simulated = purchase(synthetic); var actual = purchase(real);
        long owner = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE email='ops@unified-demo.invalid'", Long.class);
        var amount = jdbcTemplate.queryForObject("SELECT amount FROM payment WHERE id=?", java.math.BigDecimal.class, actual.getPaymentId());
        assertThat(write("/api/v1/payments/" + actual.getPaymentId() + "/confirm", HttpMethod.PUT,
                Map.of("method", "BANK_TRANSFER", "referenceCode", "UNIFY-REAL-PAID", "amountReceived", amount), reception).getStatusCode()).isEqualTo(HttpStatus.OK);
        // A normal operational class with one seat, a real receipt and paid entitlement in the same database.
        jdbcTemplate.update("INSERT facility(code,name,sport_id,facility_type,is_active) SELECT 'UNIFY-OPS','Operational fixture court',sport_id,'COURT',1 FROM sport_package WHERE id=(SELECT package_id FROM sport_package_registration WHERE id=?)", actual.getId());
        long sport=jdbcTemplate.queryForObject("SELECT p.sport_id FROM sport_package p JOIN sport_package_registration r ON r.package_id=p.id WHERE r.id=?",Long.class,actual.getId());
        var date=jdbcTemplate.queryForObject("SELECT CAST(DATEADD(day,7,GETDATE()) AS date)",java.sql.Date.class);
        String code=com.sportify.support.SessionIdentityFixtures.reserve(jdbcTemplate,date.toLocalDate(),sport);
        jdbcTemplate.update("""
                INSERT class_session(class_id,sport_id,session_date,start_time,end_time,facility_id,coach_id,training_type,capacity,booked_count,status,session_code)
                SELECT TOP 1 s.class_id,s.sport_id,?,'04:00','05:00',
                  (SELECT id FROM facility WHERE code='UNIFY-OPS'),s.coach_id,s.training_type,1,0,'PUBLISHED',?
                FROM class_session s JOIN sport_package p ON p.sport_id=s.sport_id AND p.training_format=s.training_type
                WHERE p.id=(SELECT package_id FROM sport_package_registration WHERE id=?) AND s.coach_id=(SELECT id FROM user_account WHERE email='quan.coach@example.com') ORDER BY s.id
                """, date, code, actual.getId());
        long session = jdbcTemplate.queryForObject("SELECT id FROM class_session WHERE facility_id=(SELECT id FROM facility WHERE code='UNIFY-OPS')", Long.class);
        var baseline = operationalSnapshot(session, actual.getId());
        assertThat(confirm(simulated.getPaymentId(), "ops@unified-demo.invalid").getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String,Object> syntheticBooking = Map.of("sessionId",session,"packageRegistrationId",simulated.getId());
        for (var attempt : new Object[][]{{syntheticBooking,synthetic},
                {Map.of("sessionId",session,"packageRegistrationId",simulated.getId(),"memberId",owner),reception}}) {
            var denied = write("/api/v1/bookings", HttpMethod.POST, attempt[0], (String)attempt[1]);
            assertThat(denied.getBody()).contains("SYNTHETIC_OPERATION_DENIED");
            assertThat(denied.getStatusCode().is4xxClientError()).isTrue();
        }
        assertThat(write("/api/v1/check-ins",HttpMethod.POST,Map.of("identifier",Long.toString(owner)),reception).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
        assertThat(operationalSnapshot(session, actual.getId())).isEqualTo(baseline);
        // A historical contaminated booking is not authority for attendance or result creation.
        jdbcTemplate.update("INSERT booking(booking_code,session_id,member_id,package_registration_id,status) VALUES ('UNIFY-HISTORIC',?,?,?,'CONFIRMED')",session,owner,simulated.getId());
        long historic = jdbcTemplate.queryForObject("SELECT id FROM booking WHERE booking_code='UNIFY-HISTORIC'",Long.class);
        var historicalBaseline = operationalSnapshot(session,actual.getId());
        for(String path : new String[]{"/api/v1/reception/training/attendance","/api/v1/coach/training/attendance"})
            assertThat(write(path,HttpMethod.POST,Map.of("bookingId",historic,"status","PRESENT"),path.contains("coach")?coach:reception).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
        assertThat(write("/api/v1/bookings/self-training/attendance",HttpMethod.POST,Map.of("bookingId",historic),reception).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
        assertThat(write("/api/v1/coach/training/results",HttpMethod.POST,Map.of("sessionId",session,"memberId",owner,"notes","Historical bypass attempt","scores",java.util.List.of()),coach).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
        assertThat(operationalSnapshot(session,actual.getId())).isEqualTo(historicalBaseline);
        long classId=jdbcTemplate.queryForObject("SELECT class_id FROM class_session WHERE id=?",Long.class,session);
        assertThat(write("/api/v1/coach/training/plans",HttpMethod.POST,
                Map.of("classId",classId,"memberId",owner,"bookingId",historic,"title","Synthetic plan attempt",
                        "steps",java.util.List.of(Map.of("title","Warmup","durationMinutes",5))),coach).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
        jdbcTemplate.update("INSERT attendance_record(session_id,member_id,booking_id,status,marked_at,marked_by) VALUES (?,?,?,'PRESENT',GETDATE(),(SELECT id FROM user_account WHERE email='lan.reception@example.com'))",session,owner,historic);
        long attendance=jdbcTemplate.queryForObject("SELECT id FROM attendance_record WHERE booking_id=?",Long.class,historic);
        for(String role : new String[]{"coach","reception"})
            assertThat(write("/api/v1/"+role+"/training/attendance/"+attendance,HttpMethod.PUT,
                    Map.of("status","LATE","reason","Synthetic correction attempt"),role.equals("coach")?coach:reception).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM attendance_correction WHERE attendance_record_id=?",Integer.class,attendance)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM attendance_record WHERE id=?",String.class,attendance)).isEqualTo("PRESENT");
        // Profile/flag changes and an email rename cannot launder persisted simulator provenance.
        jdbcTemplate.update("UPDATE user_account SET email='renamed-ops@example.test' WHERE id=?",owner);
        var disabledPolicy = new com.sportify.catalog.service.OperationalMemberPolicy(jdbcTemplate,
                new org.springframework.mock.env.MockEnvironment().withProperty("app.payment.unified-demo.enabled","false"));
        assertThatThrownBy(() -> disabledPolicy.requireRealMember(owner)).hasMessageContaining("Synthetic");
        long realOwner = jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE email='live@example.test'",Long.class);
        disabledPolicy.requireRealMember(realOwner);
        // Cancel the retained historical booking via the official flow; this releases its seat.
        jdbcTemplate.update("UPDATE class_session SET booked_count=1 WHERE id=?",session);
        assertThat(write("/api/v1/bookings/"+historic,HttpMethod.DELETE,null,reception).getStatusCode()).isEqualTo(HttpStatus.OK);
        var booked = write("/api/v1/bookings",HttpMethod.POST,Map.of("sessionId",session,"packageRegistrationId",actual.getId()),real);
        assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long booking = new com.fasterxml.jackson.databind.ObjectMapper().readTree(booked.getBody()).get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT booked_count FROM class_session WHERE id=?",Integer.class,session)).isEqualTo(1);
        assertThat(write("/api/v1/bookings/"+booking,HttpMethod.DELETE,null,real).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbcTemplate.queryForObject("SELECT remaining_sessions FROM sport_package_registration WHERE id=?",Integer.class,actual.getId())).isEqualTo(baseline.get("remaining_sessions"));
        assertPaidMemberImpactWithSimulationDisabled(session, actual, real, reception);
    }

    private void assertPaidMemberImpactWithSimulationDisabled(long session,
            com.sportify.catalog.dto.PackageRegistrationResponse actual, String real, String reception) {
        String control=login("control@example.test"); var controlRegistration=purchase(control);
        var amount=jdbcTemplate.queryForObject("SELECT amount FROM payment WHERE id=?",java.math.BigDecimal.class,controlRegistration.getPaymentId());
        assertThat(write("/api/v1/payments/"+controlRegistration.getPaymentId()+"/confirm",HttpMethod.PUT,
                Map.of("method","BANK_TRANSFER","referenceCode","R03-CONTROL-PAID","amountReceived",amount),reception).getStatusCode()).isEqualTo(HttpStatus.OK);
        var before=operationalSnapshot(session,actual.getId());
        // Deliberately model a legitimate account acquiring legacy simulated history, not a new unified purchase.
        long historical=pending("live@example.test","CASH");
        var store=context.getBean(com.sportify.demoqr.service.SqlQrStore.class);
        store.transaction(() -> {
            var actor=context.getBean(com.sportify.identity.repository.UserRepository.class).findByEmailIgnoreCase("live@example.test").orElseThrow();
            var id=store.insert(historical,actor.getId(),new java.math.BigDecimal("100"),com.sportify.demoqr.service.DemoQrService.Bank.VIETCOMBANK_DEMO,
                    java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-",""),java.util.UUID.randomUUID(),300);
            var s=store.findId(id,actor.getId());var tx=store.insertSettlement(s);
            context.getBean(com.sportify.demoqr.service.DemoPaymentReader.class).settle(historical,actor,"VIETCOMBANK_DEMO",tx.id(),tx.transactionCode());
            store.settle(s);return null;
        });
        var env=(org.springframework.core.env.ConfigurableEnvironment)context.getEnvironment();
        env.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("r03-disabled-impact",
                Map.of("app.payment.unified-demo.enabled","false")));
        try {
            assertThat(context.getBean(com.sportify.demoqr.service.LocalDemoPaymentService.class).isEnabled()).isFalse();
            assertThat(write("/api/v1/bookings",HttpMethod.POST,Map.of("sessionId",session,"packageRegistrationId",actual.getId()),real).getBody()).contains("SYNTHETIC_OPERATION_DENIED");
            assertThat(operationalSnapshot(session,actual.getId())).isEqualTo(before);
            long invoice=jdbcTemplate.queryForObject("SELECT id FROM invoice WHERE payment_id=?",Long.class,actual.getPaymentId());
            assertThat(restTemplate.exchange("/api/v1/invoices/"+invoice,HttpMethod.GET,new HttpEntity<>(authHeaders(real)),String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            var booked=write("/api/v1/bookings",HttpMethod.POST,Map.of("sessionId",session,"packageRegistrationId",controlRegistration.getId()),control);
            assertThat(booked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            long id=jdbcTemplate.queryForObject("SELECT id FROM booking WHERE package_registration_id=? AND status='CONFIRMED'",Long.class,controlRegistration.getId());
            assertThat(write("/api/v1/bookings/"+id,HttpMethod.DELETE,null,control).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(operationalSnapshot(session,actual.getId()).get("real_entitlement_checksum")).isEqualTo(before.get("real_entitlement_checksum"));
        } finally { env.getPropertySources().remove("r03-disabled-impact"); }
    }

    @ParameterizedTest @CsvSource({"0,CARD,PENDING_PAYMENT","1,CARD,CANCELLED","2,CARD,EXPIRED","3,CARD,REPLACED",
            "4,PACKAGE,PENDING_PAYMENT","5,PACKAGE,CANCELLED","6,PACKAGE,EXPIRED","7,PACKAGE,REFUNDED","8,PACKAGE,NO_ACTIVATION"})
    void unsupportedOrInconsistentEntitlementReplayIsDeniedWithoutRepair(int fixtureId,String type,String state) {
        String email="life"+fixtureId+"@unified-demo.invalid",token=login(email);
        long payment=type.equals("CARD")?pending(email,"BANK_TRANSFER"):purchase(token).getPaymentId();
        String path="/api/v1/payments/"+payment+"/demo-confirm";
        assertThat(write(path,HttpMethod.POST,Map.of(),token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(write(path,HttpMethod.POST,Map.of(),token).getStatusCode()).isEqualTo(HttpStatus.OK);
        String table=type.equals("CARD")?"member_card":"sport_package_registration";
        // Real SQL permits these inconsistencies; no constraint/trigger/history is disabled to construct them.
        if(state.equals("NO_ACTIVATION")) jdbcTemplate.update("UPDATE sport_package_registration SET activated_at=NULL WHERE payment_id=?",payment);
        else jdbcTemplate.update("UPDATE "+table+" SET status=? WHERE payment_id=?",state,payment);
        var before=replaySnapshot(payment,table);
        for(int retry=0;retry<2;retry++)
            assertThat(write(path,HttpMethod.POST,Map.of(),token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(replaySnapshot(payment,table)).isEqualTo(before);
        for(String rowTable:new String[]{"invoice","simulated_payment_transaction"})
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM "+rowTable+" WHERE payment_id=?",Integer.class,payment)).isEqualTo(1);
    }

    private java.util.List<Object> replaySnapshot(long payment,String entitlement) {
        return java.util.List.of(jdbcTemplate.queryForList("SELECT * FROM payment WHERE id=?",payment),
                jdbcTemplate.queryForList("SELECT * FROM "+entitlement+" WHERE payment_id=?",payment),
                jdbcTemplate.queryForList("SELECT * FROM invoice WHERE payment_id=?",payment),
                jdbcTemplate.queryForList("SELECT * FROM invoice_line WHERE invoice_id IN (SELECT id FROM invoice WHERE payment_id=?)",payment),
                jdbcTemplate.queryForList("SELECT * FROM activity_log WHERE entity_type='PAYMENT' AND entity_id=? ORDER BY id",payment),
                jdbcTemplate.queryForList("SELECT * FROM simulated_payment_transaction WHERE payment_id=?",payment),
                jdbcTemplate.queryForList("SELECT * FROM simulated_qr_session WHERE payment_id=? ORDER BY created_at_utc",payment));
    }

    @ParameterizedTest @ValueSource(ints={0,1,2,3,4,5,6,7})
    void malformedHistoricalReplayFailsWithoutRepair(int fault) {
        String email="fault"+fault+"@unified-demo.invalid";
        long payment=pending(email,fault==0?"CASH":"BANK_TRANSFER");
        var store=context.getBean(com.sportify.demoqr.service.SqlQrStore.class);
        store.transaction(() -> {
            var actor=context.getBean(com.sportify.identity.repository.UserRepository.class).findByEmailIgnoreCase(email).orElseThrow();
            var id=store.insert(payment,actor.getId(),new java.math.BigDecimal("100"),com.sportify.demoqr.service.DemoQrService.Bank.VIETCOMBANK_DEMO,
                    java.util.UUID.randomUUID().toString().replace("-","")+java.util.UUID.randomUUID().toString().replace("-",""),java.util.UUID.randomUUID(),300);
            var session=store.findId(id,actor.getId());
            // Invalid legacy transaction fixtures are inserted once, never edited around the immutable ledger trigger.
            jdbcTemplate.update("""
                    INSERT simulated_payment_transaction(transaction_code,payment_id,session_id,member_id,amount,currency,bank_code,settlement_type,provider,status,settled_at_utc)
                    VALUES (?,?,?, ?,?,'VND','VIETCOMBANK_DEMO','SIMULATED','SPORTIFY_SIMULATOR',?,SYSUTCDATETIME())
                    ""","SIM-HIST-"+payment,payment,id.toString(),fault==2?jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE email='mai.standard@unified-demo.invalid'",Long.class):actor.getId(),fault==3?99:100,fault==4?"FAILED":"SUCCEEDED");
            long tx=jdbcTemplate.queryForObject("SELECT id FROM simulated_payment_transaction WHERE payment_id=?",Long.class,payment);
            context.getBean(com.sportify.demoqr.service.DemoPaymentReader.class).settle(payment,actor,"VIETCOMBANK_DEMO",tx,"SIM-HIST-"+payment);
            if(fault!=2 && fault!=3 && fault!=4) store.settle(session);
            if(fault==1) jdbcTemplate.update("UPDATE payment SET reference_code='MISSING-TRANSACTION-REFERENCE' WHERE id=?",payment);
            if(fault==5) jdbcTemplate.update("DELETE activity_log WHERE action='PAYMENT_CONFIRMED' AND entity_id=?",payment);
            if(fault==6) jdbcTemplate.update("UPDATE activity_log SET details=JSON_MODIFY(details,'$.simulatedTransactionId',-1) WHERE action='PAYMENT_CONFIRMED' AND entity_id=?",payment);
            if(fault==7) jdbcTemplate.update("UPDATE invoice SET total_amount=99 WHERE payment_id=?",payment);
            return null;
        });
        var before=jdbcTemplate.queryForMap("SELECT p.*,i.invoice_number,i.total_amount FROM payment p JOIN invoice i ON i.payment_id=p.id WHERE p.id=?",payment);
        int audit=jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_log WHERE entity_type='PAYMENT' AND entity_id=?",Integer.class,payment);
        assertThat(confirm(payment,email).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbcTemplate.queryForMap("SELECT p.*,i.invoice_number,i.total_amount FROM payment p JOIN invoice i ON i.payment_id=p.id WHERE p.id=?",payment)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity_log WHERE entity_type='PAYMENT' AND entity_id=?",Integer.class,payment)).isEqualTo(audit);
    }

    @Test void missingTransactionIsRejectedByUnchangedDatabaseConstraints() {
        long payment=pending("fault8@unified-demo.invalid","BANK_TRANSFER");
        assertThat(confirm(payment,"fault8@unified-demo.invalid").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE payment SET simulated_transaction_id=NULL WHERE id=?",payment))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(confirm(payment,"fault8@unified-demo.invalid").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
