package com.sportify.payment.service;

import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.config.VietQrGatewayProperties;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class VietQrTransactionSyncService {

    private final PaymentRepository payments;
    private final PaymentVerificationService verification;
    private final BankTransferProperties beneficiary;
    private final VietQrGatewayProperties gateway;
    private final Clock clock;

    @Transactional
    public String accept(VietQrTransactionCallback callback) {
        if (callback == null || blank(callback.orderId()) || blank(callback.transactionId())
                || blank(callback.referenceNumber()) || blank(callback.bankAccount())
                || blank(callback.content()) || callback.amount() == null || callback.transactionTime() == null) {
            throw invalid("INVALID_CALLBACK", "Required transaction fields are missing.");
        }
        if (callback.orderId().length() > 13 || callback.transactionId().length() > 100
                || callback.referenceNumber().length() > 100 || callback.content().length() > 23) {
            throw invalid("INVALID_CALLBACK", "Transaction fields exceed the supported length.");
        }
        if (callback.transactionTime() <= 0 || callback.amount().signum() <= 0
                || callback.amount().stripTrailingZeros().scale() > 0) {
            throw invalid("INVALID_CALLBACK", "Transaction amount or time is invalid.");
        }
        if (!"C".equalsIgnoreCase(callback.transType())) {
            throw invalid("INVALID_TRANSACTION_TYPE", "Only incoming credit transactions can settle a payment.");
        }

        long paymentId;
        try {
            paymentId = Long.parseLong(callback.orderId());
        } catch (NumberFormatException invalidOrder) {
            throw invalid("PAYMENT_NOT_FOUND", "The VietQR order ID is not recognized.");
        }
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> invalid("PAYMENT_NOT_FOUND", "The VietQR order ID is not recognized."));
        if (!Objects.equals(callback.orderId(), payment.getVietQrOrderId())) {
            throw invalid("PAYMENT_NOT_FOUND", "The VietQR order ID was not created for this payment.");
        }
        String environment = gateway.isSandbox() ? "SANDBOX" : "PRODUCTION";
        if (!environment.equals(payment.getVietQrEnvironment())) {
            throw invalid("ENVIRONMENT_MISMATCH", "The order belongs to a different settlement environment.");
        }
        if (!gateway.isSandbox() && callback.transactionId().startsWith("LOCAL-SANDBOX-")) {
            throw invalid("INVALID_TRANSACTION_ID", "A local demo transaction cannot settle a production payment.");
        }

        String expectedAccount = VietQrCheckoutService.digitsOnly(beneficiary.getAccountNumber());
        if (!expectedAccount.equals(VietQrCheckoutService.digitsOnly(callback.bankAccount()))) {
            throw invalid("BENEFICIARY_MISMATCH", "The callback beneficiary account does not match this merchant.");
        }
        if (!Objects.equals(payment.getVietQrContent(), callback.content())) {
            throw invalid("CONTENT_MISMATCH", "The callback transfer content does not match this order.");
        }
        if (callback.amount().compareTo(payment.getAmount()) != 0) {
            throw invalid("AMOUNT_MISMATCH", "The callback amount does not match this order.");
        }

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS
                && (callback.transactionId().startsWith("LOCAL-SANDBOX-")
                    ? "LOCAL_DEMO" : "GATEWAY").equals(payment.getSettlementProvider())
                && environment.equals(payment.getVietQrEnvironment())
                && callback.transactionId().equals(payment.getVietQrTransactionId())
                && callback.referenceNumber().equals(payment.getReferenceCode())) {
            return payment.getPaymentCode();
        }
        if (payments.findByVietQrTransactionId(callback.transactionId()).isPresent()) {
            throw invalid("DUPLICATE_TRANSACTION", "This VietQR transaction was already used for another payment.");
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw invalid("PAYMENT_NOT_PENDING", "The payment is no longer waiting for VietQR settlement.");
        }

        LocalDateTime transactionTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(callback.transactionTime()), clock.getZone());
        verification.confirmVietQrPayment(paymentId, callback.referenceNumber(), callback.transactionId(), transactionTime,
                environment);
        return payment.getPaymentCode();
    }

    private static VietQrCallbackException invalid(String reason, String message) {
        return new VietQrCallbackException(reason, message);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
