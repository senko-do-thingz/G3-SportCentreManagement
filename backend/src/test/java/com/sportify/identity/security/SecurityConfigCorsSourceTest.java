package com.sportify.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test of the CorsConfigurationSource bean: parsing of the property value
 * and the fixed policy (methods, headers, credentials, max age).
 */
class SecurityConfigCorsSourceTest {

    private final SecurityConfig securityConfig = new SecurityConfig(null, null, null, null);

    private CorsConfiguration configurationFor(String allowedOrigins) {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource(allowedOrigins);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sports");
        ServletRequestPathUtils.parseAndCache(request);
        CorsConfiguration configuration = source.getCorsConfiguration(request);
        assertNotNull(configuration);
        return configuration;
    }

    @Test
    void commaSeparatedOrigins_shouldBeSplitAndTrimmed() {
        CorsConfiguration configuration = configurationFor(" http://localhost:5173 , http://localhost:3000 ");

        assertEquals(2, configuration.getAllowedOrigins().size());
        assertTrue(configuration.getAllowedOrigins().contains("http://localhost:5173"));
        assertTrue(configuration.getAllowedOrigins().contains("http://localhost:3000"));
    }

    @Test
    void blankEntries_shouldBeIgnored() {
        CorsConfiguration configuration = configurationFor("http://localhost:5173,, ,");

        assertEquals(1, configuration.getAllowedOrigins().size());
        assertEquals("http://localhost:5173", configuration.getAllowedOrigins().get(0));
    }

    @Test
    void emptyValue_shouldAllowNoOrigin() {
        CorsConfiguration configuration = configurationFor("");

        assertTrue(configuration.getAllowedOrigins().isEmpty());
    }

    @Test
    void policy_shouldMatchTheTaskSpecification() {
        CorsConfiguration configuration = configurationFor("http://localhost:5173");

        assertEquals(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"), configuration.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type"), configuration.getAllowedHeaders());
        assertFalse(Boolean.TRUE.equals(configuration.getAllowCredentials()));
        assertEquals(3600L, configuration.getMaxAge());
    }
}
