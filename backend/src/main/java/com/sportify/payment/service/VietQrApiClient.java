package com.sportify.payment.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sportify.payment.config.VietQrGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class VietQrApiClient {

    @Qualifier("vietQrRestClient")
    private final RestClient client;
    private final VietQrGatewayProperties properties;
    private volatile CachedToken cachedToken;

    public GenerateQrResponse generateQr(GenerateQrRequest request) {
        return withBearerToken(token -> client.post()
                .uri(properties.getApiBaseUrl() + "/api/qr/generate-customer")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(request)
                .retrieve()
                .body(GenerateQrResponse.class));
    }

    public void submitSandboxCallback(TestCallbackRequest request) {
        if (!properties.isSandbox()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "VietQR test callbacks are available only in sandbox.");
        }
        TestCallbackResponse response = withBearerToken(token -> client.post()
                .uri(properties.getApiBaseUrl() + "/bank/api/test/transaction-callback")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .body(request)
                .retrieve()
                .body(TestCallbackResponse.class));
        if (response == null || !"SUCCESS".equalsIgnoreCase(response.status())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR did not accept the sandbox callback request.");
        }
    }

    private <T> T withBearerToken(Function<String, T> operation) {
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = accessToken();
            try {
                return operation.apply(token);
            } catch (RestClientResponseException failure) {
                if (failure.getStatusCode().value() == 401 && attempt == 0) {
                    invalidate(token);
                    continue;
                }
                log.warn("VietQR API request failed with HTTP {}", failure.getStatusCode().value());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR could not process this payment request.");
            } catch (org.springframework.web.client.ResourceAccessException failure) {
                log.warn("VietQR API request could not reach the provider: {}", failure.getClass().getSimpleName());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VietQR is unavailable. Try again shortly.");
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR could not process this payment request.");
    }

    private String accessToken() {
        CachedToken current = cachedToken;
        if (current != null && Instant.now().isBefore(current.expiresAt().minusSeconds(10))) return current.value();
        synchronized (this) {
            current = cachedToken;
            if (current != null && Instant.now().isBefore(current.expiresAt().minusSeconds(10))) return current.value();
            String credentials = properties.getApiUsername() + ":" + properties.getApiPassword();
            String basic = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            ProviderTokenResponse response;
            try {
                response = client.post()
                        .uri(properties.getApiBaseUrl() + "/api/token_generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .retrieve()
                        .body(ProviderTokenResponse.class);
            } catch (RestClientResponseException failure) {
                log.warn("VietQR token request failed with HTTP {}", failure.getStatusCode().value());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR authentication failed. Check the local sandbox credentials.");
            } catch (org.springframework.web.client.ResourceAccessException failure) {
                log.warn("VietQR token request could not reach the provider: {}", failure.getClass().getSimpleName());
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "VietQR is unavailable. Try again shortly.");
            }
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()
                    || (response.tokenType() != null && !"Bearer".equalsIgnoreCase(response.tokenType()))) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned an invalid access token response.");
            }
            long lifetime = response.expiresIn() == null ? 300 : Math.max(1, response.expiresIn());
            cachedToken = new CachedToken(response.accessToken(), Instant.now().plusSeconds(lifetime));
            return cachedToken.value();
        }
    }

    private synchronized void invalidate(String token) {
        if (cachedToken != null && cachedToken.value().equals(token)) cachedToken = null;
    }

    private record CachedToken(String value, Instant expiresAt) {}

    public record GenerateQrRequest(
            long amount,
            String content,
            String bankAccount,
            String bankCode,
            String userBankName,
            String transType,
            String orderId,
            int qrType) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GenerateQrResponse(
            String bankCode,
            String bankName,
            String bankAccount,
            String userBankName,
            String amount,
            String content,
            String qrCode,
            String imgId,
            String transactionId,
            String transactionRefId,
            String qrLink,
            String orderId) {}

    public record TestCallbackRequest(
            String bankAccount,
            String content,
            long amount,
            String transType,
            String bankCode) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProviderTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") Long expiresIn) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestCallbackResponse(String status, String message) {}
}
