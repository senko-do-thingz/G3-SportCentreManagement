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
}
