package com.sportify.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.identity.entity.UserAccount;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOsCheckoutServiceTest {
    @Mock private PaymentService payments;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PayOsApiClient api;
    @Mock private PaymentVerificationService verification;

    private final PayOsProperties properties = new PayOsProperties();
    private PayOsCheckoutService service;

    @BeforeEach
    void setUp() {
        properties.setEnabled(true);
        properties.setFrontendBaseUrl("http://localhost:5173");
        service = new PayOsCheckoutService(payments, paymentRepository, api, properties, verification);
    }

    @Test
    void anyMemberPendingBankTransferCanCreatePayOsCheckout() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(payment));
        when(api.createPaymentSignature(anyLong(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn("signature");
        when(api.createPaymentLink(any())).thenAnswer(invocation -> {
            PayOsApiClient.CreatePaymentLinkRequest request = invocation.getArgument(0);
            return new PayOsApiClient.PaymentLinkData("link-71", "link-71", request.orderCode(),
                    (long) request.amount(), request.description(), "PENDING",
                    "https://pay.payos.vn/web/link-71", "vietqr-payload");
        });

        PayOsCheckoutService.Checkout checkout = service.checkout(71L, UserAccount.builder().id(12L).build());

        assertThat(checkout.enabled()).isTrue();
        assertThat(checkout.paymentLinkId()).isEqualTo("link-71");
        assertThat(payment.getPayOsPaymentLinkId()).isEqualTo("link-71");
        assertThat(checkout.orderCode()).isEqualTo(payment.getPayOsOrderCode()).isNotEqualTo(payment.getId());
        verify(paymentRepository, times(2)).saveAndFlush(payment);
    }

    @Test
    void retriesWithANewOrderCodeWhenTheSharedPayOsChannelAlreadyHasTheFirstCode() {
        Payment payment = pendingPayment();
        when(paymentRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(payment));
        when(api.createPaymentSignature(anyLong(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn("signature");
        when(api.createPaymentLink(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "duplicate order code"))
                .thenAnswer(invocation -> {
                    PayOsApiClient.CreatePaymentLinkRequest request = invocation.getArgument(0);
                    return new PayOsApiClient.PaymentLinkData("link-71", "link-71", request.orderCode(),
                            (long) request.amount(), request.description(), "PENDING",
                            "https://pay.payos.vn/web/link-71", "vietqr-payload");
                });

        PayOsCheckoutService.Checkout checkout = service.checkout(71L, UserAccount.builder().id(12L).build());

        assertThat(checkout.enabled()).isTrue();
        assertThat(payment.getPayOsOrderCode()).isEqualTo(checkout.orderCode());
        verify(api).getPaymentLink(anyString());
    }

    @Test
    void verifiedPayOsStatusCanRecoverWhenTheBrowserReturnsWithoutAWebhook() {
        Payment payment = pendingPayment();
        payment.setPayOsPaymentLinkId("link-71");
        payment.setPayOsOrderCode(71L);
        when(paymentRepository.findByIdForUpdate(71L)).thenReturn(Optional.of(payment));
        when(api.getPaymentLink("link-71")).thenReturn(new PayOsApiClient.PaymentLinkData(
                "link-71", "link-71", 71L, 4750L, "S1Z", "PAID", "https://pay.payos.vn/web/link-71", "vietqr-payload",
                4750L, 0L, new ObjectMapper().valueToTree(List.of(Map.of(
                "amount", 4750L, "reference", "BANK-REF-71", "transactionDateTime", "2026-11-17 11:30:00")))));

        PayOsCheckoutService.StatusSync status = service.syncStatus(71L, UserAccount.builder().id(12L).build());

        assertThat(status.settled()).isTrue();
        assertThat(status.providerStatus()).isEqualTo("PAID");
        verify(verification).confirmPayOsPayment(71L, "BANK-REF-71", LocalDateTime.of(2026, 11, 17, 11, 30));
    }

    private static Payment pendingPayment() {
        return Payment.builder().id(71L).paymentCode("PAY-1071").amount(new BigDecimal("4750.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER).paymentStatus(PaymentStatus.PENDING)
                .payOsOrderCode(null).build();
    }
}
