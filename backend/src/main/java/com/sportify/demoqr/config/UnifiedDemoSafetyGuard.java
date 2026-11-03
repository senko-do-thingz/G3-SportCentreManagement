package com.sportify.demoqr.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Separate, opt-in canonical local demonstration boundary. Never relaxes the legacy guard. */
@Configuration(proxyBeanMethods = false)
public class UnifiedDemoSafetyGuard {
    public static final String ENABLED = "app.payment.unified-demo.enabled";
    public static final String TEST_ENABLED = "app.payment.unified-demo.test-enabled";
    public static final String MEMBER_IDS = "app.payment.unified-demo.member-ids";
    private static final String LOCATIONS = "classpath:db/migration/identity,classpath:db/migration/catalog";
    private static final Pattern URL = Pattern.compile(
            "jdbc:sqlserver://(127\\.0\\.0\\.1|\\[::1\\]):([0-9]{1,5});databaseName=([a-z0-9_]+)"
            + ";encrypt=(true|false);trustServerCertificate=(true|false)");

    @Bean
    static BeanFactoryPostProcessor unifiedDemoSafetyPostProcessor(Environment environment) {
        return beanFactory -> validate(environment);
    }

    public static boolean requested(Environment env) {
        return env != null && (env.matchesProfiles("unified-demo", "unified-demo-test")
                || configured(env, ENABLED) || configured(env, TEST_ENABLED));
    }

    private static boolean configured(Environment env, String property) {
        String value = env.getProperty(property);
        return value != null && !"false".equalsIgnoreCase(value);
    }

    public static boolean isEnabled(Environment env) {
        if (!requested(env)) return false;
        try {
            validate(env);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static void validate(Environment env) {
        if (!requested(env)) return;
        require(env.matchesProfiles("dev") && env.matchesProfiles("unified-demo")
                && "true".equals(env.getProperty(ENABLED)), "Explicit dev,unified-demo profile and opt-in required");
        boolean test = env.matchesProfiles("unified-demo-test");
        require((test ? "true" : "false").equals(env.getProperty(TEST_ENABLED, "false")),
                "Test profile and test opt-in must match");
        Set<String> allowed = test ? Set.of("dev", "unified-demo", "unified-demo-test", "demo")
                : Set.of("dev", "unified-demo");
        require(Arrays.stream(env.getActiveProfiles()).allMatch(allowed::contains),
                "Production, legacy simulator and unknown profiles are forbidden");
        Set<String> flywayOptions = Set.of("enabled", "locations", "cleandisabled", "outoforder",
                "validateonmigrate", "baselineonmigrate", "ignoremigrationpatterns");
        Binder.get(env).bind("spring.flyway", Bindable.mapOf(String.class, Object.class)).orElseGet(java.util.Map::of)
                .keySet().forEach(key -> require(flywayOptions.contains(key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT)),
                        "Unreviewed Flyway option forbidden"));
        require(List.of("127.0.0.1", "::1").contains(env.getProperty("server.address", "")),
                "Explicit numeric loopback HTTP bind required");
        for (String property : List.of("management.server.address", "management.server.port",
                "spring.datasource.jndi-name", "spring.datasource.type", "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.data-source-class-name", "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.datasource.hikari.data-source-jndi", "spring.datasource.hikari.catalog",
                "spring.datasource.hikari.schema", "spring.datasource.hikari.connection-init-sql",
                "spring.flyway.url", "spring.flyway.user", "spring.flyway.password",
                "spring.flyway.schemas", "spring.flyway.default-schema", "spring.flyway.table",
                "spring.flyway.init-sqls", "spring.flyway.target")) {
            require(env.getProperty(property) == null, "Alternate connection or migration option forbidden: " + property);
        }
        for (String prefix : List.of("spring.datasource.hikari.data-source-properties", "spring.flyway.jdbc-properties")) {
            require(Binder.get(env).bind(prefix, Bindable.mapOf(String.class, String.class))
                    .orElseGet(java.util.Map::of).isEmpty(), "Alternate JDBC properties forbidden");
        }
        String url = env.getProperty("spring.datasource.url", "");
        var matcher = URL.matcher(url);
        require(matcher.matches(), "Use the exact numeric loopback SQL Server URL contract");
        int port = Integer.parseInt(matcher.group(2));
        require(port > 0 && port <= 65535, "Invalid SQL Server port");
        String database = matcher.group(3);
        require(test ? database.matches("sportify_unify_test_[0-9]{14}(?:_[a-z0-9]+)?") : database.equals("sportify"),
                "Database does not match canonical or disposable test mode");
        List<String> locations = Binder.get(env).bind("spring.flyway.locations", Bindable.listOf(String.class))
                .orElse(List.of(LOCATIONS.split(",")));
        require(locations.equals(List.of((LOCATIONS + (test && env.matchesProfiles("demo") ? ",classpath:db/demo" : "")).split(","))),
                "Only the reviewed migration locations are permitted");
        for (String prefix : List.of("spring.flyway.schemas", "spring.flyway.init-sqls", "spring.flyway.ignore-migration-patterns")) {
            require(Binder.get(env).bind(prefix, Bindable.listOf(String.class)).orElseGet(List::of).isEmpty(),
                    "Alternate migration options forbidden");
        }
        for (String property : List.of("spring.flyway.enabled", "spring.flyway.clean-disabled", "spring.flyway.validate-on-migrate")) {
            require("true".equals(env.getProperty(property, "true")), "Required Flyway safety option: " + property);
        }
        for (String property : List.of("spring.flyway.out-of-order", "spring.flyway.baseline-on-migrate")) {
            require("false".equals(env.getProperty(property, "false")), "Unsafe Flyway option: " + property);
        }
        require("validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto", "validate")), "Hibernate must validate only");
        for (String property : List.of("app.payment.local-demo.enabled", "app.payment.simulator.enabled",
                "app.training.attendance-backfill.enabled")) {
            require("false".equalsIgnoreCase(env.getProperty(property, "false")),
                    "Legacy simulation and attendance backfill require literal false");
        }
        require("none".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy", "none")),
                "Forwarded listener configuration forbidden");
        String origins = env.getProperty("app.cors.allowed-origins", "");
        require(!origins.isBlank(), "Explicit loopback frontend origins required");
        for (String origin : origins.split(",", -1)) {
            URI uri;
            try { uri = URI.create(origin); } catch (IllegalArgumentException ex) { throw failure("Invalid frontend origin"); }
            require("http".equals(uri.getScheme()) && List.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost())
                    && uri.getUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && "".equals(uri.getRawPath()) && uri.getPort() > 0 && uri.getPort() <= 65535,
                    "Only explicit loopback HTTP frontend origins permitted");
        }
        memberIds(env);
    }

    public static Set<Long> memberIds(Environment env) {
        String configured = env.getProperty(MEMBER_IDS, "");
        require(configured.matches("[1-9][0-9]*(?:,[1-9][0-9]*)*"), "Explicit synthetic Member IDs required");
        Set<Long> ids = new HashSet<>();
        try {
            for (String id : configured.split(",")) require(ids.add(Long.parseLong(id)), "Duplicate synthetic Member ID");
        } catch (NumberFormatException ex) { throw failure("Invalid synthetic Member ID"); }
        return Set.copyOf(ids);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw failure(message);
    }

    private static IllegalStateException failure(String message) {
        return new IllegalStateException("Unified demo rejected: " + message);
    }
}
