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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The single place that says what a payment pays for and how its price is shown.
 * Used by GET /api/v1/payments/{id} (PaymentServiceImpl), GET /api/v1/payments and the invoice of a confirmed
 * payment (PaymentVerificationServiceImpl), so every screen shows the same item name, original price and discount.
 *
 * Pricing rule (unchanged from BE-17):
 * - package registration: original price = registration.original_price, paid = registration.paid_amount;
 * - membership card: original price = tier price, paid = card.price_paid (lower than the tier price for an upgrade);
 * - discount = original price - paid, never below zero; a missing value falls back to the payment amount.
 */
@Component
@RequiredArgsConstructor
public class PaymentItemResolver {

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
        BigDecimal original = card.getTier() != null && card.getTier().getPrice() != null
                ? card.getTier().getPrice()
                : paymentAmount;
        BigDecimal paid = card.getPricePaid() != null ? card.getPricePaid() : paymentAmount;
        boolean upgradeCredit = paid != null && original != null && paid.compareTo(original) < 0;
        String description = name + " (" + card.getCardCode() + ")"
                + (upgradeCredit ? " - upgrade, credit for the current card applied" : "");
        return new PaymentItem(InvoiceItemType.MEMBERSHIP_CARD, card.getId(), card.getCardCode(), name,
                card.getStatus() != null ? card.getStatus().name() : null,
                original, discountOf(original, paid), description);
    }

    private static BigDecimal discountOf(BigDecimal original, BigDecimal paid) {
        if (original == null || paid == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal diff = original.subtract(paid);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }
}
