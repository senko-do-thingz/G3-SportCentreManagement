package com.sportify.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.payment.config.PayOsProperties;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayOsWebhookService {
    private final PayOsApiClient api;
    private final PayOsProperties properties;
    private final PaymentRepository payments;
    private final PaymentVerificationService verification;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public record WebhookEvent(String code, String desc, boolean success, JsonNode data, String signature) {}
    public record WebhookResult(String status, String message) {}
    private record WebhookData(Long orderCode, Long amount, String currency, String reference,
                               String transactionDateTime, String paymentLinkId, String code) {}

    @Transactional
    public WebhookResult accept(WebhookEvent event) {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS webhook processing is disabled.");
        }
        if (event == null || event.data() == null || !api.verifyWebhook(event.data(), event.signature())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "PayOS webhook signature is invalid.");
        }
        if (!event.success() || !"00".equals(event.code())) {
            return ignored("PayOS reported a non-success event.");
        }

        final WebhookData data;
        try {
            data = objectMapper.treeToValue(event.data(), WebhookData.class);
        } catch (Exception invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayOS webhook data is invalid.");
        }
        if (data.orderCode() == null || data.amount() == null || data.paymentLinkId() == null
                || data.paymentLinkId().isBlank() || !"00".equals(data.code())) {
            return ignored("PayOS webhook does not describe a completed payment.");
        }

        // PayOS order codes are allocated uniquely for this checkout, independently of local payment IDs.
        // Sample webhook events used to validate a URL have no matching link ID and are acknowledged
        // without changing any Sportify payment.
        Payment payment = payments.findByPayOsOrderCode(data.orderCode()).flatMap(found ->
                payments.findByIdForUpdate(found.getId())).orElse(null);
        if (payment == null || !data.paymentLinkId().equals(payment.getPayOsPaymentLinkId())) {
            return ignored("No matching PayOS payment link was found.");
        }
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS && "PAYOS".equals(payment.getSettlementProvider())) {
            return new WebhookResult("ALREADY_PROCESSED", "PayOS payment was already recorded.");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            return ignored("The matching payment is no longer eligible for PayOS settlement.");
        }
        if (data.amount() != payment.getAmount().longValueExact()
                || !"VND".equalsIgnoreCase(data.currency())) {
            log.error("PayOS callback amount or currency does not match payment id {}", payment.getId());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayOS payment amount or currency does not match.");
        }
        String reference = data.reference() == null || data.reference().isBlank()
                ? data.paymentLinkId() : data.reference().trim();
        if (reference.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PayOS transaction reference is invalid.");
        }
        var existingTransaction = payments.findByPayOsTransactionReference(reference);
        if (existingTransaction.isPresent() && !existingTransaction.get().getId().equals(payment.getId())) {
            log.error("PayOS transaction reference was already assigned to a different payment.");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PayOS transaction has already been recorded.");
        }

        verification.confirmPayOsPayment(payment.getId(), reference, parseTransactionTime(data.transactionDateTime()));
        return new WebhookResult("PAID", "PayOS payment was recorded.");
    }

    private static WebhookResult ignored(String message) {
        return new WebhookResult("IGNORED", message);
    }

    private LocalDateTime parseTransactionTime(String value) {
        if (value == null || value.isBlank()) return LocalDateTime.now(clock);
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (RuntimeException ignored) { }
        try { return OffsetDateTime.parse(value).atZoneSameInstant(clock.getZone()).toLocalDateTime(); }
        catch (RuntimeException ignored) { }
        return LocalDateTime.now(clock);
    }
}
