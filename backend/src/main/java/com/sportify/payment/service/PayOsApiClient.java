package com.sportify.payment.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.payment.config.PayOsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class PayOsApiClient {
    private final RestClient client;
    private final PayOsProperties properties;
    private final ObjectMapper objectMapper;

    public PayOsApiClient(@Qualifier("payOsRestClient") RestClient client,
                          PayOsProperties properties,
                          ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PaymentLinkData createPaymentLink(CreatePaymentLinkRequest request) {
        try {
            ApiResponse response = client.post()
                    .uri(apiBase() + "/v2/payment-requests")
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ApiResponse.class);
            if (response == null || !"00".equals(response.code()) || response.data() == null) {
                throw unavailable("PayOS did not create a payment link.");
            }
            return response.data();
        } catch (RestClientResponseException failure) {
            log.warn("PayOS create-link request failed with HTTP {}", failure.getStatusCode().value());
            if (failure.getStatusCode().value() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "PayOS order code already exists.");
            }
            throw unavailable("PayOS could not create the payment link. Check the payment channel and try again.");
        } catch (org.springframework.web.client.ResourceAccessException failure) {
            log.warn("PayOS create-link request could not reach the provider: {}", failure.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS is unavailable. Try again shortly.");
        }
    }

    public PaymentLinkData getPaymentLink(String id) {
        try {
            ApiResponse response = client.get()
                    .uri(apiBase() + "/v2/payment-requests/{id}", id)
                    .headers(this::authorize)
                    .retrieve()
                    .body(ApiResponse.class);
            if (response == null || !"00".equals(response.code()) || response.data() == null) {
                throw unavailable("PayOS did not return the payment link status.");
            }
            return response.data();
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 404) return null;
            log.warn("PayOS get-link request failed with HTTP {}", failure.getStatusCode().value());
            throw unavailable("PayOS could not retrieve the payment link.");
        } catch (org.springframework.web.client.ResourceAccessException failure) {
            log.warn("PayOS get-link request could not reach the provider: {}", failure.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS is unavailable. Try again shortly.");
        }
    }

    public PaymentLinkData cancelPaymentLink(String id) {
        try {
            ApiResponse response = client.post()
                    .uri(apiBase() + "/v2/payment-requests/{id}/cancel", id)
                    .headers(this::authorize)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancellationReason", "Member cancelled the Sportify payment"))
                    .retrieve()
                    .body(ApiResponse.class);
            if (response == null || !"00".equals(response.code()) || response.data() == null) {
                throw unavailable("PayOS did not cancel the payment link.");
            }
            return response.data();
        } catch (RestClientResponseException failure) {
            log.warn("PayOS cancel-link request failed with HTTP {}", failure.getStatusCode().value());
            throw unavailable("PayOS could not cancel the payment link. Refresh the payment and try again.");
        } catch (org.springframework.web.client.ResourceAccessException failure) {
            log.warn("PayOS cancel-link request could not reach the provider: {}", failure.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS is unavailable. Try again shortly.");
        }
    }

    public String createPaymentSignature(long orderCode, int amount, String description, String cancelUrl, String returnUrl) {
        return hmac("amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description
                + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl);
    }

    public boolean verifyWebhook(JsonNode data, String signature) {
        if (data == null || !data.isObject() || signature == null || !signature.matches("(?i)[0-9a-f]{64}")) {
            return false;
        }
        String expected = hmac(canonicalFields(data));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private String canonicalFields(JsonNode object) {
        Map<String, JsonNode> sorted = new TreeMap<>();
        object.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
        List<String> fields = new ArrayList<>(sorted.size());
        sorted.forEach((key, value) -> fields.add(key + "=" + canonicalValue(value)));
        return String.join("&", fields);
    }

    private String canonicalValue(JsonNode value) {
        if (value == null || value.isNull()) return "";
        if (value.isTextual()) return value.textValue();
        if (value.isObject()) return "{" + canonicalFields(value) + "}";
        if (value.isArray()) {
            List<String> values = new ArrayList<>();
            value.forEach(item -> values.add(item.isObject() ? "{" + canonicalFields(item) + "}" : canonicalValue(item)));
            try { return objectMapper.writeValueAsString(values); }
            catch (Exception ignored) { return value.toString(); }
        }
        return value.toString();
    }

    private String hmac(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getChecksumKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not calculate the PayOS signature", failure);
        }
    }

    private void authorize(org.springframework.http.HttpHeaders headers) {
        headers.set("x-client-id", properties.getClientId());
        headers.set("x-api-key", properties.getApiKey());
    }

    private String apiBase() {
        return properties.getApiBaseUrl().replaceAll("/+$", "");
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }

    public record CreatePaymentLinkRequest(
            @JsonProperty("orderCode") long orderCode,
            @JsonProperty("amount") int amount,
            @JsonProperty("description") String description,
            @JsonProperty("cancelUrl") String cancelUrl,
            @JsonProperty("returnUrl") String returnUrl,
            @JsonProperty("signature") String signature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse(String code, String desc, PaymentLinkData data, String signature) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentLinkData(
            @JsonProperty("id") String id,
            @JsonProperty("paymentLinkId") String paymentLinkId,
            @JsonProperty("orderCode") Long orderCode,
            @JsonProperty("amount") Long amount,
            @JsonProperty("description") String description,
            @JsonProperty("status") String status,
            @JsonProperty("checkoutUrl") String checkoutUrl,
            @JsonProperty("qrCode") String qrCode,
            @JsonProperty("amountPaid") Long amountPaid,
            @JsonProperty("amountRemaining") Long amountRemaining,
            @JsonProperty("transactions") JsonNode transactions) {
        public PaymentLinkData(String id, String paymentLinkId, Long orderCode, Long amount,
                               String status, String checkoutUrl, String qrCode) {
            this(id, paymentLinkId, orderCode, amount, null, status, checkoutUrl, qrCode, null, null, null);
        }

        public PaymentLinkData(String id, String paymentLinkId, Long orderCode, Long amount, String description,
                               String status, String checkoutUrl, String qrCode) {
            this(id, paymentLinkId, orderCode, amount, description, status, checkoutUrl, qrCode, null, null, null);
        }

        public String linkId() { return paymentLinkId != null ? paymentLinkId : id; }
    }
}
