package com.sportify.demoqr;

import com.sportify.support.DisposableUnifyDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
        for (String local : new String[]{"rollback", "unsupported", "ordinary"}) {
            jdbc.update("INSERT user_account(email,password_hash,full_name,role_id,status) SELECT ?,password_hash,'Synthetic unify fixture',role_id,'ACTIVE' FROM user_account WHERE email='an.gold@unified-demo.invalid'", local + "@unified-demo.invalid");
            jdbc.update("INSERT member_profile(user_id,member_code) SELECT id,? FROM user_account WHERE email=?", "UNIFY-" + local, local + "@unified-demo.invalid");
        }
        String ids = String.join(",", jdbc.queryForList("SELECT CONVERT(varchar(20),id) FROM user_account WHERE email LIKE '%@unified-demo.invalid' ORDER BY id", String.class));
        jdbc.update("UPDATE user_account SET email='ordinary@example.test' WHERE email='ordinary@unified-demo.invalid'");
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
}
