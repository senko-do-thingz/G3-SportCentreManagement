package com.sportify.demoqr.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

public class UnifiedDemoSafetyGuardTest {
    public static MockEnvironment canonical() {
        var env = new MockEnvironment();
        env.setActiveProfiles("dev", "unified-demo");
        env.setProperty(UnifiedDemoSafetyGuard.ENABLED, "true");
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true");
        env.setProperty("app.cors.allowed-origins", "http://127.0.0.1:5173");
        env.setProperty(UnifiedDemoSafetyGuard.MEMBER_IDS, "10,11");
        return env;
    }

    private void rejects(MockEnvironment env) {
        assertThatThrownBy(() -> UnifiedDemoSafetyGuard.validate(env)).isInstanceOf(IllegalStateException.class);
        assertThat(UnifiedDemoSafetyGuard.isEnabled(env)).isFalse();
    }

    @Test void defaultDoesNotEnableAnything() {
        var env = new MockEnvironment();
        UnifiedDemoSafetyGuard.validate(env);
        assertThat(UnifiedDemoSafetyGuard.isEnabled(env)).isFalse();
        assertThat(UnifiedDemoSafetyGuard.isEnabled(null)).isFalse();
    }

    @Test void permitsExplicitCanonicalContractWithoutConnectingToDatabase() {
        assertThat(UnifiedDemoSafetyGuard.isEnabled(canonical())).isTrue();
    }

    @Test void permitsSeparateDisposableFixtureOnlyWithBothTestControls() {
        var env = canonical();
        env.setActiveProfiles("dev", "unified-demo", "unified-demo-test");
        env.setProperty(UnifiedDemoSafetyGuard.TEST_ENABLED, "true");
        env.setProperty("spring.datasource.url", "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_unify_test_20261104120000_qr;encrypt=false;trustServerCertificate=true");
        assertThat(UnifiedDemoSafetyGuard.isEnabled(env)).isTrue();
        env.setProperty("spring.datasource.url", canonical().getProperty("spring.datasource.url"));
        rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"prod", "production", "staging", "local-demo", "qr-simulator", "demo", "test"})
    void rejectsOtherProfilesEvenWithOptIn(String profile) {
        var env = canonical();
        env.setActiveProfiles("dev", "unified-demo", profile);
        rejects(env);
    }

    @Test void requiresProfileAndLiteralTrueFlag() {
        var env = canonical();
        env.setActiveProfiles("dev");
        rejects(env);
        env = canonical();
        env.setProperty(UnifiedDemoSafetyGuard.ENABLED, "false");
        rejects(env);
        env.setProperty(UnifiedDemoSafetyGuard.ENABLED, "yes");
        rejects(env);
    }

    @Test void rejectsPartialTestControls() {
        var env = canonical();
        env.setProperty(UnifiedDemoSafetyGuard.TEST_ENABLED, "true");
        rejects(env);
        env = canonical();
        env.setActiveProfiles("dev", "unified-demo", "unified-demo-test");
        rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0.0.0.0", "localhost", "192.168.1.2", "::", "127.evil.test"})
    void rejectsUnsafeListener(String address) {
        var env = canonical(); env.setProperty("server.address", address); rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {
            "jdbc:sqlserver://remote:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://localhost:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:0;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:99999;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_demo;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_qr_demo;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify_unify_test_20261104120000;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://evil@127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1\\INSTANCE:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433/sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName={sportify};encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;databaseName=sportify;encrypt=false;trustServerCertificate=true",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true;failoverPartner=remote",
            "jdbc:sqlserver://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true;serverName=remote",
            "jdbc:other://127.0.0.1:1433;databaseName=sportify;encrypt=false;trustServerCertificate=true"
    })
    void rejectsAmbiguousRemoteAndWrongDatabaseUrls(String url) {
        var env = canonical(); env.setProperty("spring.datasource.url", url); rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"spring.flyway.url", "spring.flyway.user", "spring.flyway.password",
            "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.jndi-name", "spring.datasource.type", "spring.datasource.hikari.catalog",
            "spring.datasource.hikari.schema", "spring.datasource.hikari.connection-init-sql",
            "spring.datasource.hikari.data-source-properties.databaseName", "spring.datasource.hikari.data-source-properties.serverName",
            "spring.datasource.hikari.data-source-properties.failoverPartner", "spring.datasource.hikari.data-source-properties.URL",
            "spring.flyway.jdbc-properties.serverName", "spring.flyway.schemas[0]", "spring.flyway.init-sqls[0]",
            "management.server.port", "management.server.address", "spring.flyway.target", "spring.flyway.table",
            "spring.flyway.skip-executing-migrations", "spring.flyway.skip-default-resolvers",
            "spring.flyway.sql-migration-prefix", "spring.flyway.placeholders.database",
            "spring.flyway.ignore-migration-patterns", "spring.flyway.ignore-migration-patterns[0]"})
    void rejectsEveryAlternateTargetAndInitializationMechanism(String property) {
        var env = canonical(); env.setProperty(property, "unsafe"); rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"spring.flyway.clean-disabled", "spring.flyway.validate-on-migrate", "spring.flyway.enabled"})
    void cannotDisableFlywaySafety(String property) {
        var env = canonical(); env.setProperty(property, "false"); rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"spring.flyway.out-of-order", "spring.flyway.baseline-on-migrate",
            "app.payment.simulator.enabled", "app.payment.local-demo.enabled", "app.training.attendance-backfill.enabled"})
    void cannotActivateUnsafeHistoryOrOtherCapabilities(String property) {
        var env = canonical(); env.setProperty(property, "true"); rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"", "*", "https://remote.test", "http://127.0.0.1:5173/path",
            "http://user@127.0.0.1:5173", "http://127.0.0.1:5173,https://remote.test"})
    void rejectsNonLoopbackCors(String origin) {
        var env = canonical(); env.setProperty("app.cors.allowed-origins", origin); rejects(env);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-1", "10,10", "10,invalid", "999999999999999999999999"})
    void requiresValidExplicitSyntheticMemberAllowlist(String members) {
        var env = canonical(); env.setProperty(UnifiedDemoSafetyGuard.MEMBER_IDS, members); rejects(env);
    }

    @Test void indexedFlywayLocationCannotOverrideReviewedCatalog() {
        var env = canonical(); env.setProperty("spring.flyway.locations[0]", "classpath:db/demo"); rejects(env);
    }

    @Test void rejectsBeforeAnyDatabaseSingletonCanInitialize() {
        var connected = new AtomicBoolean();
        new ApplicationContextRunner().withUserConfiguration(UnifiedDemoSafetyGuard.class)
                .withPropertyValues("spring.profiles.active=prod,unified-demo", "app.payment.unified-demo.enabled=true")
                .withBean("databaseProbe", Object.class, () -> { connected.set(true); return new Object(); })
                .run(context -> { assertThat(context).hasFailed(); assertThat(connected).isFalse(); });
    }
}
