package com.sportify.demoqr.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDemoSafetyGuardTest {

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
            "jdbc:sqlserver://localhost:1433;databaseName=sportify_qr_demo;encrypt=true;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;database=sportify_qr_demo",
            "jdbc:sqlserver://localhost:1433/sportify_qr_demo;encrypt=true"
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
}
