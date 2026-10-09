package com.sportify.catalog.controller;

import com.sportify.catalog.service.MembershipService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class DevMembershipControllerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DevMembershipController.class, MockConfig.class);

    @Configuration
    static class MockConfig {
        @Bean
        public MembershipService membershipService() {
            return Mockito.mock(MembershipService.class);
        }
    }

    @Test
    void devController_Absent_WhenProfileIsProd() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DevMembershipController.class);
                });
    }

    @Test
    void devController_Present_WhenProfileIsDev() {
        contextRunner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context).hasSingleBean(DevMembershipController.class);
                });
    }
}
