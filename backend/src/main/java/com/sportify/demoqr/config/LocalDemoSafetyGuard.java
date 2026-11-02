package com.sportify.demoqr.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict safety guard for local demo mode.
 * Enforces non-production profile, loopback server bind (127.0.0.1/localhost),
 * and throwaway database allowlist before any beans or database operations run.
 */
@Configuration(proxyBeanMethods = false)
public class LocalDemoSafetyGuard {

    private static final Set<String> ALLOWED_DEMO_DATABASES = Set.of(
            "sportify_qr_demo",
            "sportify_demo",
            "sportify_test",
            "testdb"
    );

    private static final Pattern DB_NAME_PATTERN = Pattern.compile(
            ";(?:databaseName|database)=([a-zA-Z0-9_-]+)",
            Pattern.CASE_INSENSITIVE
    );

    @Bean
    static BeanFactoryPostProcessor localDemoSafetyPostProcessor(Environment environment) {
        return beanFactory -> validateLocalDemoSafety(environment);
    }

    public static void validateLocalDemoSafety(Environment environment) {
        if (environment == null) {
            return;
        }

        boolean profileActive = environment.matchesProfiles("local-demo");
        String flagValue = environment.getProperty("app.payment.local-demo.enabled");
        if (flagValue == null || flagValue.isBlank()) {
            flagValue = environment.getProperty("PAYMENT_LOCAL_DEMO_ENABLED");
        }
        boolean flagEnabled = "true".equalsIgnoreCase(flagValue);

        if (!profileActive && !flagEnabled) {
            return;
        }

        // 1. Profile invariants
        if (environment.matchesProfiles("prod") || environment.matchesProfiles("production")) {
            throw new IllegalStateException("Local demo mode must never run in a production profile.");
        }

        if (flagEnabled && !profileActive) {
            throw new IllegalStateException("Local demo mode flag is enabled but local-demo profile is not active. "
                    + "Activate the local-demo profile explicitly.");
        }

        // 2. Server loopback bind invariants
        String serverAddress = environment.getProperty("server.address");
        if (serverAddress == null || serverAddress.isBlank()) {
            serverAddress = environment.getProperty("SERVER_ADDRESS");
        }

        if (serverAddress == null || serverAddress.isBlank()) {
            throw new IllegalStateException("Local demo mode requires server to be bound strictly to loopback. "
                    + "Configure SERVER_ADDRESS=127.0.0.1");
        }

        String normalizedAddress = serverAddress.trim().toLowerCase(Locale.ROOT);
        boolean isLoopback = normalizedAddress.equals("127.0.0.1")
                || normalizedAddress.equals("localhost")
                || normalizedAddress.equals("::1")
                || normalizedAddress.startsWith("127.");

        if (!isLoopback) {
            throw new IllegalStateException("Local demo mode requires server to be bound strictly to loopback (127.0.0.1). "
                    + "Disallowed server address: " + serverAddress);
        }

        // 3. Database allowlist invariants
        String dbUrl = environment.getProperty("spring.datasource.url");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("DB_URL");
        }

        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException("Local demo mode requires an explicit database URL.");
        }

        String dbName = extractDatabaseName(dbUrl);
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalStateException("Could not determine database name from JDBC URL: " + dbUrl);
        }

        String normalizedDb = dbName.toLowerCase(Locale.ROOT);
        if (normalizedDb.equals("sportify")) {
            throw new IllegalStateException("Local demo mode strictly forbids running against the default 'sportify' database. "
                    + "Configure an approved throwaway database such as 'sportify_qr_demo'.");
        }

        if (!ALLOWED_DEMO_DATABASES.contains(normalizedDb) && !normalizedDb.startsWith("sportify_qr04_test")) {
            throw new IllegalStateException("Local demo mode rejected database '" + dbName
                    + "'. Only approved throwaway databases (" + ALLOWED_DEMO_DATABASES + ") are permitted.");
        }
    }

    public static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        Matcher matcher = DB_NAME_PATTERN.matcher(jdbcUrl);
        if (matcher.find()) {
            return matcher.group(1);
        }
        // Handle slash-separated database URLs like jdbc:sqlserver://localhost:1433/database_name
        int slashIndex = jdbcUrl.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < jdbcUrl.length() - 1) {
            String remainder = jdbcUrl.substring(slashIndex + 1);
            int semicolon = remainder.indexOf(';');
            int question = remainder.indexOf('?');
            int end = remainder.length();
            if (semicolon >= 0) end = Math.min(end, semicolon);
            if (question >= 0) end = Math.min(end, question);
            String candidate = remainder.substring(0, end).trim();
            if (!candidate.isBlank() && !candidate.contains(":")) {
                return candidate;
            }
        }
        return null;
    }
}
