package com.sportify.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.payment.payos")
public class PayOsProperties {
    private boolean enabled;
    private String apiBaseUrl = "https://api-merchant.payos.vn";
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    private String frontendBaseUrl = "http://localhost:5173";

    @jakarta.validation.constraints.AssertTrue(message = "PayOS Client ID, API key and checksum key are required when PayOS is enabled")
    public boolean isConfigurationValid() {
        return !enabled || (notBlank(clientId) && notBlank(apiKey) && notBlank(checksumKey)
                && trustedApiBase() && validFrontendBase());
    }

    private boolean trustedApiBase() {
        try {
            URI uri = URI.create(apiBaseUrl);
            return "https".equalsIgnoreCase(uri.getScheme()) && "api-merchant.payos.vn".equalsIgnoreCase(uri.getHost())
                    && uri.getUserInfo() == null && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private boolean validFrontendBase() {
        try {
            URI uri = URI.create(frontendBaseUrl);
            boolean scheme = "https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme()) && "localhost".equalsIgnoreCase(uri.getHost()));
            return scheme && uri.getUserInfo() == null && uri.getPort() != 0
                    && (uri.getPath() == null || uri.getPath().isEmpty())
                    && uri.getRawQuery() == null && uri.getRawFragment() == null;
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
