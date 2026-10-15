package com.sportify.catalog.service;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundQuoteResponse;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

/**
 * Package refunds (Flow 3: F3-07 Refund Requests, S3-RefundSubmitted, S3-RefundReview, S3-RefundApproved,
 * S3-RefundRejected, S3-RefundDone). Since BE-21 every new refund is linked to the SUCCESS payment that bought the
 * registration, and a payment can have only one live refund (PENDING, APPROVED or COMPLETED).
 */
public interface RefundService {

    /**
     * Suggested pro-rated refund of a registration. Owner member or staff. Same 400, 403 and 409 rules as submit,
     * except that a registration with every session used answers a suggested amount of 0.00 instead of 400.
     */
    RefundQuoteResponse quoteRefund(Long registrationId, UserAccount actor);

    RefundResponse submitRefund(RefundCreateRequest request, UserAccount actor);
    RefundResponse reviewRefund(Long refundId, RefundReviewRequest request, UserAccount manager);
    RefundResponse completeRefund(Long refundId, UserAccount actor);
    List<RefundResponse> getPendingRefunds();
    List<RefundResponse> getMyRefunds(UserAccount currentUser);
    List<RefundResponse> getMemberRefunds(Long memberId);
}
