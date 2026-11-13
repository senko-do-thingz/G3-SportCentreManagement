package com.sportify.demoqr.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict safety guard for local demo mode.
 * Enforces non-production profile, loopback server bind (127.0.0.1/localhost),
 * and loopback database checks before any beans or database operations run. The normal local
 * development database is permitted only with the explicit local demo flag enabled.
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
            "[;?&]\\s*(?:databaseName|database)\\s*=([^;?&]*)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SERVER_NAME_PATTERN = Pattern.compile(
            "[;?&]\\s*(?:serverName|server|host)\\s*=([^;?&]+)",
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

        String flagValue = environment.getProperty("app.payment.local-demo.enabled");
        if (flagValue == null || flagValue.isBlank()) {
            flagValue = environment.getProperty("PAYMENT_LOCAL_DEMO_ENABLED");
        }
        boolean flagEnabled = "true".equalsIgnoreCase(flagValue);
        boolean profileActive = environment.matchesProfiles("local-demo")
                || environment.matchesProfiles("dev") && flagEnabled;

        if (!profileActive && !flagEnabled) {
            return;
        }

        // 1. Profile invariants
        if (environment.matchesProfiles("prod") || environment.matchesProfiles("production")) {
            throw new IllegalStateException("Local demo mode must never run in a production profile.");
        }

        if (flagEnabled && !profileActive) {
            throw new IllegalStateException("Local demo mode flag is enabled outside an allowed local profile.");
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
        boolean isLoopback = isLoopbackHost(normalizedAddress);

        if (!isLoopback) {
            throw new IllegalStateException("Local demo mode requires server to be bound strictly to loopback (127.0.0.1). "
                    + "Disallowed server address: " + serverAddress);
        }

        // 3. Database allowlist and host loopback invariants
        String dbUrl = environment.getProperty("spring.datasource.url");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = environment.getProperty("DB_URL");
        }

        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalStateException("Local demo mode requires an explicit database URL.");
        }

        // Reject alternate connection targets before Flyway or the pool can open a connection.
        for (String property : List.of("spring.datasource.hikari.jdbc-url", "spring.flyway.url")) {
            String override = environment.getProperty(property);
            if (override != null && !override.isBlank() && !override.equals(dbUrl)) {
                throw new IllegalStateException("Local demo mode forbids an alternate database URL: " + property);
            }
        }
        for (String property : List.of("serverName", "databaseName", "failoverPartner")) {
            if (environment.getProperty("spring.datasource.hikari.data-source-properties." + property) != null) {
                throw new IllegalStateException("Local demo mode forbids connection target overrides.");
            }
        }
        if (!dbUrl.startsWith("jdbc:sqlserver://") || dbUrl.contains("{") || dbUrl.contains("}")
                || Pattern.compile("[;?&]\\s*failoverPartner\\s*=", Pattern.CASE_INSENSITIVE).matcher(dbUrl).find()) {
            throw new IllegalStateException("Local demo mode requires an unambiguous SQL Server URL without failover.");
        }

        String dbHost = extractDatabaseHost(dbUrl);
        if (dbHost == null || dbHost.isBlank()) {
            throw new IllegalStateException("Could not determine database host from JDBC URL.");
        }

        String normalizedHost = dbHost.trim().toLowerCase(Locale.ROOT);
        boolean isLoopbackDb = isLoopbackHost(normalizedHost);

        if (!isLoopbackDb) {
            throw new IllegalStateException("Local demo mode requires database host to be strictly loopback (127.0.0.1 / localhost). "
                    + "Disallowed database host: " + dbHost);
        }

        String dbName = extractDatabaseName(dbUrl);
        if (dbName == null || dbName.isBlank()) {
            throw new IllegalStateException("Could not determine database name from JDBC URL.");
        }

        String normalizedDb = dbName.toLowerCase(Locale.ROOT);
        boolean localDevDatabase = normalizedDb.equals("sportify")
                && environment.matchesProfiles("dev") && flagEnabled;
        if (normalizedDb.equals("sportify") && !localDevDatabase) {
            throw new IllegalStateException("Local demo mode strictly forbids running against the default 'sportify' database. "
                    + "Enable the guarded dev-only local demo flag while the server and database remain loopback-bound, "
                    + "or configure an approved throwaway database such as 'sportify_qr_demo'.");
        }

        if (!localDevDatabase && !ALLOWED_DEMO_DATABASES.contains(normalizedDb)
                && !normalizedDb.matches("sportify_qr04_test_[0-9]{14}")) {
            throw new IllegalStateException("Local demo mode rejected database '" + dbName
                    + "'. Only approved throwaway databases (" + ALLOWED_DEMO_DATABASES + ") are permitted.");
        }
    }

    private static boolean isLoopbackHost(String host) {
        if (host.equals("localhost") || host.equals("::1")) return true;
        if (!host.matches("127(?:\\.[0-9]{1,3}){3}")) return false;
        return java.util.Arrays.stream(host.split("\\.")).allMatch(part -> Integer.parseInt(part) <= 255);
    }

    public static String extractDatabaseName(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }

        Matcher matcher = DB_NAME_PATTERN.matcher(jdbcUrl);
        List<String> matchedNames = new ArrayList<>();
        while (matcher.find()) {
            matchedNames.add(matcher.group(1).trim());
        }

        if (matchedNames.size() > 1) {
            throw new IllegalStateException("Ambiguous JDBC URL: duplicate or conflicting database parameters detected: "
                    + matchedNames);
        }

        String paramDb = matchedNames.isEmpty() ? null : matchedNames.get(0);
        if (paramDb != null && !paramDb.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalStateException("Ambiguous JDBC URL: invalid database parameter.");
        }

        // Handle slash-separated database URLs like jdbc:sqlserver://localhost:1433/database_name
        String pathDb = null;
        int doubleSlash = jdbcUrl.indexOf("//");
        if (doubleSlash >= 0) {
            int pathStart = jdbcUrl.indexOf('/', doubleSlash + 2);
            if (pathStart >= 0 && pathStart < jdbcUrl.length() - 1) {
                String remainder = jdbcUrl.substring(pathStart + 1);
                int semicolon = remainder.indexOf(';');
                int question = remainder.indexOf('?');
                int end = remainder.length();
                if (semicolon >= 0) end = Math.min(end, semicolon);
                if (question >= 0) end = Math.min(end, question);
                String candidate = remainder.substring(0, end).trim();
                if (!candidate.isBlank() && !candidate.contains(":")) {
                    pathDb = candidate;
                }
            }
        }

        if (paramDb != null && pathDb != null && !paramDb.equalsIgnoreCase(pathDb)) {
            throw new IllegalStateException("Ambiguous JDBC URL: conflicting database names in path ('"
                    + pathDb + "') and parameter ('" + paramDb + "').");
        }

        return paramDb != null ? paramDb : pathDb;
    }

    public static String extractDatabaseHost(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.contains("//")) {
            return null;
        }

        // Multiple authority delimiters
        if (jdbcUrl.indexOf("//") != jdbcUrl.lastIndexOf("//")) {
            throw new IllegalStateException("Ambiguous JDBC URL: multiple authority delimiters ('//') detected.");
        }

        int doubleSlash = jdbcUrl.indexOf("//");
        String afterSlash = jdbcUrl.substring(doubleSlash + 2);
        int end = afterSlash.length();
        int slash = afterSlash.indexOf('/');
        int semicolon = afterSlash.indexOf(';');
        int question = afterSlash.indexOf('?');
        if (slash >= 0) end = Math.min(end, slash);
        if (semicolon >= 0) end = Math.min(end, semicolon);
        if (question >= 0) end = Math.min(end, question);

        String authority = afterSlash.substring(0, end).trim();
        if (authority.isEmpty()) {
            return null;
        }

        // Reject userinfo / authority confusion tricks (e.g. evil.com@localhost)
        if (authority.contains("@")) {
            throw new IllegalStateException("Ambiguous JDBC URL: '@' userinfo in authority is not permitted: " + authority);
        }

        // Reject multiple comma-separated hosts in local demo
        if (authority.contains(",")) {
            throw new IllegalStateException("Ambiguous JDBC URL: multiple hosts in authority are not permitted: " + authority);
        }

        String host;
        // IPv6 bracketed address: [::1]:1433
        if (authority.startsWith("[")) {
            int closingBracket = authority.indexOf(']');
            if (closingBracket > 1) {
                host = authority.substring(1, closingBracket).trim();
                if (!authority.substring(closingBracket + 1).matches("(?::[0-9]+)?")) {
                    throw new IllegalStateException("Ambiguous JDBC URL: invalid IPv6 authority.");
                }
            } else {
                return null;
            }
        } else {
            // Strip instance name: localhost\SQLEXPRESS
            int backslash = authority.indexOf('\\');
            if (backslash >= 0) {
                authority = authority.substring(0, backslash);
            }

            // Strip port: localhost:1433
            int colon = authority.indexOf(':');
            if (colon >= 0) {
                authority = authority.substring(0, colon);
            }

            host = authority.trim();
        }

        // Check if secondary serverName parameter conflicts with authority host
        Matcher serverMatcher = SERVER_NAME_PATTERN.matcher(jdbcUrl);
        while (serverMatcher.find()) {
            String serverProp = serverMatcher.group(1).trim();
            if (!serverProp.equalsIgnoreCase(host)) {
                throw new IllegalStateException("Ambiguous JDBC URL: server property ('" + serverProp
                        + "') conflicts with authority host ('" + host + "').");
            }
        }

        return host;
    }
}
