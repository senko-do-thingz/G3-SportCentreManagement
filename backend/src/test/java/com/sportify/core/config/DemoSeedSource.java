package com.sportify.core.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/** Reads the actual migration and README so tests cannot silently exercise a separate seed fixture. */
final class DemoSeedSource {
    static final String RESOURCE = "db/demo/V28__seed_demo_accounts.sql";
    static final Path ROOT = Files.isDirectory(Path.of("backend")) ? Path.of(".") : Path.of("..");
    static final String SQL = resource(RESOURCE);
    static final String README = file(ROOT.resolve("README.md"));
    static final String PASSWORD = capture(README, "Common demo password: `([^`]+)`");
    static final String HASH = capture(SQL, "DECLARE @PasswordHash NVARCHAR\\(100\\) = '([^']+)'");
    static final List<Account> ACCOUNTS = Pattern.compile(
            "\\((\\d+), '([^']+@example\\.com)', '([^']+)', '(RECEPTIONIST|COACH|MEMBER)', "
                    + "(NULL|'[^']+'), (NULL|'[^']+')\\)")
            .matcher(SQL).results().map(m -> new Account(Integer.parseInt(m.group(1)), m.group(2),
                    m.group(3), m.group(4), nullable(m.group(5)), nullable(m.group(6)))).toList();

    record Account(int ordinal, String email, String name, String role, String sport, String tier) {
        @Override
        public String toString() {
            return email;
        }
    }

    static String capture(String text, String expression) {
        var matcher = Pattern.compile(expression).matcher(text);
        if (!matcher.find()) {
            throw new IllegalStateException("Required demo specification field is missing");
        }
        return matcher.group(1);
    }

    static String resource(String name) {
        try (var input = DemoSeedSource.class.getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IllegalStateException("Missing demo migration resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static String file(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String nullable(String value) {
        return "NULL".equals(value) ? null : value.substring(1, value.length() - 1);
    }

    private DemoSeedSource() {
    }
}
