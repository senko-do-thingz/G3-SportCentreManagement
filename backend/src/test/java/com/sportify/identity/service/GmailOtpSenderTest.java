package com.sportify.identity.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Gmail OAuth refresh and send, with every Google call replaced by a recorded fake. */
class GmailOtpSenderTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final List<URI> calls = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();

    private GmailOtpSender sender(String from, int tokenStatus, String tokenBody, int sendStatus, String sendBody) {
        GmailOtpSender.Transport transport = (uri, headers, body) -> {
            calls.add(uri);
            bodies.add(body);
            return uri.equals(GmailOtpSender.TOKEN_URI) ? response(tokenStatus, tokenBody) : response(sendStatus, sendBody);
        };
        return new GmailOtpSender(JSON, from, "client-id", "client-secret", "refresh-token", transport,
                Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void refreshesOnceSendsAndReusesTheAccessToken() throws Exception {
        var gmail = sender("", 200, "{\"access_token\":\"at\",\"expires_in\":3599}", 200, "{\"id\":\"m1\"}");

        gmail.send("member@example.com", "123456");
        gmail.send("member@example.com", "654321");

        assertThat(calls).containsExactly(GmailOtpSender.TOKEN_URI, GmailOtpSender.SEND_URI, GmailOtpSender.SEND_URI);
        assertThat(bodies.get(0)).contains("grant_type=refresh_token");
        String mime = new String(Base64.getUrlDecoder().decode(JSON.readTree(bodies.get(1)).path("raw").asText()), StandardCharsets.UTF_8);
        assertThat(mime).startsWith("To: member@example.com").contains("123456").doesNotContain("From:");
    }

    @Test
    void aConfiguredSenderIsUsedAsTheFromAddress() throws Exception {
        var gmail = sender("sportify.center@example.com", 200, "{\"access_token\":\"at\",\"expires_in\":3599}", 200, "{}");

        gmail.send("member@example.com", "123456");

        String mime = new String(Base64.getUrlDecoder().decode(JSON.readTree(bodies.get(1)).path("raw").asText()), StandardCharsets.UTF_8);
        assertThat(mime).startsWith("From: sportify.center@example.com\r\nTo: member@example.com");
    }

    @Test
    void aRefusedRefreshReportsTheOAuthErrorWithoutSecrets() {
        var gmail = sender("", 401, "{\"error\":\"unauthorized_client\",\"error_description\":\"Unauthorized\"}", 200, "{}");

        assertThatThrownBy(() -> gmail.send("member@example.com", "123456"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gmail authorization failed: HTTP 401 unauthorized_client")
                .hasMessageNotContaining("refresh-token").hasMessageNotContaining("client-secret");
        assertThat(calls).containsExactly(GmailOtpSender.TOKEN_URI);
    }

    @Test
    void aMissingSendScopeIsReportedWithGooglesReason() {
        var gmail = sender("", 200, "{\"access_token\":\"at\",\"expires_in\":3599}", 403,
                "{\"error\":{\"status\":\"PERMISSION_DENIED\",\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}");

        assertThatThrownBy(() -> gmail.send("member@example.com", "123456"))
                .hasMessage("Gmail delivery failed: HTTP 403 PERMISSION_DENIED insufficientPermissions")
                .hasMessageNotContaining("123456");
    }

    @Test
    void withoutClientOrRefreshTokenItIsNotConfigured() {
        var gmail = new GmailOtpSender(JSON, "", "", "", "", (uri, headers, body) -> { throw new AssertionError("no call"); }, Clock.systemUTC());

        assertThat(gmail.configured()).isFalse();
        assertThatThrownBy(() -> gmail.send("member@example.com", "123456")).hasMessage("Gmail password recovery is not configured");
    }

    private static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return null; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return null; }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
