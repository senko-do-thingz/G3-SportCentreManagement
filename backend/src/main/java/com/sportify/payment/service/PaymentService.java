package com.sportify.payment.service;

import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

public interface PaymentService {

    Set<PaymentMethod> MEMBER_ALLOWED_PAYMENT_METHODS = Set.of(
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.CASH
    );

    Set<PaymentMethod> SUPPORTED_PAYMENT_METHODS = Set.of(
            PaymentMethod.CASH,
            PaymentMethod.BANK_TRANSFER
    );

    /**
     * Methods an item activation may record. WALLET only reaches activation through WalletPaymentService, which debits
     * the wallet in the same transaction; staff confirmation, desk sales and method changes still use
     * SUPPORTED_PAYMENT_METHODS, so they can never select WALLET.
     */
    Set<PaymentMethod> SETTLEMENT_PAYMENT_METHODS = Set.of(
            PaymentMethod.CASH,
            PaymentMethod.BANK_TRANSFER,
            PaymentMethod.WALLET
    );

    @Transactional(propagation = Propagation.MANDATORY)
    Payment createPendingPayment(MemberProfile member, BigDecimal amount, PaymentMethod method);

    /**
     * Creates a SUCCESS payment for money taken at the desk (BE-19), with payment_time now, the method, the reference
     * and the staff user who took it. The caller links it to the card or registration and issues the receipt.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Payment recordDeskPayment(MemberProfile member, BigDecimal amount, PaymentMethod method, String referenceCode,
                              UserAccount confirmedBy);

    @Transactional(propagation = Propagation.MANDATORY)
    Payment markPaymentSuccess(Long paymentId, UserAccount confirmedBy);

    @Transactional(propagation = Propagation.MANDATORY)
    Payment markPaymentSuccess(Long paymentId, UserAccount confirmedBy, PaymentMethod actualMethod);

    @Transactional(propagation = Propagation.MANDATORY)
    Payment markPaymentFailed(Long paymentId, String reason);

    @Transactional(propagation = Propagation.MANDATORY)
    Payment markPaymentCancelled(Long paymentId, String reason);

    /**
     * Locks the payment row (pessimistic write) for the rest of the caller's transaction. Card and package
     * activation and cancellation call it before they read the card or registration (lock order: payment first).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Payment lockPayment(Long paymentId);

    PaymentResponse getPayment(Long id, UserAccount actor);

    PaymentResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request, UserAccount actor);

    PaymentResponse submitForVerification(Long id, UserAccount actor);

    PaymentResponse cancelPayment(Long id, UserAccount actor);
}
