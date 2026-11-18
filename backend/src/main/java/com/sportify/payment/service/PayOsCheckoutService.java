package com.sportify.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayOsCheckoutService {
    private final PaymentService payments;
    private final PaymentRepository paymentRepository;
    private final PayOsApiClient api;
    private final PayOsProperties properties;
    private final PaymentVerificationService verification;

    public record Checkout(long paymentId, String paymentCode, boolean enabled, long orderCode,
                           String paymentLinkId, String checkoutUrl, String qrCode,
                           BigDecimal amount, String currency, String notice) {}
    public record StatusSync(long paymentId, boolean settled, String providerStatus, String notice) {}

    @Transactional
    public Checkout checkout(long paymentId, UserAccount actor) {
        payments.getPayment(paymentId, actor); // Ownership is checked before loading a writable entity.
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
        requireEligiblePayment(payment);

        if (!properties.isEnabled()) {
            return new Checkout(paymentId, payment.getPaymentCode(), false, paymentId,
                    null, null, null, payment.getAmount(), "VND",
                    "PayOS checkout is not configured. Add the PayOS channel credentials to backend/.env and restart the backend.");
        }
        int amount = wholeVnd(payment.getAmount());
        long orderCode = payment.getPayOsOrderCode() != null
                ? payment.getPayOsOrderCode() : reservePayOsOrderCode(payment);
        if (orderCode <= 0 || orderCode > Integer.MAX_VALUE) {
            throw new BusinessRuleException("This payment cannot be represented by a PayOS order code.", "PAYOS_ORDER_CODE_INVALID");
        }

        if (payment.getPayOsPaymentLinkId() == null) {
            String base = properties.getFrontendBaseUrl().replaceAll("/+$", "");
            String returnUrl = base + "/member/payments/" + paymentId + "/status?payos=return";
            String cancelUrl = base + "/member/payments/" + paymentId + "/status?payos=cancel";
            PayOsApiClient.PaymentLinkData link = createOrRecoverLink(payment, amount, cancelUrl, returnUrl);
            orderCode = payment.getPayOsOrderCode();
            validateLink(payment.getId(), link, orderCode, amount);
            payment.setPayOsPaymentLinkId(link.linkId());
            payment.setPayOsCheckoutUrl(link.checkoutUrl());
            payment.setPayOsQrCode(blankToNull(link.qrCode()));
            paymentRepository.saveAndFlush(payment);
        }

        return new Checkout(paymentId, payment.getPaymentCode(), true, orderCode,
                payment.getPayOsPaymentLinkId(), payment.getPayOsCheckoutUrl(), payment.getPayOsQrCode(),
                payment.getAmount(), "VND",
                "PayOS uses a real bank transfer. This payment updates automatically after PayOS confirms it by webhook.");
    }

    @Transactional
    public StatusSync syncStatus(long paymentId, UserAccount actor) {
        payments.getPayment(paymentId, actor);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS
                && "PAYOS".equals(payment.getSettlementProvider())) {
            return new StatusSync(paymentId, true, "PAID", "PayOS payment was already recorded.");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || payment.getPayOsPaymentLinkId() == null || payment.getPayOsPaymentLinkId().isBlank()
                || payment.getPayOsOrderCode() == null) {
            throw new BusinessRuleException("This payment has no pending PayOS checkout to verify.", "PAYOS_PAYMENT_NOT_AVAILABLE");
        }
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "PayOS status verification is disabled.");
        }

        int expectedAmount = wholeVnd(payment.getAmount());
        PayOsApiClient.PaymentLinkData provider = api.getPaymentLink(payment.getPayOsPaymentLinkId());
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS could not find this payment link.");
        }
        if (!payment.getPayOsPaymentLinkId().equals(provider.linkId())
                || provider.orderCode() == null || provider.orderCode().longValue() != payment.getPayOsOrderCode()
                || provider.amount() == null || provider.amount() != expectedAmount) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS returned status for different payment details.");
        }

        String providerStatus = provider.status() == null ? "UNKNOWN" : provider.status().toUpperCase(Locale.ROOT);
        if (!"PAID".equals(providerStatus)) {
            return new StatusSync(paymentId, false, providerStatus, "PayOS has not confirmed this transfer yet.");
        }
        if (provider.amountPaid() == null || provider.amountPaid() != expectedAmount
                || provider.amountRemaining() == null || provider.amountRemaining() != 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS payment totals do not match this order.");
        }

        String reference = transactionReference(provider.transactions(), expectedAmount, payment.getPayOsPaymentLinkId());
        LocalDateTime transactionTime = transactionTime(provider.transactions());
        verification.confirmPayOsPayment(paymentId, reference, transactionTime);
        return new StatusSync(paymentId, true, providerStatus, "PayOS confirmed and recorded this transfer.");
    }

    private static void requireEligiblePayment(Payment payment) {
        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || "SIMULATED".equals(payment.getSettlementType()) || payment.getSimulatedTransactionId() != null
                || "SPORTIFY_SIMULATOR".equals(payment.getSettlementProvider())) {
            throw new BusinessRuleException("Only pending bank-transfer payments can use PayOS checkout.", "PAYOS_PAYMENT_NOT_AVAILABLE");
        }
    }

    private static int wholeVnd(BigDecimal value) {
        try {
            if (value == null || value.signum() <= 0) throw new ArithmeticException("non-positive");
            return value.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException invalid) {
            throw new BusinessRuleException("PayOS requires a positive whole-number VND amount within its integer limit.",
                    "PAYOS_AMOUNT_INVALID");
        }
    }

    private long reservePayOsOrderCode(Payment payment) {
        long orderCode = ThreadLocalRandom.current().nextLong(1_000_000_000L, Integer.MAX_VALUE);
        payment.setPayOsOrderCode(orderCode);
        paymentRepository.saveAndFlush(payment);
        return orderCode;
    }

    private PayOsApiClient.PaymentLinkData createOrRecoverLink(Payment payment, int amount,
                                                               String cancelUrl, String returnUrl) {
        for (int attempt = 0; attempt < 4; attempt++) {
            long orderCode = payment.getPayOsOrderCode();
            String description = shortDescription(orderCode);
            String signature = api.createPaymentSignature(orderCode, amount, description, cancelUrl, returnUrl);
            PayOsApiClient.CreatePaymentLinkRequest request = new PayOsApiClient.CreatePaymentLinkRequest(
                    orderCode, amount, description, cancelUrl, returnUrl, signature);
            try {
                return api.createPaymentLink(request);
            } catch (ResponseStatusException createFailure) {
                PayOsApiClient.PaymentLinkData existing = null;
                try {
                    existing = api.getPaymentLink(Long.toString(orderCode));
                } catch (ResponseStatusException lookupFailure) {
                    if (createFailure.getStatusCode() != HttpStatus.CONFLICT) throw createFailure;
                }
                if (matchesExpectedOrder(existing, orderCode, amount, description)) return existing;
                if (createFailure.getStatusCode() != HttpStatus.CONFLICT) throw createFailure;

                // A different app/database may already use this numeric order code on the shared PayOS channel.
                // Move this still-unlinked payment to a new code, then retry. The description lets a lost create
                // response recover only the link that this checkout itself requested.
                payment.setPayOsOrderCode(ThreadLocalRandom.current().nextLong(1_000_000_000L, Integer.MAX_VALUE));
                paymentRepository.saveAndFlush(payment);
            }
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "PayOS could not allocate a unique order code. Retry the checkout.");
    }

    private static boolean matchesExpectedOrder(PayOsApiClient.PaymentLinkData link, long orderCode,
                                                int amount, String description) {
        return link != null && link.orderCode() != null && link.orderCode() == orderCode
                && link.amount() != null && link.amount() == amount
                && (link.description() == null || matchesProviderDescription(description, link.description()));
    }

    private static String shortDescription(long orderCode) {
        return "S" + Long.toString(orderCode, 36).toUpperCase(Locale.ROOT);
    }

    private void validateLink(long paymentId, PayOsApiClient.PaymentLinkData link, long orderCode, int amount) {
        String expectedDescription = shortDescription(orderCode);
        boolean linkIdPresent = link != null && link.linkId() != null && !link.linkId().isBlank();
        boolean checkoutUrlPresent = link != null && link.checkoutUrl() != null && !link.checkoutUrl().isBlank();
        boolean orderCodeMatches = link != null && link.orderCode() != null && link.orderCode() == orderCode;
        boolean amountMatches = link != null && link.amount() != null && link.amount() == amount;
        boolean descriptionMatches = link != null && matchesProviderDescription(expectedDescription, link.description());
        if (!linkIdPresent || !checkoutUrlPresent || !orderCodeMatches || !amountMatches || !descriptionMatches) {
            log.warn("PayOS create-link response did not match local payment {}: linkIdPresent={}, checkoutUrlPresent={}, "
                            + "orderCodeMatches={}, amountMatches={}, descriptionMatches={}",
                    paymentId, linkIdPresent, checkoutUrlPresent, orderCodeMatches, amountMatches, descriptionMatches);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS returned a payment link for different order details.");
        }
        try {
            URI uri = URI.create(link.checkoutUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"pay.payos.vn".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("unexpected checkout host");
            }
        } catch (RuntimeException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS returned an invalid checkout URL.");
        }
        if (link.qrCode() != null && link.qrCode().length() > 1500) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PayOS returned an oversized QR payload.");
        }
    }

    /**
     * PayOS may prepend its own transfer reference to the merchant description. The unique local token
     * remains at the end, so require either an exact match or an alphanumeric provider prefix followed by it.
     */
    private static boolean matchesProviderDescription(String expected, String actual) {
        if (expected == null || actual == null) return false;
        if (expected.equals(actual)) return true;
        String suffix = " " + expected;
        if (!actual.endsWith(suffix)) return false;
        String providerReference = actual.substring(0, actual.length() - suffix.length());
        return providerReference.matches("[A-Za-z0-9]{1,32}");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String transactionReference(JsonNode transactions, int expectedAmount, String fallback) {
        String reference = null;
        for (JsonNode transaction : transactionNodes(transactions)) {
            JsonNode amount = transaction.get("amount");
            if (amount != null && amount.canConvertToLong() && amount.longValue() != expectedAmount) continue;
            String candidate = transaction.path("reference").asText("").trim();
            if (!candidate.isBlank()) reference = candidate;
        }
        return reference == null ? fallback : reference;
    }

    private static LocalDateTime transactionTime(JsonNode transactions) {
        String value = null;
        for (JsonNode transaction : transactionNodes(transactions)) {
            String candidate = transaction.path("transactionDateTime").asText("").trim();
            if (!candidate.isBlank()) value = candidate;
        }
        if (value != null) {
            try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
            catch (RuntimeException ignored) { }
            try { return OffsetDateTime.parse(value).toLocalDateTime(); }
            catch (RuntimeException ignored) { }
        }
        return LocalDateTime.now();
    }

    private static java.util.List<JsonNode> transactionNodes(JsonNode transactions) {
        if (transactions == null || transactions.isNull()) return java.util.List.of();
        java.util.List<JsonNode> result = new java.util.ArrayList<>();
        if (transactions.isArray()) transactions.forEach(result::add);
        else if (transactions.isObject()) transactions.elements().forEachRemaining(result::add);
        return result;
    }
}
