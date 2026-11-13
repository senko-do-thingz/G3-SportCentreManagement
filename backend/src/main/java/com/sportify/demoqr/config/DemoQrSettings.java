package com.sportify.demoqr.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** Explicit configuration only; request Host headers and client origins never create links. */
public record DemoQrSettings(String publicBaseUrl, long lifetimeSeconds) {
    /** Local frontend used when no public HTTPS base is configured. */
    public static final String LOCAL_FRONTEND_BASE = "http://localhost:5173/";

    public DemoQrSettings {
        lifetimeSeconds = validateLifetime(lifetimeSeconds);
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) publicBaseUrl = LOCAL_FRONTEND_BASE;
        publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
    }

    /** Uses the strict public HTTPS base when it is valid, otherwise the local frontend. */
    public static DemoQrSettings lenient(String base, String allowedOrigins, long seconds) {
        try {
            return new DemoQrSettings(base, allowedOrigins, false, seconds);
        } catch (IllegalArgumentException ex) {
            return new DemoQrSettings(base == null || base.isBlank() ? LOCAL_FRONTEND_BASE : base, 300);
        }
    }

    public DemoQrSettings(String base, String allowedOrigins, boolean ignoredLegacyLanHttp, long seconds) {
        this(validate(base, allowedOrigins), validateLifetime(seconds));
    }

    private static long validateLifetime(long seconds) {
        if (seconds != 300) throw new IllegalArgumentException("Demo token lifetime must be 300 seconds");
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
