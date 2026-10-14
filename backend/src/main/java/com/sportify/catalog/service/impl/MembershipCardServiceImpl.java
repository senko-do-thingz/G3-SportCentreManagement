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
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.core.common.SecurityUtils;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.service.PaymentService;
import com.sportify.payment.service.PaymentVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MembershipCardServiceImpl implements MembershipCardService {

    private final MembershipCardTierRepository tierRepository;
    private final MemberCardRepository memberCardRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final CodeFormatter codeFormatter;
    private final Clock clock;
    /** Resolved on use: PaymentVerificationService itself depends on this service. */
    private final ObjectProvider<PaymentVerificationService> paymentVerificationService;

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
        if (SecurityUtils.isMember(actor)) {
            targetMemberId = actor.getId();
        } else {
            targetMemberId = request.getMemberId() != null ? request.getMemberId() : actor.getId();
        }

        MemberProfile member = memberProfileRepository.findById(targetMemberId).orElse(null);
        if (member == null) {
            UserAccount targetUser = userRepository.findById(targetMemberId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + targetMemberId));
            if (!SecurityUtils.isMember(targetUser)) {
                throw new BusinessRuleException("Cannot create member profile for non-member user: " + targetMemberId);
            }
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
        if (!Boolean.TRUE.equals(tier.getIsActive())) {
            throw new BusinessRuleException("Card tier is not on sale: " + tier.getCode());
        }

        LocalDate today = LocalDate.now(clock);
        boolean paidAtDesk = SecurityUtils.isStaff(actor);

        // Standard: free and permanent, nothing to pay
        if (tier.getDurationMonths() == null || tier.getDurationMonths() <= 0) {
            return mapToCardResponse(memberCardRepository.save(newCard(member, tier, today, null, CardStatus.ACTIVE, tier.getPrice())));
        }

        // One card per member: a second request cannot be made while one is waiting for payment
        List<MemberCard> pending = memberCardRepository.findByMemberIdAndStatus(member.getId(), CardStatus.PENDING_PAYMENT);
        if (!pending.isEmpty()) {
            throw new BusinessRuleException("A membership card request is already waiting for payment: "
                    + pending.get(0).getCardCode() + ". Pay or cancel it first.");
        }

        CardPlan plan = planCard(member.getId(), tier, today);
        if (!paidAtDesk && plan.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than zero. Attempted card price: " + plan.price());
        }

        CardStatus status = paidAtDesk ? CardStatus.ACTIVE : CardStatus.PENDING_PAYMENT;
        MemberCard card = newCard(member, tier, plan.startDate(), plan.endDate(), status, plan.price());
        if (status == CardStatus.PENDING_PAYMENT) {
            Payment payment = paymentService.createPendingPayment(member, plan.price(), request.getPaymentMethod());
            card.setPayment(payment);
        }
        card = memberCardRepository.save(card);
        if (paidAtDesk && plan.replaces() != null) {
            replace(plan.replaces());
        }
        return mapToCardResponse(card);
    }

    /**
     * The front desk confirms the payment of a card request. The dates are worked out again from what the
     * member holds today, because days may have passed since the request; the price stays as requested.
     */
    @Override
    @Transactional
    public MemberCardResponse activateCard(Long cardId, UserAccount actor) {
        return activateCard(cardId, actor, null);
    }

    @Override
    @Transactional
    public MemberCardResponse activateCard(Long cardId, UserAccount actor, PaymentMethod actualMethod) {
        MemberCard card = activatePending(cardId, actor, actualMethod);
        if (card.getPayment() != null) {
            // Same receipt as PUT /payments/{id}/confirm: one PAID invoice and one PAYMENT_CONFIRMED row.
            paymentVerificationService.getObject().recordPaymentConfirmed(card.getPayment().getId(), actor, null);
        }
        return mapToCardResponse(card);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MemberCardResponse activateCardForPaymentConfirmation(Long cardId, UserAccount actor, PaymentMethod actualMethod) {
        return mapToCardResponse(activatePending(cardId, actor, actualMethod));
    }

    private MemberCard activatePending(Long cardId, UserAccount actor, PaymentMethod actualMethod) {
        MemberCard card = memberCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Member card not found for id: " + cardId));
        if (card.getStatus() != CardStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Cannot activate membership card with status: " + card.getStatus()
                    + ". Only PENDING_PAYMENT cards can be activated.");
        }
        if (actualMethod != null && card.getPayment() == null) {
            throw new BusinessRuleException("Cannot specify payment method for a card with no linked payment");
        }

        LocalDate today = LocalDate.now(clock);
        CardPlan plan = planCard(card.getMember().getId(), card.getTier(), today);
        boolean requestedAsUpgrade = isUpgradePrice(card);
        if (requestedAsUpgrade != (plan.replaces() != null)) {
            throw new BusinessRuleException("The member's card has changed since this request. Cancel it and request the card again.");
        }

        card.setStartDate(plan.startDate());
        card.setEndDate(plan.endDate());
        card.setStatus(CardStatus.ACTIVE);
        card = memberCardRepository.save(card);
        if (card.getPayment() != null) {
            paymentService.markPaymentSuccess(card.getPayment().getId(), actor, actualMethod);
        }
        if (plan.replaces() != null) {
            replace(plan.replaces());
        }
        return card;
    }

    @Override
    @Transactional
    public MemberCardResponse cancelCard(Long cardId, UserAccount actor) {
        MemberCard card = memberCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Member card not found for id: " + cardId));
        boolean isStaff = SecurityUtils.isStaff(actor);
        // MemberProfile shares its primary key with UserAccount (@MapsId user_id), so member id equals user id by design.
        if (!isStaff && !card.getMember().getId().equals(actor.getId())) {
            throw new ResourceNotFoundException("Member card not found for id: " + cardId);
        }
        if (card.getStatus() != CardStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Only a card request that is waiting for payment can be cancelled.");
        }
        card.setStatus(CardStatus.CANCELLED);
        if (card.getPayment() != null) {
            String actorDescription = isStaff ? "staff user id " + actor.getId() : "member user id " + actor.getId();
            paymentService.markPaymentFailed(card.getPayment().getId(),
                    "Payment cancelled due to card cancellation by " + actorDescription);
        }
        return mapToCardResponse(memberCardRepository.save(card));
    }

    /**
     * What buying {@code tier} means today for this member:
     * <ul>
     *   <li>no paid card: a new card from today;</li>
     *   <li>the same tier: a renewal from the day after the last card ends;</li>
     *   <li>a dearer tier while a card is valid: an upgrade from today that keeps the current end date;
     *       the member pays only the price difference and the current card is replaced;</li>
     *   <li>a cheaper tier: it starts the day after the last card ends, full price.</li>
     * </ul>
     * A card lasts {@code durationMonths} months: it ends the day before the same date {@code durationMonths} later.
     */
    private CardPlan planCard(Long memberId, MembershipCardTier tier, LocalDate today) {
        int months = tier.getDurationMonths();
        List<MemberCard> paid = memberCardRepository.findActiveCardsForMember(memberId, CardStatus.ACTIVE, today).stream()
                .filter(c -> c.getTier().getDurationMonths() != null && c.getTier().getDurationMonths() > 0 && c.getEndDate() != null)
                .toList();
        MemberCard current = paid.stream()
                .filter(c -> c.getStartDate() == null || !c.getStartDate().isAfter(today))
                .max(Comparator.comparing(MemberCard::getEndDate))
                .orElse(null);
        MemberCard last = paid.stream().max(Comparator.comparing(MemberCard::getEndDate)).orElse(null);

        if (last == null) {
            return new CardPlan(today, cardEnd(today, months), tier.getPrice(), null);
        }
        if (last.getTier().getId().equals(tier.getId())) {
            LocalDate from = last.getEndDate().plusDays(1);
            return new CardPlan(from, cardEnd(from, months), tier.getPrice(), null);
        }
        if (current != null && tier.getPrice().compareTo(current.getTier().getPrice()) > 0) {
            if (last != current) {
                throw new BusinessRuleException("The current card is already renewed from " + last.getStartDate()
                        + ". The card can be upgraded once that term has started.");
            }
            BigDecimal difference = tier.getPrice().subtract(current.getTier().getPrice());
            return new CardPlan(today, current.getEndDate(), difference, current);
        }
        LocalDate from = last.getEndDate().plusDays(1);
        return new CardPlan(from, cardEnd(from, months), tier.getPrice(), null);
    }

    private static LocalDate cardEnd(LocalDate start, int months) {
        return start.plusMonths(months).minusDays(1);
    }

    /** A card request priced below its tier was requested as an upgrade (the member pays only the difference). */
    private static boolean isUpgradePrice(MemberCard card) {
        return card.getPricePaid() != null && card.getPricePaid().compareTo(card.getTier().getPrice()) < 0;
    }

    private void replace(MemberCard old) {
        old.setStatus(CardStatus.REPLACED);
        memberCardRepository.save(old);
    }

    private MemberCard newCard(MemberProfile member, MembershipCardTier tier, LocalDate start, LocalDate end,
                               CardStatus status, BigDecimal price) {
        long seq = memberCardRepository.getNextCardCodeSequence();
        return MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(seq))
                .member(member)
                .tier(tier)
                .startDate(start)
                .endDate(end)
                .status(status)
                .pricePaid(price)
                .build();
    }

    private record CardPlan(LocalDate startDate, LocalDate endDate, BigDecimal price, MemberCard replaces) {
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
        // Only a paid card whose term has started gives a discount. A member holds one card at a time;
        // max() only guards against old data from before that rule.
        return memberCardRepository.findActiveCardsForMember(memberId, CardStatus.ACTIVE, today).stream()
                .filter(c -> c.getStartDate() == null || !c.getStartDate().isAfter(today))
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
        MemberCardResponse.MemberCardResponseBuilder builder = MemberCardResponse.builder()
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
                .createdAt(card.getCreatedAt());

        if (card.getPayment() != null) {
            builder.paymentId(card.getPayment().getId())
                    .paymentCode(card.getPayment().getPaymentCode());
        }

        return builder.build();
    }
}
