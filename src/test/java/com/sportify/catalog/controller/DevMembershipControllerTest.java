package com.sportify.catalog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test") // Ensures "dev" is not active
public class DevMembershipControllerTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void devController_NotRegistered_WhenDevProfileOff() {
        assertThrows(NoSuchBeanDefinitionException.class, () -> context.getBean(DevMembershipController.class));
    }
}
