package com.sportify.payment.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.FailPaymentRequest;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Front desk payment verification (Flow 3: F3-04 Payment Requests, F3-05 Verify and Record Payment,
 * F3-06 Payment Confirmed and Receipt, S3-Mismatch Payment Needs Review).
 */
public interface PaymentVerificationService {

    int DEFAULT_PAGE_SIZE = 20;
    int MAX_PAGE_SIZE = 100;

    /**
     * Payment requests, PENDING first. {@code from} and {@code to} are inclusive dates on created_at.
     */
    PageResponse<StaffPaymentResponse> listPayments(PaymentStatus status, PaymentMethod method, String search,
                                                    LocalDate from, LocalDate to, int page, int size);

    /**
     * Confirms a PENDING payment when the amount received equals the amount: the payment becomes SUCCESS,
     * the linked card or package registration becomes ACTIVE and a PAID invoice is issued.
     * A different amount answers 409 PAYMENT_AMOUNT_MISMATCH, keeps the payment PENDING and records a note.
     */
    StaffPaymentResponse confirmPayment(Long paymentId, ConfirmPaymentRequest request, UserAccount actor);

    /**
     * Receipt of a payment that has just become SUCCESS: issues the PAID invoice with its line and writes the
     * PAYMENT_CONFIRMED activity log. Both confirmation paths call it, PUT /payments/{id}/confirm and the
     * PUT /membership-cards/{id}/activate and PUT /packages/registrations/{id}/activate endpoints, so every
     * confirmed payment has exactly one invoice and one PAYMENT_CONFIRMED row. Must run in the caller's
     * transaction (the implementation declares Propagation.MANDATORY).
     *
     * @param note front desk note to keep in the log details, or null
     */
    Invoice recordPaymentConfirmed(Long paymentId, UserAccount actor, String note);

    /**
     * Money taken at the desk when staff sell a card or a package (BE-19, F1-13 and F1-14). Checks the method,
     * the reference (required except for CASH) and the amount received (cash may be more, the change is noted;
     * any other method must match), then creates a SUCCESS payment. The caller links it to the item and calls
     * {@link #recordPaymentConfirmed} for the invoice. Must run in the caller's transaction.
     *
     * @param method         payment method; CASH when null
     * @param amountReceived money handed over; the amount itself when null
     */
    Payment recordDeskPayment(MemberProfile member, BigDecimal amount, PaymentMethod method, String referenceCode,
                              BigDecimal amountReceived, UserAccount actor);

    /**
     * Marks a PENDING payment FAILED and cancels the card or package request it pays for.
     */
    StaffPaymentResponse failPayment(Long paymentId, FailPaymentRequest request, UserAccount actor);

    /**
     * The invoice issued for a payment. Staff see every invoice, a member only the invoices of their own payments.
     */
    InvoiceResponse getInvoiceByPayment(Long paymentId, UserAccount actor);
}
