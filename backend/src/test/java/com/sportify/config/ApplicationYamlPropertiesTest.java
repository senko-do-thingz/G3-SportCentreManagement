package com.sportify.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationYamlPropertiesTest {

    @Test
    void applicationYml_shouldHaveCorrectJwtProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertNotNull(properties, "Properties should not be null");

        // Verify the correct keys exist
        assertTrue(properties.containsKey("application.security.jwt.secret-key"),
                "Missing application.security.jwt.secret-key");
        assertEquals("${JWT_SECRET}", properties.getProperty("application.security.jwt.secret-key"));

        assertTrue(properties.containsKey("application.security.jwt.expiration"),
                "Missing application.security.jwt.expiration");
        
        assertTrue(properties.containsKey("application.security.jwt.refresh-token.expiration"),
                "Missing application.security.jwt.refresh-token.expiration");

        // Verify the INCORRECT spring.security.jwt keys DO NOT exist
        assertFalse(properties.containsKey("spring.security.jwt.secret-key"),
                "spring.security.jwt.secret-key should not exist");
    }
}
