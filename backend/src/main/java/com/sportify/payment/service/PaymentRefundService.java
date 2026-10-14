package com.sportify.payment.service;

import com.sportify.payment.entity.Payment;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Payment side of a completed refund (BE-21, F3-07). Called by the refund flow inside its own transaction,
 * after it has locked the payment row (lock order: payment first, then the registration and its bookings).
 */
public interface PaymentRefundService {

    /** 409 code: the payment is already REFUNDED, or already has a live refund request. */
    String CODE_PAYMENT_ALREADY_REFUNDED = "PAYMENT_ALREADY_REFUNDED";

    /** 409 code: the payment is missing or not SUCCESS, so there is nothing to refund. */
    String CODE_PAYMENT_NOT_REFUNDABLE = "PAYMENT_NOT_REFUNDABLE";

    /**
     * Moves a SUCCESS payment to REFUNDED, appends a note with the refund code and amount, and marks every
     * PAID or ISSUED invoice of the payment REFUNDED. Must run in the caller's transaction.
     *
     * @throws com.sportify.core.exception.ConflictException PAYMENT_ALREADY_REFUNDED when the payment is
     *         already REFUNDED; PAYMENT_NOT_REFUNDABLE when it is PENDING or FAILED
     */
    @Transactional(propagation = Propagation.MANDATORY)
    Payment markRefunded(Long paymentId, String refundCode, BigDecimal amountRefunded);
}
