package com.sportify.demoqr;

import com.sportify.demoqr.service.DemoQrService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Real HTTP, JWT authentication, Flyway and SQL Server. Only a new isolated test database is allowed. */
@Tag("integration")
@EnabledIf(value = "databaseAvailable", disabledReason = "Requires an isolated local QR-04 SQL database or CI Docker SQL Server")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(value = "qr-demo", inheritProfiles = false)
@TestPropertySource(properties = {"app.demo.qr.enabled=true", "app.demo.qr.public-base-url=https://demo.example.test/",
        "app.demo.qr.allowed-origins=https://demo.example.test", "app.payment.bank-transfer.account-name=Demo only",
        "app.payment.bank-transfer.account-number=NOT_AVAILABLE", "app.payment.bank-transfer.bank=Demo only",
        "application.security.jwt.secret-key=cXJvNC1pc29sYXRlZC10ZXN0LWtleS1uby1yZWFsLWNyZWRlbnRpYWxz", "spring.jpa.show-sql=false"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(DemoQrDatabaseIntegrationTest.ClockSetup.class)
class DemoQrDatabaseIntegrationTest {
    private static MSSQLServerContainer<?> container;
    private static boolean localDatabase;
    private static final String PREFIX = "/api/v1/demo/qr/sessions";
    private static final String PASSWORD = "Qr04IsolatedTest2026!";
    @Autowired JdbcTemplate jdbc;
    @Autowired TestRestTemplate http;
    @Autowired PasswordEncoder encoder;
    @Autowired ControlledClock clock;
    private long paymentId;
    private String ownerToken;
    private String otherToken;

    static boolean databaseAvailable() {
        String local = System.getenv("QR04_TEST_DB_URL");
        return (local != null && !local.isBlank()) || org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String local = System.getenv("QR04_TEST_DB_URL");
        localDatabase = local != null && !local.isBlank();
        if (localDatabase) {
            if (!local.matches(".*;databaseName=sportify_qr04_test_[0-9]{14}(;.*)?")) {
                throw new IllegalArgumentException("QR04_TEST_DB_URL must select a new isolated QR-04 test database");
            }
            registry.add("spring.datasource.url", () -> local);
            registry.add("spring.datasource.username", () -> System.getenv("QR04_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("QR04_TEST_DB_PASSWORD"));
        } else {
            container = new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense().withPassword("Qr04ContainerTest2026!");
            container.start();
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
    }

    @BeforeAll
    void seedIsolatedFixture() {
        String database = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        assertThat(localDatabase ? database.startsWith("sportify_qr04_test_") : container.isRunning()).isTrue();
        long owner = member("owner@qr04.example.test", "QR04-OWNER", "QR04 Demo Owner");
        member("other@qr04.example.test", "QR04-OTHER", "QR04 Other Owner");
        var existing = jdbc.queryForList("SELECT id FROM payment WHERE payment_code='QR04-TEST-52'", Long.class);
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO payment (payment_code,member_id,amount,payment_method,payment_status,version) VALUES ('QR04-TEST-52',?,810000,'BANK_TRANSFER','PENDING',0)", owner);
        }
        paymentId = jdbc.queryForObject("SELECT id FROM payment WHERE payment_code='QR04-TEST-52'", Long.class);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE payment_code='QR04-TEST-53'", Integer.class) == 0) {
            jdbc.update("INSERT INTO payment (payment_code,member_id,amount,payment_method,payment_status,version) VALUES ('QR04-TEST-53',?,510000,'BANK_TRANSFER','PENDING',0)", owner);
        }
        ownerToken = login("owner@qr04.example.test");
        otherToken = login("other@qr04.example.test");
    }

    private long member(String email, String code, String name) {
        var existing = jdbc.queryForList("SELECT id FROM user_account WHERE email=?", Long.class, email);
        if (!existing.isEmpty()) return existing.getFirst();
        Long id = jdbc.queryForObject("INSERT INTO user_account(email,password_hash,full_name,role_id,status,version) OUTPUT INSERTED.id VALUES (?,?,?,(SELECT id FROM role WHERE code='MEMBER'),'ACTIVE',0)", Long.class, email, encoder.encode(PASSWORD), name);
        jdbc.update("INSERT INTO member_profile(user_id,member_code,current_level,joined_on) VALUES (?,?,'BEGINNER',CAST(SYSDATETIME() AS DATE))", id, code);
        return id;
    }

    private String login(String email) {
        var response = http.postForEntity("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    private <T> ResponseEntity<T> post(String path, Object body, String token, Class<T> type) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), type);
    }

    private Map<String, String> financialFingerprint() throws Exception {
        Map<String, String> result = new TreeMap<>();
        List<String> tables = List.of("payment", "invoice", "invoice_line", "refund_request", "cash_event", "cash_capture_ticket", "cash_coverage");
        for (String table : tables) {
            var rows = jdbc.queryForList("SELECT * FROM [" + table + "]");
            var canonical = rows.stream().map(row -> new TreeMap<>(row).toString()).sorted().toList().toString();
            result.put(table, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))));
        }
        return result;
    }

    @Test
    void successfulLiveHttpConfirmationChangesOnlyDemoStateAndEveryFinancialTableIsIdentical() throws Exception {
        var before = financialFingerprint();
        var created = post(PREFIX, Map.of("paymentId", paymentId, "bankCode", "MB_BANK_DEMO", "amount", 1, "ownerId", 999), ownerToken, DemoQrService.Created.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = created.getBody().confirmationUrl().split("#token=")[1];
        assertThat(post(PREFIX + "/preview", Map.of("token", token), ownerToken, DemoQrService.View.class).getBody().state()).isEqualTo(DemoQrService.State.PENDING);
        for (int i = 0; i < 2; i++) assertThat(post(PREFIX + "/confirm", Map.of("token", token), ownerToken, DemoQrService.View.class).getBody().state()).isEqualTo(DemoQrService.State.CONFIRMED);
        assertThat(financialFingerprint()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT payment_status FROM payment WHERE id=?", String.class, paymentId)).isEqualTo("PENDING");
    }

    @Test
    void crossOwnerAndCancellationAreEnforcedAcrossRealHttpRequestsWithNoFinancialWrites() throws Exception {
        var before = financialFingerprint();
        var created = post(PREFIX, Map.of("paymentId", paymentId, "bankCode", "BIDV_DEMO"), ownerToken, DemoQrService.Created.class).getBody();
        String token = created.confirmationUrl().split("#token=")[1];
        assertThat(post(PREFIX + "/confirm", Map.of("token", token), otherToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post(PREFIX + "/" + created.session().id() + "/cancel", null, ownerToken, DemoQrService.View.class).getBody().state()).isEqualTo(DemoQrService.State.CANCELED);
        assertThat(post(PREFIX + "/confirm", Map.of("token", token), ownerToken, Map.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(financialFingerprint()).isEqualTo(before);
    }

    @Test
    void exactExpiryOverRealHttpLeavesAllFinancialRowsIdentical() throws Exception {
        var before = financialFingerprint();
        var created = post(PREFIX, Map.of("paymentId", paymentId, "bankCode", "TECHCOMBANK_DEMO"), ownerToken, DemoQrService.Created.class).getBody();
        String token = created.confirmationUrl().split("#token=")[1];
        clock.now.set(created.session().expiresAt());
        var rejected = post(PREFIX + "/confirm", Map.of("token", token), ownerToken, Map.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().get("code")).isEqualTo("DEMO_EXPIRED");
        assertThat(post(PREFIX + "/preview", Map.of("token", token), ownerToken, DemoQrService.View.class).getBody().state()).isEqualTo(DemoQrService.State.EXPIRED);
        assertThat(financialFingerprint()).isEqualTo(before);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockSetup {
        @Bean @Primary ControlledClock qr04TestClock() { return new ControlledClock(); }
    }

    static final class ControlledClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-29T08:00:00Z"));
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }

    @AfterAll
    static void closeContainer() { if (container != null) container.stop(); }
}
