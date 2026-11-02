package com.sportify.demoqr.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition guarding simulated QR components: active only when non-production AND
 * either the qr-simulator profile is active with simulator enabled, OR the local-demo
 * profile is active with local-demo enabled.
 */
public class SimulatedSettlementEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();
        if (env == null) {
            return false;
        }
        if (env.matchesProfiles("prod") || env.matchesProfiles("production")) {
            return false;
        }

        boolean qrSimActive = env.matchesProfiles("qr-simulator")
                && "true".equalsIgnoreCase(env.getProperty("app.payment.simulator.enabled"));

        boolean localDemoActive = env.matchesProfiles("local-demo")
                && ("true".equalsIgnoreCase(env.getProperty("app.payment.local-demo.enabled"))
                || "true".equalsIgnoreCase(env.getProperty("PAYMENT_LOCAL_DEMO_ENABLED")));

        return qrSimActive || localDemoActive;
    }
}
