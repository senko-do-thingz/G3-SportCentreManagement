package com.sportify.demoqr.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** Explicit configuration only; request Host headers and client origins never create links. */
public record DemoQrSettings(String publicBaseUrl, long lifetimeSeconds) {
    public DemoQrSettings {
        lifetimeSeconds = validateLifetime(lifetimeSeconds);
        URI uri = URI.create(publicBaseUrl);
        publicBaseUrl = validate(publicBaseUrl, uri.getScheme() + "://" + uri.getRawAuthority());
    }

    public DemoQrSettings(String base, String allowedOrigins, boolean ignoredLegacyLanHttp, long seconds) {
        this(validate(base, allowedOrigins), validateLifetime(seconds));
    }

    private static long validateLifetime(long seconds) {
        if (seconds < 30 || seconds > 600) throw new IllegalArgumentException("Demo lifetime must be 30 to 600 seconds");
        return seconds;
    }

    private static String validate(String base, String origins) {
        try {
            URI uri = URI.create(base);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            boolean allowed = Arrays.stream(origins.split(",")).map(String::trim).anyMatch(origin::equals);
            String host = uri.getHost();
            String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
            boolean https = "https".equals(uri.getScheme());

            if (!allowed || host == null || normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                    || normalizedHost.startsWith("127.") || normalizedHost.equals("0.0.0.0") || normalizedHost.equals("::1")
                    || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || !https
                    || !uri.normalize().equals(uri) || base.contains("%") || base.contains("\\")) {
                throw new IllegalArgumentException();
            }
            return base.endsWith("/") ? base : base + "/";
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Configure an allowlisted HTTPS frontend base");
        }
    }

}
