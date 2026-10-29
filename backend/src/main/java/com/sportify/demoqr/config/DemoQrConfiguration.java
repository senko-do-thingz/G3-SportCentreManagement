package com.sportify.demoqr.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("(dev | qr-demo) & !prod & !production")
@ConditionalOnProperty(name = "app.demo.qr.enabled", havingValue = "true")
public class DemoQrConfiguration {
    @Bean
    public DemoQrSettings demoQrSettings(
            @Value("${app.demo.qr.public-base-url:}") String base,
            @Value("${app.demo.qr.allowed-origins:}") String origins,
            @Value("${app.demo.qr.allow-lan-http:false}") boolean allowLanHttp,
            @Value("${app.demo.qr.lifetime-seconds:300}") long lifetimeSeconds) {
        return new DemoQrSettings(base, origins, allowLanHttp, lifetimeSeconds);
    }
}
