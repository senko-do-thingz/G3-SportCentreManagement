package com.sportify.demoqr.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDemoSafetyGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://127.evil.example:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://127.0.0.999:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo.prod",
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr04_test_production",
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;failoverPartner=remote.example",
            "jdbc:sqlserver://[::1]@remote.example;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://localhost:1433;databaseName={sportify}",
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo; databaseName =sportify",
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo; serverName =remote.example",
            "jdbc:other://localhost:1433;databaseName=sportify_qr_demo"
    })
    void rejectsDisguisedOrRedirectedDatabaseTargets(String url) {
        MockEnvironment env = safeEnvironment();
        env.setProperty("spring.datasource.url", url);
        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.hikari.jdbc-url", "spring.flyway.url"})
    void rejectsAlternateConnectionUrl(String property) {
        MockEnvironment env = safeEnvironment();
        env.setProperty(property, "jdbc:sqlserver://localhost:1433;databaseName=sportify");
        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("alternate database URL");
    }

    private MockEnvironment safeEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "demo", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo");
        return env;
    }

    @Test
    void doesNothingWhenLocalDemoNeitherActiveNorEnabled() {
        MockEnvironment env = new MockEnvironment();
        // Should not throw
        LocalDemoSafetyGuard.validateLocalDemoSafety(env);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void rejectsProductionProfileWhenLocalDemoActive(String prodProfile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(prodProfile, "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;");

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production profile");
    }

    @Test
    void rejectsFlagEnabledWithoutLocalDemoProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.setProperty("PAYMENT_LOCAL_DEMO_ENABLED", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;");

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Activate the local-demo profile explicitly");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0.0.0", "192.168.1.100", "example.com", "10.0.0.5"})
    void rejectsPublicOrNonLoopbackServerBind(String host) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", host);
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;");

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly to loopback");
    }

    @Test
    void rejectsMissingServerAddress() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;");

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly to loopback");
    }

    @Test
    void rejectsDefaultSportifyDatabase() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=true;");

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly forbids running against the default 'sportify' database");
    }

    @Test
    void rejectsUnrecognizedDatabase() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://localhost:1433;databaseName=production_crm;");

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rejected database");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://192.168.1.50:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://remote.corp.com:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://azure.database.windows.net:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://0.0.0.0:1433;databaseName=sportify_qr_demo"
    })
    void rejectsRemoteDatabaseHostEvenWithApprovedDatabaseName(String remoteDbUrl) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", remoteDbUrl);

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strictly loopback");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;encrypt=true;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;database=sportify_qr_demo",
            "jdbc:sqlserver://localhost:1433/sportify_qr_demo;encrypt=true",
            "jdbc:sqlserver://[::1]:1433;databaseName=sportify_qr_demo"
    })
    void passesWithApprovedConfiguration(String dbUrl) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "demo", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", dbUrl);

        // Should not throw
        LocalDemoSafetyGuard.validateLocalDemoSafety(env);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_qr_demo;database=sportify_prod",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_qr_demo;databaseName=sportify_prod",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_qr_demo;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://localhost:1433/sportify_qr_demo;databaseName=sportify"
    })
    void rejectsAmbiguousOrConflictingDatabaseParameters(String ambiguousUrl) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", ambiguousUrl);

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous JDBC URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:sqlserver://localhost:1433;serverName=remote.corp.com;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://remote.corp.com//localhost:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://evil.com@localhost:1433;databaseName=sportify_qr_demo",
            "jdbc:sqlserver://localhost:1433,remote.corp.com:1433;databaseName=sportify_qr_demo"
    })
    void rejectsAmbiguousDatabaseAuthorityOrHostOverrides(String ambiguousHostUrl) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "local-demo");
        env.setProperty("app.payment.local-demo.enabled", "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", ambiguousHostUrl);

        assertThatThrownBy(() -> LocalDemoSafetyGuard.validateLocalDemoSafety(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ambiguous JDBC URL");
    }

    @Test
    void extractsDatabaseNameCorrectly() {
        assertThat(LocalDemoSafetyGuard.extractDatabaseName("jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;encrypt=true"))
                .isEqualTo("sportify_qr_demo");
        assertThat(LocalDemoSafetyGuard.extractDatabaseName("jdbc:sqlserver://localhost:1433;database=sportify_demo;"))
                .isEqualTo("sportify_demo");
        assertThat(LocalDemoSafetyGuard.extractDatabaseName("jdbc:sqlserver://localhost:1433/sportify_test?encrypt=true"))
                .isEqualTo("sportify_test");
        assertThat(LocalDemoSafetyGuard.extractDatabaseName(null)).isNull();
    }

    @Test
    void extractsDatabaseHostCorrectly() {
        assertThat(LocalDemoSafetyGuard.extractDatabaseHost("jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;encrypt=true"))
                .isEqualTo("localhost");
        assertThat(LocalDemoSafetyGuard.extractDatabaseHost("jdbc:sqlserver://127.0.0.1:1433;database=sportify_demo;"))
                .isEqualTo("127.0.0.1");
        assertThat(LocalDemoSafetyGuard.extractDatabaseHost("jdbc:sqlserver://[::1]:1433;databaseName=sportify_qr_demo"))
                .isEqualTo("::1");
        assertThat(LocalDemoSafetyGuard.extractDatabaseHost("jdbc:sqlserver://localhost\\SQLEXPRESS;databaseName=sportify_qr_demo"))
                .isEqualTo("localhost");
        assertThat(LocalDemoSafetyGuard.extractDatabaseHost("jdbc:sqlserver://remote.corp.com:1433/sportify_qr_demo"))
                .isEqualTo("remote.corp.com");
        assertThat(LocalDemoSafetyGuard.extractDatabaseHost(null)).isNull();
    }
}
