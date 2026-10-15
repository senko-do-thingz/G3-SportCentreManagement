package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single place that says what a payment pays for and how its price is shown.
 * Used by GET /api/v1/payments/{id} (PaymentServiceImpl), GET /api/v1/payments and the invoice of a confirmed
 * payment (PaymentVerificationServiceImpl), so every screen shows the same item name, original price and discount.
 *
 * Pricing rule: only values stored when the payment request was made are used, never the current catalog price,
 * so a tier or package price edited after the request cannot change what the invoice says.
 * - package registration: original price = registration.original_price, paid = registration.paid_amount (both are
 *   snapshots taken by BE-17 at request time);
 * - membership card: paid = card.price_paid (the snapshot taken at request time). A new card or renewal is priced
 *   at the full tier price, so original price = price_paid and discount = 0. An upgrade is priced at
 *   tier price - credit, where the credit is what the member paid toward the current term: the price_paid of the
 *   replaced card and of every card it replaced in turn (all snapshots). So original price = price_paid + credit
 *   and discount = credit. When that chain cannot be read (a legacy upgrade row without replaces_card_id, a missing
 *   price or a chain that is too long), no credit is claimed: original price = price_paid, discount = 0;
 * - discount = original price - paid, never below zero; a missing value falls back to the payment amount.
 */
@Component
@RequiredArgsConstructor
public class PaymentItemResolver {

    /**
     * Same bound as the BE-17 upgrade pricing (MembershipCardServiceImpl.MAX_CHAIN_DEPTH): the replaced card plus at
     * most this many cards it replaced in turn.
     */
    static final int MAX_CHAIN_DEPTH = 20;

    private final MemberCardRepository memberCardRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final InvoiceRepository invoiceRepository;

    /**
     * The card or package registration a payment pays for.
     *
     * @param name               short name shown on the payment screens
     * @param originalPrice      price before the card discount or the upgrade credit
     * @param discount           card discount or upgrade credit, zero or more
     * @param invoiceDescription description written on the invoice line
     */
    public record PaymentItem(InvoiceItemType type, Long id, String code, String name, String status,
                              BigDecimal originalPrice, BigDecimal discount, String invoiceDescription) {
    }

    /** What the payment screens show for one payment. */
    public record PaymentItemSummary(String description, BigDecimal originalPrice, BigDecimal discount) {
    }

    /** The item of one payment: a package registration first, then a membership card. */
    public Optional<PaymentItem> findItem(Payment payment) {
        Optional<PaymentItem> registration = registrationRepository.findByPaymentId(payment.getId())
                .map(reg -> fromRegistration(reg, payment.getAmount()));
        if (registration.isPresent()) {
            return registration;
        }
        return memberCardRepository.findByPaymentId(payment.getId())
                .map(card -> fromCard(card, payment.getAmount()));
    }

    /** The items of a page of payments, keyed by payment id, with two batch queries. */
    public Map<Long, PaymentItem> findItems(Collection<Payment> payments) {
        Map<Long, PaymentItem> items = new HashMap<>();
        if (payments.isEmpty()) {
            return items;
        }
        Map<Long, BigDecimal> amounts = new HashMap<>();
        payments.forEach(p -> amounts.put(p.getId(), p.getAmount()));
        for (SportPackageRegistration reg : registrationRepository.findByPaymentIdIn(amounts.keySet())) {
            Long paymentId = reg.getPayment().getId();
            items.put(paymentId, fromRegistration(reg, amounts.get(paymentId)));
        }
        for (MemberCard card : memberCardRepository.findByPaymentIdIn(amounts.keySet())) {
            Long paymentId = card.getPayment().getId();
            items.putIfAbsent(paymentId, fromCard(card, amounts.get(paymentId)));
        }
        return items;
    }

