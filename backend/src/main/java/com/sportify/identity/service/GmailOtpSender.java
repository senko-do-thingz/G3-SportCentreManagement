package com.sportify.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Sends the password-reset OTP through the Gmail API (users.messages.send) with an OAuth 2.0 refresh token. Only the
 * refresh token, client id and client secret are configuration; access tokens are fetched on demand and kept in
 * memory until shortly before they expire. Failures report the HTTP status and the OAuth error code only, never a
 * token, secret, address or OTP.
 */
@Component
public class GmailOtpSender {
    static final URI TOKEN_URI = URI.create("https://oauth2.googleapis.com/token");
    static final URI SEND_URI = URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Logger log = LoggerFactory.getLogger(GmailOtpSender.class);

    /** One HTTP POST; replaced in tests so no real Google call is made. */
    interface Transport {
        HttpResponse<String> post(URI uri, Map<String, String> headers, String body) throws IOException, InterruptedException;
    }

    private final String sender;
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final ObjectMapper json;
    private final Transport transport;
    private final Clock clock;
    private String accessToken;
    private Instant accessTokenExpiry = Instant.EPOCH;

    @Autowired
    public GmailOtpSender(ObjectMapper json,
                          @Value("${GMAIL_SENDER_EMAIL:}") String sender,
                          @Value("${GMAIL_OAUTH_CLIENT_ID:}") String clientId,
                          @Value("${GMAIL_OAUTH_CLIENT_SECRET:}") String clientSecret,
                          @Value("${GMAIL_OAUTH_REFRESH_TOKEN:}") String refreshToken) {
        this(json, sender, clientId, clientSecret, refreshToken, httpTransport(), Clock.systemUTC());
    }

    GmailOtpSender(ObjectMapper json, String sender, String clientId, String clientSecret, String refreshToken,
                   Transport transport, Clock clock) {
        this.json = json;
        this.sender = trim(sender);
        this.clientId = trim(clientId);
        this.clientSecret = trim(clientSecret);
        this.refreshToken = trim(refreshToken);
        this.transport = transport;
        this.clock = clock;
    }

    private static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        return (uri, headers, body) -> {
            var request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        };
    }

    /** True when the OAuth client and refresh token are present; the sender address is optional. */
    public boolean configured() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !refreshToken.isEmpty();
    }

    public void send(String to, String otp) {
        if (!configured()) throw new IllegalStateException("Gmail password recovery is not configured");
        try {
            String token = accessToken();
            // Without a configured sender the From header is left out and Gmail uses the authorised account itself.
            String from = sender.isEmpty() ? "" : "From: " + sender + "\r\n";
            String mime = from + "To: " + to + "\r\nSubject: Sportify Center - Password Reset OTP\r\n"
                    + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n"
                    + "Hello,\r\n\r\nYou requested a password reset for your Sportify Center account.\r\n"
                    + "Your verification code is: " + otp + "\r\nThis code expires in 5 minutes.\r\n"
                    + "If you did not request this change, you can ignore this email.\r\n\r\nSportify Center\r\n";
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
            var sent = transport.post(SEND_URI, Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"),
                    json.writeValueAsString(Map.of("raw", raw)));
            if (sent.statusCode() == 401) clearAccessToken();
            if (sent.statusCode() < 200 || sent.statusCode() >= 300) {
                String reason = googleReason(sent.body());
                log.warn("Gmail send failed: HTTP {} {}", sent.statusCode(), reason);
                throw new IllegalStateException("Gmail delivery failed: HTTP " + sent.statusCode() + " " + reason);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gmail delivery interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("Gmail delivery failed: " + e.getClass().getSimpleName());
        }
    }

    private synchronized String accessToken() throws IOException, InterruptedException {
        if (accessToken != null && clock.instant().isBefore(accessTokenExpiry)) return accessToken;
        String form = "client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken) + "&grant_type=refresh_token";
        var response = transport.post(TOKEN_URI, Map.of("Content-Type", "application/x-www-form-urlencoded"), form);
        JsonNode body = readJson(response.body());
        if (response.statusCode() != 200) {
            // invalid_client: wrong client secret; invalid_grant: revoked, expired or Testing-mode refresh token;
            // unauthorized_client: the refresh token was issued to a different OAuth client.
            String error = body.path("error").asText("unknown");
            log.warn("Gmail OAuth refresh failed: HTTP {} error={}", response.statusCode(), error);
            throw new IllegalStateException("Gmail authorization failed: HTTP " + response.statusCode() + " " + error);
        }
        String token = body.path("access_token").asText("");
        if (token.isEmpty()) throw new IllegalStateException("Gmail authorization failed: no access token");
        long seconds = Math.max(60, body.path("expires_in").asLong(3600));
        accessToken = token;
        accessTokenExpiry = clock.instant().plusSeconds(seconds - 60);
        return token;
    }

    private synchronized void clearAccessToken() {
        accessToken = null;
        accessTokenExpiry = Instant.EPOCH;
    }

    /** Google's error status or reason only, for example PERMISSION_DENIED or insufficientPermissions. */
    private String googleReason(String body) {
        JsonNode node = readJson(body).path("error");
        String status = node.path("status").asText("");
        String reason = node.path("errors").path(0).path("reason").asText("");
        return (status + " " + reason).trim();
    }

    private JsonNode readJson(String body) {
        try { return json.readTree(body == null || body.isBlank() ? "{}" : body); }
        catch (IOException e) { return json.createObjectNode(); }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
