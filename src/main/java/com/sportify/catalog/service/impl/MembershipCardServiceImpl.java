package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.MembershipCardTierRepository;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipCardServiceImpl implements MembershipCardService {

    private final MembershipCardTierRepository tierRepository;
    private final MemberCardRepository memberCardRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CardTierResponse> getAllActiveTiers() {
        return tierRepository.findByIsActiveTrue().stream()
                .map(this::mapToTierResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MemberCardResponse purchaseCard(MemberCardPurchaseRequest request, UserAccount actor) {
        Long targetMemberId;
        if (actor.getRole() != null && "MEMBER".equals(actor.getRole().getCode())) {
            targetMemberId = actor.getId();
        } else {
            targetMemberId = request.getMemberId() != null ? request.getMemberId() : actor.getId();
        }

        MemberProfile member = memberProfileRepository.findById(targetMemberId).orElse(null);
        if (member == null) {
            UserAccount targetUser = userRepository.findById(targetMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetMemberId));
            Long nextSeq = memberProfileRepository.getNextMemberCode();
            String memberCode = codeFormatter.formatMemberCode(nextSeq);
            member = MemberProfile.builder()
                    .userAccount(targetUser)
                    .memberCode(memberCode)
                    .currentLevel("BEGINNER")
                    .joinedOn(LocalDate.now(clock))
                    .build();
            member = memberProfileRepository.save(member);
        }

        MembershipCardTier tier = tierRepository.findById(request.getTierId())
                .orElseThrow(() -> new ResourceNotFoundException("Card tier not found for id: " + request.getTierId()));

        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today;
        LocalDate endDate = null;

        if (tier.getDurationMonths() > 0) {
            // Check if member already has active card of this tier for consecutive extension
            List<MemberCard> activeCards = memberCardRepository.findActiveCardsForMember(member.getId(), CardStatus.ACTIVE, today);
            LocalDate extensionBaseDate = today;
            for (MemberCard card : activeCards) {
                if (card.getTier().getId().equals(tier.getId()) && card.getEndDate() != null && card.getEndDate().isAfter(extensionBaseDate)) {
                    extensionBaseDate = card.getEndDate();
                }
            }
            endDate = extensionBaseDate.plusMonths(tier.getDurationMonths());
        }

        long seq = memberCardRepository.getNextCardCodeSequence();
        String cardCode = codeFormatter.formatCardCode(seq);

        MemberCard card = MemberCard.builder()
                .cardCode(cardCode)
                .member(member)
                .tier(tier)
                .startDate(startDate)
                .endDate(endDate)
                .status(CardStatus.ACTIVE)
                .pricePaid(tier.getPrice())
                .build();

        card = memberCardRepository.save(card);
        return mapToCardResponse(card);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCardResponse> getMyCards(UserAccount currentUser) {
        return getMemberCards(currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberCardResponse> getMemberCards(Long memberId) {
        return memberCardRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(this::mapToCardResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public int getApplicableDiscountPercentage(Long memberId, int packageDurationDays) {
        // Single visit packages (<= 1 day) are strictly excluded from discounts
        if (packageDurationDays <= 1) {
            return 0;
        }

        LocalDate today = LocalDate.now(clock);
        List<MemberCard> activeCards = memberCardRepository.findActiveCardsForMember(memberId, CardStatus.ACTIVE, today);
        if (activeCards.isEmpty()) {
            return 0;
        }

        // Return highest discount from active cards (no stacking)
        return activeCards.stream()
                .mapToInt(c -> c.getTier().getDiscountPercentage())
                .max()
                .orElse(0);
    }

    private CardTierResponse mapToTierResponse(MembershipCardTier tier) {
        return CardTierResponse.builder()
                .id(tier.getId())
                .code(tier.getCode())
                .name(tier.getName())
                .price(tier.getPrice())
                .durationMonths(tier.getDurationMonths())
                .discountPercentage(tier.getDiscountPercentage())
                .description(tier.getDescription())
                .isActive(tier.getIsActive())
                .build();
    }

    private MemberCardResponse mapToCardResponse(MemberCard card) {
        return MemberCardResponse.builder()
                .id(card.getId())
                .cardCode(card.getCardCode())
                .memberId(card.getMember().getId())
                .memberCode(card.getMember().getMemberCode())
                .memberFullName(card.getMember().getUserAccount().getFullName())
                .tierId(card.getTier().getId())
                .tierCode(card.getTier().getCode())
                .tierName(card.getTier().getName())
                .discountPercentage(card.getTier().getDiscountPercentage())
                .startDate(card.getStartDate())
                .endDate(card.getEndDate())
                .status(card.getStatus())
                .pricePaid(card.getPricePaid())
                .createdAt(card.getCreatedAt())
                .build();
    }
}
