package com.sportify.demoqr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class DemoQrConfiguration {
    @Bean
    public DemoQrSettings demoQrSettings(
            @Value("${app.payment.simulator.public-base-url:}") String base,
            @Value("${app.payment.simulator.allowed-origins:}") String origins,

            @Value("${app.payment.simulator.lifetime-seconds:300}") long lifetimeSeconds) {
        return new DemoQrSettings(base, origins, false, lifetimeSeconds);
    }
}
