package com.sportify.catalog.service;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.PaymentMethod;

import java.util.List;

public interface MembershipCardService {
    List<CardTierResponse> getAllActiveTiers();
    MemberCardResponse purchaseCard(MemberCardPurchaseRequest request, UserAccount actor);
    MemberCardResponse activateCard(Long cardId, UserAccount actor);
    MemberCardResponse activateCard(Long cardId, UserAccount actor, PaymentMethod actualMethod);

    /**
     * Activation step only: PENDING_PAYMENT -> ACTIVE and the payment SUCCESS, without the invoice and the
     * PAYMENT_CONFIRMED log. Must run inside a transaction whose caller then calls
     * PaymentVerificationService.recordPaymentConfirmed. {@link #activateCard} does both.
     */
    MemberCardResponse activateCardForPaymentConfirmation(Long cardId, UserAccount actor, PaymentMethod actualMethod);
    MemberCardResponse cancelCard(Long cardId, UserAccount actor);
    List<MemberCardResponse> getMyCards(UserAccount currentUser);
    List<MemberCardResponse> getMemberCards(Long memberId);
    int getApplicableDiscountPercentage(Long memberId, int packageDurationDays);
}
