package com.sportify.payment.exception;

import com.sportify.core.exception.ConflictException;

/**
 * The amount the front desk received differs from the amount of the payment request (S3-Mismatch).
 * The payment stays PENDING; the confirm transaction still commits the note that records the mismatch.
 */
public class PaymentAmountMismatchException extends ConflictException {

    public static final String CODE = "PAYMENT_AMOUNT_MISMATCH";

    public PaymentAmountMismatchException(String message) {
        super(message, CODE);
    }
}
