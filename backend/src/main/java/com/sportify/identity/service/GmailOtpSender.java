package com.sportify.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Component
public class GmailOtpSender {
    @Value("${GMAIL_SENDER_EMAIL:}") private String sender;
    @Value("${GMAIL_OAUTH_CLIENT_ID:}") private String clientId;
    @Value("${GMAIL_OAUTH_CLIENT_SECRET:}") private String clientSecret;
    @Value("${GMAIL_OAUTH_REFRESH_TOKEN:}") private String refreshToken;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;

    public GmailOtpSender(ObjectMapper json) { this.json = json; }

    public void send(String to, String otp) {
        if (sender.isBlank() || clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank())
            throw new IllegalStateException("Gmail password recovery is not configured");
        try {
            String form = "client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret)
                    + "&refresh_token=" + encode(refreshToken) + "&grant_type=refresh_token";
            var tokenResponse = http.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            if (tokenResponse.statusCode() != 200) throw new IllegalStateException("Gmail authorization failed");
            String accessToken = json.readTree(tokenResponse.body()).path("access_token").asText();
            if (accessToken.isBlank()) throw new IllegalStateException("Gmail authorization failed");
            String mime = "From: " + sender + "\r\nTo: " + to + "\r\nSubject: Sportify Center - Password Reset OTP\r\n"
                    + "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n"
                    + "Hello,\r\n\r\nYou requested a password reset for your Sportify Center account.\r\n"
                    + "Your verification code is: " + otp + "\r\nThis code expires in 5 minutes.\r\n"
                    + "If you did not request this change, you can ignore this email.\r\n\r\nSportify Center\r\n";
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(mime.getBytes(StandardCharsets.UTF_8));
            String body = json.writeValueAsString(Map.of("raw", raw));
            var sent = http.send(HttpRequest.newBuilder(URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                    .header("Authorization", "Bearer " + accessToken).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding());
            if (sent.statusCode() < 200 || sent.statusCode() >= 300) throw new IllegalStateException("Gmail delivery failed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gmail delivery interrupted");
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Gmail delivery failed");
        }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
