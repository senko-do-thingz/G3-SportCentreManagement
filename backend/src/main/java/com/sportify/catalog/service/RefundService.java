package com.sportify.catalog.service;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

public interface RefundService {
    RefundResponse submitRefund(RefundCreateRequest request, UserAccount actor);
    RefundResponse reviewRefund(Long refundId, RefundReviewRequest request, UserAccount manager);
    RefundResponse completeRefund(Long refundId, UserAccount actor);
    List<RefundResponse> getPendingRefunds();
    List<RefundResponse> getMyRefunds(UserAccount currentUser);
    List<RefundResponse> getMemberRefunds(Long memberId);
}
