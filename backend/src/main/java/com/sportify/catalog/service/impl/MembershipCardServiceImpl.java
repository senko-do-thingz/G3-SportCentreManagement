package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.CardTierResponse;
import com.sportify.catalog.dto.MemberCardPurchaseRequest;
import com.sportify.catalog.dto.MemberCardResponse;
import com.sportify.catalog.dto.MembershipCardTierUpdateRequest;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.MembershipCardTierRepository;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.identity.repository.UserRepository;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.service.PaymentService;
import com.sportify.payment.service.PaymentVerificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.regex.Pattern;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipCardServiceImpl implements MembershipCardService {

    public static final String STANDARD_TIER_CODE = "STANDARD";
    private static final int MAX_CHAIN_DEPTH = 20;

    private final MembershipCardTierRepository tierRepository;
    private final MemberCardRepository memberCardRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;
    private final CodeFormatter codeFormatter;
    private final AuditService auditService;
    private final Clock clock;
    private final EntityManager entityManager;
    /** Resolved on use: PaymentVerificationService itself depends on this service. */
    private final ObjectProvider<PaymentVerificationService> paymentVerificationService;

    /**
     * Determines whether the given tier is the STANDARD tier.
     * Note: Specific SQL Server database collation is unknown (not explicitly configured in migrations),
     * but case-insensitive comparison (equalsIgnoreCase) defensively treats codes like "standard" and "STANDARD"
     * identically, matching case-insensitive database behavior verified by integration tests.
     */
    public static boolean isStandard(MembershipCardTier tier) {
        return tier != null && STANDARD_TIER_CODE.equalsIgnoreCase(tier.getCode());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardTierResponse> getAllActiveTiers() {
        return tierRepository.findByIsActiveTrue().stream()
                .map(this::mapToTierResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CardTierResponse> getAllTiersForManager() {
        return tierRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::mapToTierResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CardTierResponse updateTier(Long id, MembershipCardTierUpdateRequest request, UserAccount actor) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        // Basic request checks that require no database state (fail fast with HTTP 400 before lock acquisition)
        if (request.getPrice() == null) {
            throw new BusinessRuleException("Price is required");
        }
        if (request.getPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("Price cannot be negative");
        }
        if (request.getDurationMonths() == null) {
            throw new BusinessRuleException("Duration in months is required");
        }
        if (request.getDurationMonths() < 0) {
            throw new BusinessRuleException("Duration in months must be non-negative");
        }
        if (request.getDiscountPercentage() == null) {
            throw new BusinessRuleException("Discount percentage is required");
        }
        if (request.getDiscountPercentage() < 0 || request.getDiscountPercentage() > 100) {
            throw new BusinessRuleException("Discount percentage must be between 0 and 100");
        }
        if (request.getActive() == null) {
            throw new BusinessRuleException("Active flag is required");
        }

        // Concurrency decision (Option 2): deterministic row locking via findAllForUpdate() (ORDER BY id).
        // In SQL Server, the dialect supports NOWAIT (which fails immediately) but does not support a per-query bounded wait.
        // Sequential serialization (unbounded wait) was kept because a fail-fast response was not requested.
        // A second concurrent transaction waits until the first transaction ends.
        // Catching PessimisticLockingFailureException maps database or driver-level lock failures (such as deadlock victim selection)
        // and does not imply a timeout. Known limitation: unbounded wait holds a database connection while waiting.
        List<MembershipCardTier> lockedTiers;
        try {
            lockedTiers = tierRepository.findAllForUpdate();
        } catch (PessimisticLockingFailureException ex) {
            throw new BusinessRuleException("Membership tiers are currently being updated by another operation. Please retry in a few moments.", ex);
        }

        // The target tier is resolved from lockedTiers AFTER the lock is held.
        // This ensures the entity state in the persistence context is fresh and reflects any prior commits,
        // preventing stale state in the activity_log 'before' snapshot.
        // Evaluating 404 after lock acquisition is acceptable for this low-traffic manager-only catalog endpoint.
        MembershipCardTier tier = lockedTiers.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Card tier not found for id: " + id));

        boolean standard = isStandard(tier);

        // Database-dependent validations
        if (standard) {
            if (request.getPrice().compareTo(BigDecimal.ZERO) != 0) {
                throw new BusinessRuleException("The STANDARD tier price cannot be changed");
            }
            if (!request.getActive()) {
                throw new BusinessRuleException("The STANDARD tier cannot be deactivated");
            }
            if (request.getDiscountPercentage() != 0) {
                throw new BusinessRuleException("The STANDARD tier discount percentage must be 0");
            }
            if (request.getDurationMonths() != 0) {
                throw new BusinessRuleException("The STANDARD tier duration must be 0 months");
            }
        } else {
            if (request.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessRuleException("Price must be greater than zero for paid tiers");
            }
            if (request.getDurationMonths() < 1) {
                throw new BusinessRuleException("Duration in months must be at least 1 for paid tiers");
            }
        }

        if (!standard && Boolean.TRUE.equals(request.getActive())) {
            validateTierPriceMonotonicity(tier, request.getPrice());
        }

        Map<String, Object> before = snapshotTier(tier);

        if (!standard) {
            tier.setPrice(request.getPrice());
            tier.setIsActive(request.getActive());
        }
        tier.setDurationMonths(request.getDurationMonths());
        tier.setDiscountPercentage(request.getDiscountPercentage());
        // Full-replace semantics: a null or blank value clears the description.
        String cleanedDescription = (request.getDescription() == null || request.getDescription().isBlank())
                ? null
                : request.getDescription().trim();
        tier.setDescription(cleanedDescription);

        MembershipCardTier saved = tierRepository.saveAndFlush(tier);
        Map<String, Object> after = snapshotTier(saved);

        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.CARD_TIER_UPDATED)
                .entityType("MEMBERSHIP_CARD_TIER")
                .entityId(saved.getId())
                .entityCode(saved.getCode())
                .summary("Membership card tier '" + saved.getName() + "' updated")
                .details(Map.of("before", before, "after", after))
                .build());

        return mapToTierResponse(saved);
    }

    private Map<String, Object> snapshotTier(MembershipCardTier t) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", t.getName());
        map.put("price", t.getPrice());
        map.put("durationMonths", t.getDurationMonths());
        map.put("discountPercentage", t.getDiscountPercentage());
        map.put("description", t.getDescription());
        map.put("isActive", t.getIsActive());
        return map;
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

        // Standard: free and permanent, nothing to pay.
        // Invariant: STANDARD always has durationMonths 0 and price 0; every paid tier has durationMonths >= 1.
        if (isStandard(tier)) {
            return mapToCardResponse(memberCardRepository.save(newCard(member, tier, today, null, CardStatus.ACTIVE, BigDecimal.ZERO, false, null)));
        }

        // One card per member: a second request cannot be made while one is waiting for payment
        List<MemberCard> pending = memberCardRepository.findByMemberIdAndStatus(member.getId(), CardStatus.PENDING_PAYMENT);
        if (!pending.isEmpty()) {
            throw new BusinessRuleException("A membership card request is already waiting for payment: "
                    + pending.get(0).getCardCode() + ". Pay or cancel it first.");
        }

        CardPlan plan = planCard(member.getId(), tier, today);
        boolean isUpgrade = plan.replaces() != null;
        if (!isUpgrade && !paidAtDesk && plan.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than zero. Attempted card price: " + plan.price());
        }
        validateUpgradeFeeForActor(isUpgrade, plan.price(), paidAtDesk);
        CardStatus status = paidAtDesk ? CardStatus.ACTIVE : CardStatus.PENDING_PAYMENT;
        MemberCard card = newCard(member, tier, plan.startDate(), plan.endDate(), status, plan.price(), isUpgrade, plan.replaces());
        if (status == CardStatus.PENDING_PAYMENT) {
            Payment payment = paymentService.createPendingPayment(member, plan.price(), request.getPaymentMethod());
            card.setPayment(payment);
        }
        // BE-19: a desk sale records the money at once (SUCCESS payment, then the invoice). A zero fee upgrade has
        // nothing to pay and gets no payment.
        boolean takesMoneyAtDesk = status == CardStatus.ACTIVE && plan.price().signum() > 0;
        if (takesMoneyAtDesk) {
            card.setPayment(paymentVerificationService.getObject().recordDeskPayment(member, plan.price(),
                    request.getPaymentMethod(), request.getReferenceCode(), request.getAmountReceived(), actor));
        }
        try {
            card = memberCardRepository.save(card);
            memberCardRepository.flush();
            if (status == CardStatus.ACTIVE && plan.replaces() != null) {
                replace(plan.replaces());
            }
        } catch (DataIntegrityViolationException ex) {
            if (isUpgrade) {
                if (isIndexViolation(ex, "ux_member_card_replaces_in_force")) {
                    log.warn("Concurrent upgrade conflict for memberId={} replacing cardId={}: {}",
                            member.getId(), plan.replaces().getId(), ex.getMessage());
                    throw new BusinessRuleException("A concurrent upgrade is already in progress for this membership card.", ex);
                }
            }
            throw ex;
        } catch (PessimisticLockingFailureException ex) {
            if (isUpgrade) {
                log.warn("Lock acquisition failure during upgrade for memberId={} replacing cardId={}: {}",
                        member.getId(), plan.replaces().getId(), ex.getMessage());
                throw new BusinessRuleException("The upgrade could not be processed due to a concurrent conflict. Please retry.", ex);
            }
            throw ex;
        }
        MemberCardResponse response = mapToCardResponse(card);
        if (takesMoneyAtDesk) {
            withInvoice(response, paymentVerificationService.getObject()
                    .recordPaymentConfirmed(card.getPayment().getId(), actor, null));
        }
        return response;
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
        MemberCardResponse response = mapToCardResponse(card);
        if (card.getPayment() != null) {
            // Same receipt as PUT /payments/{id}/confirm: one PAID invoice and one PAYMENT_CONFIRMED row.
            withInvoice(response, paymentVerificationService.getObject()
                    .recordPaymentConfirmed(card.getPayment().getId(), actor, null));
        }
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MemberCardResponse activateCardForPaymentConfirmation(Long cardId, UserAccount actor, PaymentMethod actualMethod) {
        return mapToCardResponse(activatePending(cardId, actor, actualMethod));
    }

    private MemberCard activatePending(Long cardId, UserAccount actor, PaymentMethod actualMethod) {
        lockLinkedPayment(cardId);
        MemberCard card = memberCardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Member card not found for id: " + cardId));
        // Payment first, then lock and reload the item before checking its current status.
        entityManager.refresh(card, LockModeType.PESSIMISTIC_WRITE);
        if (card.getStatus() != CardStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Cannot activate membership card with status: " + card.getStatus()
                    + ". Only PENDING_PAYMENT cards can be activated.");
        }
        if (actualMethod != null && card.getPayment() == null) {
            throw new BusinessRuleException("Cannot specify payment method for a card with no linked payment");
        }

        // Honor a previously requested card after tier deactivation, retaining its requested price.
        // Revalidate the member baseline and payment through the existing activation path.
        LocalDate today = LocalDate.now(clock);
        CardPlan plan = planCard(card.getMember().getId(), card.getTier(), today);
        boolean requestedAsUpgrade = card.isUpgradeRequest();
        if (requestedAsUpgrade) {
            if (card.getReplacesCard() == null) {
                throw new BusinessRuleException("Cannot determine upgrade baseline for this membership card. Please contact the front desk.");
            }
            if (plan.replaces() == null || !plan.replaces().getId().equals(card.getReplacesCard().getId())) {
                throw new BusinessRuleException("The member's card has changed since this request. Cancel it and request the card again.");
            }
        } else if (plan.replaces() != null) {
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
        lockLinkedPayment(cardId);
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

    private boolean isIndexViolation(Throwable throwable, String indexName) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        pending.add(throwable);
        Pattern indexEvidence = Pattern.compile("(?i)(?:unique\\s+(?:key\\s+)?(?:index|constraint)|constraint)\\s+['\\\"\\[]"
                + Pattern.quote(indexName) + "['\\\"\\]]");
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            // Error codes alone cannot identify which unique constraint failed.
            boolean uniqueCode = !(current instanceof SQLException sql)
                    || sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627;
            if (uniqueCode && current.getMessage() != null
                    && indexEvidence.matcher(current.getMessage()).find()) {
                return true;
            }
            if (current.getCause() != null) {
                pending.add(current.getCause());
            }
            if (current instanceof SQLException sql && sql.getNextException() != null) {
                pending.add(sql.getNextException());
            }
        }
        return false;
    }

    /**
     * Calculates the baseline price paid for the active term.
     * If the current card was created via an upgrade, the member only paid the price difference for that card,
     * so we sum pricePaid across the replacement chain.
     * Legacy rows with unresolved replaces_card_id fail closed (throws BusinessRuleException), requiring front desk assistance.
     * Traversal is bounded to prevent infinite cycles.
     */
    private BigDecimal calculateTermBasePrice(MemberCard card) {
        if (card == null || card.getPricePaid() == null) {
            throw new BusinessRuleException("Cannot determine upgrade baseline for this membership card. Please contact the front desk.");
        }
        if (card.isUpgradeRequest() && card.getReplacesCard() == null) {
            // Fail closed: legacy upgrade rows where replaces_card_id could not be resolved
            // cannot safely determine baseline price. Automated upgrade is rejected and member is directed to the front desk.
            throw new BusinessRuleException("Cannot determine upgrade baseline for this membership card. Please contact the front desk.");
        }
        BigDecimal total = card.getPricePaid();
        MemberCard replaced = card.getReplacesCard();
        int depth = 0;
        Set<Long> visited = new HashSet<>();
        if (card.getId() != null) {
            visited.add(card.getId());
        }
        while (replaced != null) {
            if (++depth > MAX_CHAIN_DEPTH || (replaced.getId() != null && !visited.add(replaced.getId()))) {
                throw new BusinessRuleException("Membership card upgrade chain exceeds maximum allowed depth or contains cycle. Please contact the front desk.");
            }
            if (replaced.getPricePaid() == null
                    || replaced.getMember() == null || card.getMember() == null
                    || !replaced.getMember().getId().equals(card.getMember().getId())
                    || replaced.getStatus() != CardStatus.REPLACED
                    || !java.util.Objects.equals(replaced.getEndDate(), card.getEndDate())
                    || (replaced.isUpgradeRequest() && replaced.getReplacesCard() == null)) {
                throw new BusinessRuleException("Cannot determine upgrade baseline for this membership card. Please contact the front desk.");
            }
            total = total.add(replaced.getPricePaid());
            replaced = replaced.getReplacesCard();
        }
        return total;
    }

    /**
     * What buying {@code tier} means today for this member:
     * <ul>
     *   <li>no paid card: a new card from today;</li>
     *   <li>the same tier: a renewal from the day after the last card ends;</li>
     *   <li>a higher-ranked tier while a card is valid (rank defined by tier_rank):
     *       an upgrade from today that keeps the current card's end date.
     *       The fee is max(0, targetTier.price - calculateTermBasePrice(current)), where the base is what the member
     *       actually paid toward the active term. If fee is 0, the upgrade is immediately ACTIVE with 0 fee.
     *       The current card is replaced upon activation;</li>
     *   <li>an equal or lower ranked tier: it starts the day after the last card ends, at full price.</li>
     * </ul>
     * A card lasts {@code durationMonths} months: it ends the day before the same date {@code durationMonths} later.
     */
    private CardPlan planCard(Long memberId, MembershipCardTier tier, LocalDate today) {
        if (!isStandard(tier) && tier.getEffectiveRank() <= 0) {
            throw new BusinessRuleException("Tier ranking is not configured for this tier");
        }
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

        // Rank rule: an upgrade is strictly determined by tier ranking,
        // decoupled from live catalog price edits.
        boolean isUpgradeRank = (current != null
                && current.getTier() != null
                && tier.getEffectiveRank() > 0
                && tier.getEffectiveRank() > current.getTier().getEffectiveRank());

        if (isUpgradeRank) {
            if (last != current) {
                throw new BusinessRuleException("The current card is already renewed from " + last.getStartDate()
                        + ". The card can be upgraded once that term has started.");
            }
            BigDecimal currentBasePrice = calculateTermBasePrice(current);
            BigDecimal diff = tier.getPrice().subtract(currentBasePrice);
            BigDecimal fee = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
            return new CardPlan(today, current.getEndDate(), fee, current);
        }

        // Equal or lower rank keeps the existing path that queues the new card after the last card ends at full price.
        LocalDate from = last.getEndDate().plusDays(1);
        return new CardPlan(from, cardEnd(from, months), tier.getPrice(), null);
    }

    private static LocalDate cardEnd(LocalDate start, int months) {
        return start.plusMonths(months).minusDays(1);
    }

    /**
     * Read only the scalar payment id, then lock payment before accessing the card.
     * Activation additionally locks and refreshes its item because a caller may have cached it already.
     * With no linked payment there is no payment lock; activation still locks and refreshes the item.
     */
    private void lockLinkedPayment(Long cardId) {
        memberCardRepository.findPaymentIdById(cardId).ifPresent(paymentService::lockPayment);
    }

    /** Adds the receipt to the response so the desk screen can open it (F1-13 State After Payment). */
    private static MemberCardResponse withInvoice(MemberCardResponse response, Invoice invoice) {
        if (invoice != null) {
            response.setInvoiceId(invoice.getId());
            response.setInvoiceNumber(invoice.getInvoiceNumber());
        }
        return response;
    }

    private void replace(MemberCard old) {
        old.setStatus(CardStatus.REPLACED);
        memberCardRepository.save(old);
    }

    /**
     * Temporary fail-closed policy pending Product Owner decision:
     * Self-service members cannot execute zero-fee upgrades online.
     * When computed upgrade fee is <= 0, non-staff actors are directed to the front desk.
     * Staff-assisted purchases at the desk remain permitted to process zero-fee upgrades immediately.
     */
    private void validateUpgradeFeeForActor(boolean isUpgrade, BigDecimal fee, boolean paidAtDesk) {
        if (isUpgrade && !paidAtDesk && fee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Zero-fee upgrades cannot be completed online. Please contact the front desk.");
        }
    }

    /**
     * Enforces price monotonicity for active paid tiers:
     * When ordered by tier_rank, active paid tier prices must be strictly increasing.
     * Compares the proposed price against the nearest active lower-ranked and nearest active higher-ranked paid tier.
     * Decision on inactive tiers: Inactive tiers are excluded from active price monotonicity enforcement
     * because they are not on sale to members. When an inactive tier is edited or reactivated, its price is validated
     * if request.getActive() is true. STANDARD tier is excluded from this rule.
     */
    private void validateTierPriceMonotonicity(MembershipCardTier tierBeingUpdated, BigDecimal newPrice) {
        int targetRank = tierBeingUpdated.getEffectiveRank();
        if (targetRank <= 0) {
            return;
        }
        List<MembershipCardTier> activeTiers = tierRepository.findByIsActiveTrue().stream()
                .filter(t -> !isStandard(t))
                .filter(t -> !t.getId().equals(tierBeingUpdated.getId()))
                .filter(t -> t.getEffectiveRank() > 0)
                .collect(Collectors.toList());

        // Nearest lower-ranked active paid tier
        activeTiers.stream()
                .filter(t -> t.getEffectiveRank() < targetRank)
                .max(Comparator.comparingInt(MembershipCardTier::getEffectiveRank))
                .ifPresent(lowerTier -> {
                    if (newPrice.compareTo(lowerTier.getPrice()) <= 0) {
                        throw new BusinessRuleException("Tier price must be strictly greater than lower-ranked tier '"
                                + lowerTier.getName() + "' (" + lowerTier.getPrice() + ")");
                    }
                });

        // Nearest higher-ranked active paid tier
        activeTiers.stream()
                .filter(t -> t.getEffectiveRank() > targetRank)
                .min(Comparator.comparingInt(MembershipCardTier::getEffectiveRank))
                .ifPresent(higherTier -> {
                    if (newPrice.compareTo(higherTier.getPrice()) >= 0) {
                        throw new BusinessRuleException("Tier price must be strictly less than higher-ranked tier '"
                                + higherTier.getName() + "' (" + higherTier.getPrice() + ")");
                    }
                });
    }

    private MemberCard newCard(MemberProfile member, MembershipCardTier tier, LocalDate start, LocalDate end,
                               CardStatus status, BigDecimal price, boolean isUpgrade, MemberCard replacesCard) {
        long seq = memberCardRepository.getNextCardCodeSequence();
        return MemberCard.builder()
                .cardCode(codeFormatter.formatCardCode(seq))
                .member(member)
                .tier(tier)
                .startDate(start)
                .endDate(end)
                .status(status)
                .pricePaid(price)
                .upgradeRequest(isUpgrade)
                .replacesCard(replacesCard)
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

    /**
     * Decision: benefits follow the tier. Existing active cards use the CURRENT discountPercentage
     * of their tier (live tier benefits). We do not snapshot discount on member_card.
     * When package registrations occur, they snapshot originalPrice, discountPercentage, and paidAmount
     * at registration time, which must never be recomputed.
     */
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
                .tierRank(tier.getEffectiveRank())
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
                .tierRank(card.getTier().getEffectiveRank())
                .upgradeRequest(card.isUpgradeRequest())
                .replacesCardId(card.getReplacesCard() == null ? null : card.getReplacesCard().getId())
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
