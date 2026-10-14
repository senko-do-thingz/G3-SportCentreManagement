package com.sportify.payment.service;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.dto.MemberPaymentResponse;
import com.sportify.payment.entity.PaymentStatus;

/**
 * Read side of payments and receipts (Flow 3: F3-03 My Payments and Receipts, S3-PaidHistory,
 * S3-CardReceipt, S3-MemberReceipt, S3-SingleReceipt).
 *
 * Visibility rule for every call: a member sees only their own payments and invoices, staff
 * (RECEPTIONIST, MANAGER) see every invoice. A member who asks for an invoice of another member
 * gets 403 (AccessDeniedException); an id that does not exist gets 404.
 */
public interface ReceiptService {

    int DEFAULT_PAGE_SIZE = 20;
    int MAX_PAGE_SIZE = 100;

    /**
     * Payments of the signed-in member, newest first (created_at, then id, both descending).
     *
     * @param status optional filter, null means every status
     */
    PageResponse<MemberPaymentResponse> listMyPayments(PaymentStatus status, int page, int size, UserAccount actor);

    /** Invoice header and lines. Owner member or staff. */
    InvoiceResponse getInvoice(Long invoiceId, UserAccount actor);

    /** The invoice issued for a payment. Owner member or staff; 404 when the payment has no invoice yet. */
    InvoiceResponse getInvoiceByPayment(Long paymentId, UserAccount actor);
}
