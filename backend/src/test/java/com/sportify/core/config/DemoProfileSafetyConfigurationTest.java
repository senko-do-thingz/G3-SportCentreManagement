package com.sportify.core.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DemoProfileSafetyConfigurationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FlywayProperties.class)
    static class PropertiesConfiguration {
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod,demo", "demo,prod", "dev,prod,demo"})
    void conflictingProfilesFailBeforeFlywayInitializerIsEvenCreated(String profiles) {
        var created = new AtomicInteger();
        var migrated = new AtomicInteger();
        runner(profiles, created, migrated).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining(
                    "The prod and demo profiles must never be active together. "
                            + "Demo accounts require an isolated demonstration database.");
            assertThat(created).hasValue(0);
            assertThat(migrated).hasValue(0);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dev", "prod", "test"})
    void ordinaryProfilesCannotDiscoverDemoMigration(String profiles) {
        var created = new AtomicInteger();
        var migrated = new AtomicInteger();
        runner(profiles, created, migrated).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(FlywayProperties.class);
            assertThat(properties.getLocations()).containsExactly("classpath:db/migration/identity", "classpath:db/migration/catalog");
            assertThat(properties.isOutOfOrder()).isFalse();
            assertThat(discoveredScripts(properties)).doesNotContain("V28__seed_demo_accounts.sql", "V29__seed_demo_classes_and_sessions.sql");
            assertThat(created).hasValue(1);
            assertThat(migrated).hasValue(1);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "dev,demo"})
    void explicitDemoProfileAddsTheVersionedSeedAndRetainsSchemaLocations(String profiles) {
        var created = new AtomicInteger();
        var migrated = new AtomicInteger();
        runner(profiles, created, migrated).run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(FlywayProperties.class);
            assertThat(properties.getLocations()).containsExactly("classpath:db/migration/identity",
                    "classpath:db/migration/catalog", "classpath:db/demo");
            assertThat(properties.isOutOfOrder()).isFalse();
            assertThat(discoveredScripts(properties)).contains("V27__refund_request_payment_link.sql", "V28__seed_demo_accounts.sql",
                    "V29__seed_demo_classes_and_sessions.sql");
            assertThat(migrated).hasValue(1);
        });
    }

    private ApplicationContextRunner runner(String profiles, AtomicInteger created, AtomicInteger migrated) {
        var runner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(DemoProfileSafetyConfiguration.class, PropertiesConfiguration.class)
                .withBean("flywayInitializer", FlywayMigrationInitializer.class, () -> {
                    created.incrementAndGet();
                    return new FlywayMigrationInitializer(mock(Flyway.class), flyway -> migrated.incrementAndGet());
                });
        return profiles.isEmpty() ? runner : runner.withPropertyValues("spring.profiles.active=" + profiles);
    }

    private java.util.List<String> discoveredScripts(FlywayProperties properties) throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var result = new java.util.ArrayList<String>();
        for (String location : properties.getLocations()) {
            Arrays.stream(resolver.getResources(location.replace("classpath:", "classpath*:") + "/V*__*.sql"))
                    .forEach(resource -> result.add(resource.getFilename()));
        }
        return result;
    }
}
