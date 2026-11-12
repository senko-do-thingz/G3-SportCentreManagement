package com.sportify.demoqr.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Enables token QR only when its complete public-link contract is configured. */
public class TokenQrEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        if (!new SimulatedSettlementEnabledCondition().matches(context, metadata)) return false;
        var env = context.getEnvironment();
        if (env.matchesProfiles("qr-simulator")) return true;
        try {
            new DemoQrSettings(env.getProperty("app.payment.simulator.public-base-url", ""),
                    env.getProperty("app.payment.simulator.allowed-origins", ""), false,
                    env.getProperty("app.payment.simulator.lifetime-seconds", Long.class, 300L));
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