    /**
     * Summary of one payment, for GET /api/v1/payments/{id}: the item if there is one, otherwise the first invoice,
     * otherwise the payment itself.
     */
    public PaymentItemSummary summarize(Payment payment) {
        Optional<PaymentItem> item = findItem(payment);
        if (item.isPresent()) {
            return summarize(payment, item.get(), null);
        }
        Invoice invoice = invoiceRepository.findByPaymentId(payment.getId()).stream()
                .min(Comparator.comparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        return summarize(payment, null, invoice);
    }

    /** Same rule as {@link #summarize(Payment)} when the item and the invoice are already loaded (list rows). */
    public PaymentItemSummary summarize(Payment payment, PaymentItem item, Invoice invoice) {
        if (item != null) {
            return new PaymentItemSummary(item.name(), item.originalPrice(), item.discount());
        }
        if (invoice != null) {
            BigDecimal original = invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : payment.getAmount();
            BigDecimal discount = invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO;
            return new PaymentItemSummary("Invoice " + invoice.getInvoiceNumber(), original, discount);
        }
        return new PaymentItemSummary("Payment " + payment.getPaymentCode(), payment.getAmount(), BigDecimal.ZERO);
    }

    /** First invoice of each payment, keyed by payment id. */
    public Map<Long, Invoice> findFirstInvoices(Collection<Long> paymentIds) {
        Map<Long, Invoice> invoices = new HashMap<>();
        if (paymentIds.isEmpty()) {
            return invoices;
        }
        List<Invoice> rows = invoiceRepository.findByPaymentIdIn(paymentIds);
        for (Invoice invoice : rows) {
            invoices.merge(invoice.getPayment().getId(), invoice, (a, b) -> a.getId() <= b.getId() ? a : b);
        }
        return invoices;
    }

    private PaymentItem fromRegistration(SportPackageRegistration reg, BigDecimal paymentAmount) {
        String name = reg.getSportPackage() != null ? reg.getSportPackage().getName() : "Sport Package Registration";
        BigDecimal original = reg.getOriginalPrice() != null ? reg.getOriginalPrice() : paymentAmount;
        BigDecimal paid = reg.getPaidAmount() != null ? reg.getPaidAmount() : paymentAmount;
        return new PaymentItem(InvoiceItemType.SPORT_PACKAGE, reg.getId(), reg.getRegistrationCode(), name,
                reg.getStatus() != null ? reg.getStatus().name() : null,
                original, discountOf(original, paid),
                name + " (" + reg.getRegistrationCode() + ")");
    }

    private PaymentItem fromCard(MemberCard card, BigDecimal paymentAmount) {
        String name = card.getTier() != null ? card.getTier().getName() + " Card" : "Membership Card";
        BigDecimal paid = card.getPricePaid() != null ? card.getPricePaid() : paymentAmount;
        BigDecimal credit = card.isUpgradeRequest() ? upgradeCredit(card) : BigDecimal.ZERO;
        BigDecimal original = paid != null ? paid.add(credit) : null;
        String description = name + " (" + card.getCardCode() + ")";
        if (card.isUpgradeRequest()) {
            description += credit.signum() > 0 ? " - upgrade, credit for the current card applied" : " - upgrade";
        }
        return new PaymentItem(InvoiceItemType.MEMBERSHIP_CARD, card.getId(), card.getCardCode(), name,
                card.getStatus() != null ? card.getStatus().name() : null,
                original, discountOf(original, paid), description);
    }

    /**
     * The credit an upgrade was priced with: the sum of price_paid over the replaced card and the cards it replaced,
     * the same base BE-17 subtracts from the tier price when it prices the upgrade. Zero when the chain cannot be
     * read reliably, so no discount is ever claimed that was not stored.
     */
    static BigDecimal upgradeCredit(MemberCard card) {
        BigDecimal credit = BigDecimal.ZERO;
        Set<Long> visited = new HashSet<>();
        if (card.getId() != null) {
            visited.add(card.getId());
        }
        MemberCard replaced = card.getReplacesCard();
        int depth = 0;
        while (replaced != null) {
            boolean cycle = replaced.getId() != null && !visited.add(replaced.getId());
            if (++depth > MAX_CHAIN_DEPTH + 1 || cycle || replaced.getPricePaid() == null
                    || (replaced.isUpgradeRequest() && replaced.getReplacesCard() == null)) {
                return BigDecimal.ZERO;
            }
            credit = credit.add(replaced.getPricePaid());
            replaced = replaced.getReplacesCard();
        }
        return credit;
    }

    private static BigDecimal discountOf(BigDecimal original, BigDecimal paid) {
        if (original == null || paid == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = original.subtract(paid);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }
}
