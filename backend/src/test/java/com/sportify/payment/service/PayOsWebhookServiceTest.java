package com.sportify.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.payment.config.PayOsProperties;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOsWebhookServiceTest {
    @Mock private PayOsApiClient api;
    @Mock private PaymentRepository payments;
    @Mock private PaymentVerificationService verification;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PayOsProperties properties = new PayOsProperties();
    private PayOsWebhookService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        service = new PayOsWebhookService(api, properties, payments, verification, objectMapper,
                Clock.fixed(Instant.parse("2026-11-17T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void verifiedPaymentWebhookSettlesTheMatchingMemberPayment() {
        Payment payment = pendingPayment();
        JsonNode data = data(4750L, "link-71");
        when(api.verifyWebhook(eq(data), eq("signature"))).thenReturn(true);
        when(payments.findByPayOsOrderCode(71L)).thenReturn(Optional.of(payment));
        when(payments.findByIdForUpdate(71L)).thenReturn(Optional.of(payment));
        when(payments.findByPayOsTransactionReference("BANK-REF-71")).thenReturn(Optional.empty());

        PayOsWebhookService.WebhookResult result = service.accept(event(data));

        assertThat(result.status()).isEqualTo("PAID");
        verify(verification).confirmPayOsPayment(71L, "BANK-REF-71", LocalDateTime.of(2026, 11, 17, 11, 30));
    }

    @Test
    void invalidWebhookSignatureCannotSettlePayment() {
        JsonNode data = data(4750L, "link-71");
        when(api.verifyWebhook(eq(data), eq("signature"))).thenReturn(false);

        assertThatThrownBy(() -> service.accept(event(data)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(payments, verification);
    }

    @Test
    void mismatchedCallbackAmountIsRejectedWithoutSettlingPayment() {
        Payment payment = pendingPayment();
        JsonNode data = data(4751L, "link-71");
        when(api.verifyWebhook(eq(data), eq("signature"))).thenReturn(true);
        when(payments.findByPayOsOrderCode(71L)).thenReturn(Optional.of(payment));
        when(payments.findByIdForUpdate(71L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.accept(event(data)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(verification);
    }

    @Test
    void exactWebhookRetryAcknowledgesAlreadySettledPayment() {
        Payment payment = pendingPayment();
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setSettlementProvider("PAYOS");
        JsonNode data = data(4750L, "link-71");
        when(api.verifyWebhook(eq(data), eq("signature"))).thenReturn(true);
        when(payments.findByPayOsOrderCode(71L)).thenReturn(Optional.of(payment));
        when(payments.findByIdForUpdate(71L)).thenReturn(Optional.of(payment));

        assertThat(service.accept(event(data)).status()).isEqualTo("ALREADY_PROCESSED");
        verifyNoInteractions(verification);
    }

    private Payment pendingPayment() {
        return Payment.builder().id(71L).paymentCode("PAY-1071").amount(new BigDecimal("4750.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER).paymentStatus(PaymentStatus.PENDING)
                .payOsPaymentLinkId("link-71").payOsOrderCode(71L).build();
    }

    private JsonNode data(long amount, String linkId) {
        return objectMapper.valueToTree(Map.of(
                "orderCode", 71L,
                "amount", amount,
                "currency", "VND",
                "reference", "BANK-REF-71",
                "transactionDateTime", "2026-11-17 11:30:00",
                "paymentLinkId", linkId,
                "code", "00"));
    }

    private static PayOsWebhookService.WebhookEvent event(JsonNode data) {
        return new PayOsWebhookService.WebhookEvent("00", "Success", true, data, "signature");
    }
}
