package com.sportify.payment.service;

import com.sportify.payment.entity.Payment;

/**
 * Lets a payment channel (Sportify demo QR today, the VietQR gateway once PR #325 is merged) refuse a cancellation
 * after its own checkout or settlement has started. Every method is called while the payment row is already locked
 * (payment-first lock order), inside the cancelling transaction.
 */
public interface PaymentCancellationGuard {

    /** Returns why the payment can no longer be cancelled, or {@code null} when this channel has nothing in progress. */
    String blockedReason(Payment payment);

    /** Closes this channel's still-open checkout of a payment that is being cancelled, so it cannot be paid later. */
    default void onCancelled(Payment payment) { }
}
