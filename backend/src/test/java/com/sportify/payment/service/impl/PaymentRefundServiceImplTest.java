package com.sportify.payment.service.impl;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRefundServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;

    private PaymentRefundServiceImpl service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new PaymentRefundServiceImpl(paymentRepository, invoiceRepository);
        payment = Payment.builder().id(70L).paymentCode("PAY-0070").amount(new BigDecimal("380000.00"))
                .paymentStatus(PaymentStatus.SUCCESS).notes("Confirmed by user id 7").build();
    }

    @Test
    @DisplayName("SUCCESS payment becomes REFUNDED, PAID invoice becomes REFUNDED, CANCELLED invoice untouched, note appended")
    void markRefunded_Success() {
        Invoice paid = Invoice.builder().id(1L).status(InvoiceStatus.PAID).build();
        Invoice cancelled = Invoice.builder().id(2L).status(InvoiceStatus.CANCELLED).build();
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findByPaymentId(70L)).thenReturn(List.of(paid, cancelled));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = service.markRefunded(70L, "RFD-0001", new BigDecimal("266000.00"));

        assertThat(result.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getNotes()).endsWith("Refunded by RFD-0001: 266000.00");
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        verify(invoiceRepository).save(paid);
        verify(invoiceRepository, never()).save(cancelled);
    }

    @Test
    @DisplayName("Already REFUNDED: 409 PAYMENT_ALREADY_REFUNDED, nothing saved")
    void markRefunded_AlreadyRefunded_Conflict() {
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.markRefunded(70L, "RFD-0002", BigDecimal.ONE))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo(PaymentRefundService.CODE_PAYMENT_ALREADY_REFUNDED));
        verify(paymentRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"PENDING", "FAILED"})
    @DisplayName("PENDING or FAILED: 409 PAYMENT_NOT_REFUNDABLE")
    void markRefunded_NotSuccess_Conflict(PaymentStatus status) {
        payment.setPaymentStatus(status);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.markRefunded(70L, "RFD-0003", BigDecimal.ONE))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo(PaymentRefundService.CODE_PAYMENT_NOT_REFUNDABLE));
    }

    @Test
    @DisplayName("Unknown payment: 404")
    void markRefunded_NotFound() {
        when(paymentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRefunded(99L, "RFD-0004", BigDecimal.ONE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01"})
    void markRefunded_NonPositiveAmount_RejectsBeforeMutation(String amount) {
        assertThatThrownBy(() -> service.markRefunded(70L, "RFD-0001", new BigDecimal(amount)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Amount refunded must be greater than zero");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verifyNoInteractions(paymentRepository, invoiceRepository);
    }

    @Test
    void markRefunded_IssuedInvoice_RefundedAndCancelledUnchanged() {
        Invoice issued = Invoice.builder().id(1L).status(InvoiceStatus.ISSUED).build();
        Invoice cancelled = Invoice.builder().id(2L).status(InvoiceStatus.CANCELLED).build();
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findByPaymentId(70L)).thenReturn(List.of(issued, cancelled));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.markRefunded(70L, "RFD-0001", BigDecimal.ONE).getPaymentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(issued.getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
        assertThat(cancelled.getStatus()).isEqualTo(InvoiceStatus.CANCELLED);
        verify(invoiceRepository).save(issued);
        verify(invoiceRepository, never()).save(cancelled);
    }

    @Test
    @DisplayName("SIMULATED payment: 409 SIMULATED_PAYMENT_NOT_REFUNDABLE, nothing saved")
    void markRefunded_SimulatedPayment_Conflict() {
        payment.setSettlementType("SIMULATED");
        payment.setSettlementProvider("SPORTIFY_SIMULATOR");
        payment.setSimulatedTransactionId(901L);
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.markRefunded(70L, "RFD-0005", BigDecimal.ONE))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode())
                        .isEqualTo(PaymentRefundService.CODE_SIMULATED_PAYMENT_NOT_REFUNDABLE));
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(invoiceRepository);
    }
}
