package com.sportify.demoqr.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Token QR is available in every non-production runtime; production never exposes simulated settlement. */
public class TokenQrEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        return env != null && !env.matchesProfiles("prod") && !env.matchesProfiles("production");
    }
}
