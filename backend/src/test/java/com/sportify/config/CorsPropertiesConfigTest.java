package com.sportify.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks where the app.cors.allowed-origins property is defined (BE-00).
 */
class CorsPropertiesConfigTest {

    private static final String KEY = "app.cors.allowed-origins";

    private Properties load(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        Properties properties = factory.getObject();
        assertNotNull(properties, resource + " should be readable");
        return properties;
    }

    @Test
    void applicationYml_shouldDefaultToNoAllowedOrigins() {
        assertEquals("${CORS_ALLOWED_ORIGINS:}", load("application.yml").getProperty(KEY));
    }

    @Test
    void applicationDevYml_shouldDefaultToLocalFrontendOrigins() {
        assertEquals("${CORS_ALLOWED_ORIGINS:http://localhost:5173,http://localhost:3000}",
                load("application-dev.yml").getProperty(KEY));
    }

    @Test
    void applicationProdYml_shouldReadOriginsFromTheEnvironmentWithoutLocalhostDefault() {
        String value = load("application-prod.yml").getProperty(KEY);

        assertNotNull(value);
        assertTrue(value.startsWith("${CORS_ALLOWED_ORIGINS"), "prod must read CORS_ALLOWED_ORIGINS");
        assertFalse(value.contains("localhost"), "prod must not fall back to localhost");
    }

    @Test
    void envExample_shouldDocumentCorsAllowedOrigins() throws IOException {
        // Maven runs the tests with backend/ as the working directory.
        String content = Files.readString(Path.of(".env.example"));

        assertTrue(content.contains("CORS_ALLOWED_ORIGINS="), "Missing CORS_ALLOWED_ORIGINS in .env.example");
    }

    @Test
    void envExample_shouldContainAllRequiredKeysAndNoDoubleQuotes() throws IOException {
        java.util.List<String> lines = Files.readAllLines(Path.of(".env.example"));
        java.util.List<String> requiredKeys = java.util.List.of(
                "DB_URL",
                "DB_USERNAME",
                "DB_PASSWORD",
                "JWT_SECRET",
                "CORS_ALLOWED_ORIGINS",
                "SPORTIFY_PAYMENT_ACCOUNT_NAME",
                "SPORTIFY_PAYMENT_ACCOUNT_NUMBER",
                "SPORTIFY_PAYMENT_BANK"
        );

        for (String key : requiredKeys) {
            String matchingLine = lines.stream()
                    .filter(line -> line.startsWith(key + "="))
                    .findFirst()
                    .orElse(null);

            assertNotNull(matchingLine, "Missing required key in .env.example: " + key);
            String value = matchingLine.substring(key.length() + 1).trim();
            assertFalse(value.startsWith("\""), "Value for " + key + " must not start with double quote: " + value);
        }
    }
}
