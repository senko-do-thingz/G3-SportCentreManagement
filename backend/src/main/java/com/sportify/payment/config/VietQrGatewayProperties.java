package com.sportify.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.payment.vietqr")
public class VietQrGatewayProperties {

    private boolean enabled;
    private String environment = "sandbox";
    private boolean localCallbackFallback;
    private String apiUsername = "";
    private String apiPassword = "";
    private String bankCode = "";
    private String callbackUsername = "";
    private String callbackPassword = "";
    private String callbackJwtSecret = "";
    private int callbackTokenTtlSeconds = 300;

    public boolean isSandbox() {
        return environment == null || !"production".equalsIgnoreCase(environment.trim());
    }

    public String getApiBaseUrl() {
        return isSandbox() ? "https://dev.vietqr.org/vqr" : "https://api.vietqr.org/vqr";
    }

    @jakarta.validation.constraints.AssertTrue(message = "VietQR environment must be sandbox or production")
    public boolean isEnvironmentValid() {
        return environment != null && ("sandbox".equalsIgnoreCase(environment.trim())
                || "production".equalsIgnoreCase(environment.trim()));
    }

    @jakarta.validation.constraints.AssertTrue(message = "VietQR API and callback credentials must be configured when VietQR is enabled")
    public boolean isGatewayConfigurationValid() {
        if (!enabled) return true;
        boolean callbackReceiverRequired = !localCallbackFallback || !isSandbox();
        return nonBlank(apiUsername) && nonBlank(apiPassword)
                && bankCode != null && bankCode.matches("[A-Za-z0-9]{2,10}")
                && (!callbackReceiverRequired || nonBlank(callbackUsername) && nonBlank(callbackPassword)
                    && callbackJwtSecret != null && callbackJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32)
                && callbackTokenTtlSeconds >= 60 && callbackTokenTtlSeconds <= 3600;
    }

    @jakarta.validation.constraints.AssertTrue(message = "VietQR callback username, password and a 32-byte JWT secret must be configured together")
    public boolean isCallbackConfigurationValid() {
        boolean any = nonBlank(callbackUsername) || nonBlank(callbackPassword) || nonBlank(callbackJwtSecret);
        if (!any) return true;
        return nonBlank(callbackUsername) && nonBlank(callbackPassword)
                && callbackJwtSecret != null && callbackJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32
                && callbackTokenTtlSeconds >= 60 && callbackTokenTtlSeconds <= 3600;
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }
}
