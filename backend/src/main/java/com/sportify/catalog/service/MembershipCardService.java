package com.sportify.catalog.service;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.identity.entity.UserAccount;

import java.util.List;

public interface MembershipCardService {
    List<CardTierResponse> getAllActiveTiers();
    MemberCardResponse purchaseCard(MemberCardPurchaseRequest request, UserAccount actor);
    MemberCardResponse activateCard(Long cardId, UserAccount actor);
    MemberCardResponse cancelCard(Long cardId, UserAccount actor);
    List<MemberCardResponse> getMyCards(UserAccount currentUser);
    List<MemberCardResponse> getMemberCards(Long memberId);
    int getApplicableDiscountPercentage(Long memberId, int packageDurationDays);
}
