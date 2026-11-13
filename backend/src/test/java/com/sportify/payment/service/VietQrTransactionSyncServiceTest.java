package com.sportify.payment.service;

import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.config.VietQrGatewayProperties;
import com.sportify.payment.entity.*;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VietQrTransactionSyncServiceTest {
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentVerificationService verification = mock(PaymentVerificationService.class);
    private final BankTransferProperties beneficiary = new BankTransferProperties();
    private final VietQrGatewayProperties gateway = new VietQrGatewayProperties();
    private final VietQrTransactionSyncService service;

    VietQrTransactionSyncServiceTest() {
        beneficiary.setAccountNumber("123456789");
        service = new VietQrTransactionSyncService(payments, verification, beneficiary, gateway, Clock.systemUTC());
    }

    private Payment payment() {
        return Payment.builder().id(7L).paymentCode("PAY-7").amount(new BigDecimal("1000"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER).paymentStatus(PaymentStatus.PENDING)
                .vietQrOrderId("7").vietQrContent("SP7").vietQrEnvironment("SANDBOX").build();
    }

    private VietQrTransactionCallback callback(String transactionId) {
        return new VietQrTransactionCallback("123456789", new BigDecimal("1000"), "C", "SP7",
                transactionId, 1700000000000L, "REF-1", "7", null, null, null, null, null);
    }

    @Test void settlesPendingPaymentOnceAndAcceptsExactRetry() {
        Payment payment = payment();
        when(payments.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));
        assertThat(service.accept(callback("TX-1"))).isEqualTo("PAY-7");
        verify(verification).confirmVietQrPayment(eq(7L), eq("REF-1"), eq("TX-1"), any(), eq("SANDBOX"));
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setSettlementProvider("GATEWAY");
        payment.setVietQrTransactionId("TX-1");
        payment.setReferenceCode("REF-1");
        assertThat(service.accept(callback("TX-1"))).isEqualTo("PAY-7");
        verifyNoMoreInteractions(verification);
        assertThatThrownBy(() -> service.accept(callback("TX-2")))
                .isInstanceOf(VietQrCallbackException.class);
    }

    @Test void rejectsEnvironmentAndAmountMismatchWithoutSettlement() {
        Payment payment = payment();
        when(payments.findByIdForUpdate(7L)).thenReturn(Optional.of(payment));
        gateway.setEnvironment("production");
        assertThatThrownBy(() -> service.accept(callback("TX-1")))
                .isInstanceOf(VietQrCallbackException.class).hasMessageContaining("environment");
        gateway.setEnvironment("sandbox");
        payment.setAmount(new BigDecimal("2000"));
        assertThatThrownBy(() -> service.accept(callback("TX-1")))
                .isInstanceOf(VietQrCallbackException.class).hasMessageContaining("amount");
        verifyNoInteractions(verification);
    }
}
