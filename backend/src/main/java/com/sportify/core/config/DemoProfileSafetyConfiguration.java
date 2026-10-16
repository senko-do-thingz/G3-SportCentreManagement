package com.sportify.core.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Prevents a demo seed from running when the production profile is also active. */
@Configuration(proxyBeanMethods = false)
public class DemoProfileSafetyConfiguration {

    // A static factory post-processor runs before singleton creation, including Flyway's initializer.
    @Bean
    static BeanFactoryPostProcessor demoProfileSafetyGuard(Environment environment) {
        return beanFactory -> {
            if (environment.matchesProfiles("prod") && environment.matchesProfiles("demo")) {
                throw new IllegalStateException("The prod and demo profiles must never be active together. "
                        + "Demo accounts require an isolated demonstration database.");
            }
        };
    }
}
