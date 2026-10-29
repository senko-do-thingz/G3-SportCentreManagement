package com.sportify.demoqr.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

/** Explicit configuration only; request Host headers and client origins never create links. */
public record DemoQrSettings(String publicBaseUrl, long lifetimeSeconds) {
    public DemoQrSettings(String base, String allowedOrigins, boolean allowLanHttp, long seconds) {
        this(validate(base, allowedOrigins, allowLanHttp), validateLifetime(seconds));
    }

    private static long validateLifetime(long seconds) {
        if (seconds < 30 || seconds > 600) throw new IllegalArgumentException("Demo lifetime must be 30 to 600 seconds");
        return seconds;
    }

    private static String validate(String base, String origins, boolean allowLanHttp) {
        try {
            URI uri = URI.create(base);
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            boolean allowed = Arrays.stream(origins.split(",")).map(String::trim).anyMatch(origin::equals);
            String host = uri.getHost();
            String normalizedHost = host == null ? "" : host.toLowerCase(Locale.ROOT).replace("[", "").replace("]", "");
            boolean https = "https".equals(uri.getScheme());
            boolean localHttp = "http".equals(uri.getScheme()) && allowLanHttp && isPrivateIpv4(host);
            if (!allowed || host == null || normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                    || normalizedHost.startsWith("127.") || normalizedHost.equals("0.0.0.0") || normalizedHost.equals("::1")
                    || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (!https && !localHttp)
                    || !uri.normalize().equals(uri) || base.contains("%") || base.contains("\\")) {
                throw new IllegalArgumentException();
            }
            return base.endsWith("/") ? base : base + "/";
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Configure an allowlisted HTTPS frontend base or explicit private LAN HTTP base");
        }
    }

    private static boolean isPrivateIpv4(String host) {
        if (host == null || !host.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        int[] octets;
        try {
            octets = Arrays.stream(host.split("\\.")).mapToInt(Integer::parseInt).toArray();
        } catch (NumberFormatException ex) {
            return false;
        }
        for (int octet : octets) if (octet > 255) return false;
        String canonical = octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
        if (!host.equals(canonical)) return false;
        return octets[0] == 10 || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31)
                || (octets[0] == 192 && octets[1] == 168);
    }
}
