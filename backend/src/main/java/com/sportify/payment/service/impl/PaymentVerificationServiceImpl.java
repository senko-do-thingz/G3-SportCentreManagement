package com.sportify.payment.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.FailPaymentRequest;
import com.sportify.payment.dto.InvoiceLineResponse;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.InvoiceLine;
import com.sportify.payment.entity.InvoiceStatus;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.exception.PaymentAmountMismatchException;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.repository.PaymentSpecifications;
import com.sportify.payment.service.PaymentVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentVerificationServiceImpl implements PaymentVerificationService {

    static final String CODE_PAYMENT_NOT_PENDING = "PAYMENT_NOT_PENDING";
    static final String CODE_INVOICE_ALREADY_ISSUED = "INVOICE_ALREADY_ISSUED";
    static final String ENTITY_TYPE = "PAYMENT";
    private static final int DESCRIPTION_MAX = 255;
    private static final int SUMMARY_MAX = 255;

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final MemberCardRepository memberCardRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final MembershipCardService membershipCardService;
    private final SportPackageService sportPackageService;
    private final AuditService auditService;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StaffPaymentResponse> listPayments(PaymentStatus status, PaymentMethod method, String search,
                                                           LocalDate from, LocalDate to, int page, int size) {
        if (page < 0) {
            throw new BusinessRuleException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The from date must not be after the to date");
        }
        LocalDateTime createdFrom = from != null ? from.atStartOfDay() : null;
        LocalDateTime createdBefore = to != null ? to.plusDays(1).atStartOfDay() : null;

        // The specification sets the order (PENDING first), so the page request carries no sort.
        Page<Payment> result = paymentRepository.findAll(
                PaymentSpecifications.withFilters(status, method, search, createdFrom, createdBefore),
                PageRequest.of(page, size));

        List<Payment> payments = result.getContent();
        Map<Long, LinkedItem> items = resolveItems(payments.stream().map(Payment::getId).toList());
        Map<Long, Invoice> invoices = firstInvoiceByPayment(payments.stream().map(Payment::getId).toList());

        List<StaffPaymentResponse> content = payments.stream()
                .map(p -> toResponse(p, items.get(p.getId()), invoices.get(p.getId())))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Runs in one transaction. A mismatch commits the note it appends (noRollbackFor) and changes nothing else;
     * every other error rolls the whole confirmation back. Two receptionists confirming the same payment at the
     * same time are told apart by the @Version column of payment: the later flush fails with 409.
     */
    @Override
    @Transactional(noRollbackFor = PaymentAmountMismatchException.class)
    public StaffPaymentResponse confirmPayment(Long paymentId, ConfirmPaymentRequest request, UserAccount actor) {
        requireStaff(actor);
        if (request == null || request.getMethod() == null) {
            throw new BusinessRuleException("Payment method is required");
        }
        if (request.getAmountReceived() == null || request.getAmountReceived().signum() <= 0) {
            throw new BusinessRuleException("Amount received must be greater than zero");
        }
        PaymentMethod method = request.getMethod();
        String reference = trimToNull(request.getReferenceCode());
        if (method != PaymentMethod.CASH && reference == null) {
            throw new BusinessRuleException("Reference code is required for payment method " + method);
        }
        String note = trimToNull(request.getNote());

        Payment payment = findPayment(paymentId);
        requirePending(payment, "confirmed");

        BigDecimal amountReceived = request.getAmountReceived();
        if (amountReceived.compareTo(payment.getAmount()) != 0) {
            String mismatchNote = "Amount mismatch: received " + amountReceived.toPlainString()
                    + ", expected " + payment.getAmount().toPlainString()
                    + " by " + method + (reference != null ? " reference " + reference : "")
                    + ", checked by user id " + actor.getId()
                    + (note != null ? ". Note: " + note : "");
            payment.setNotes(PaymentNotes.append(payment.getNotes(), mismatchNote));
            paymentRepository.saveAndFlush(payment);
            throw new PaymentAmountMismatchException("Amount received " + amountReceived.toPlainString()
                    + " does not match the payment amount " + payment.getAmount().toPlainString()
                    + ". The payment stays PENDING and needs review.");
        }

        LinkedItem item = resolveItem(payment.getId())
                .orElseThrow(() -> new BusinessRuleException("Payment " + payment.getPaymentCode()
                        + " is not linked to a membership card or a package registration"));
        if (!invoiceRepository.findByPaymentId(payment.getId()).isEmpty()) {
            throw new ConflictException("An invoice already exists for payment " + payment.getPaymentCode(),
                    CODE_INVOICE_ALREADY_ISSUED);
        }

        payment.setReferenceCode(reference);
        if (note != null) {
            payment.setNotes(PaymentNotes.append(payment.getNotes(), "Front desk note: " + note));
        }

        // Activation recomputes the card or package dates and marks this payment SUCCESS with payment_time now.
        if (item.type() == InvoiceItemType.MEMBERSHIP_CARD) {
            membershipCardService.activateCard(item.id(), actor, method);
        } else {
            sportPackageService.activateRegistration(item.id(), actor, method);
        }
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Payment " + payment.getPaymentCode() + " was not marked SUCCESS on activation");
        }

        Invoice invoice = issueInvoice(payment, item);
        // Flush here so a concurrent confirmation fails inside this call (optimistic lock, mapped to 409).
        paymentRepository.flush();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", method.name());
        details.put("referenceCode", reference);
        details.put("amount", payment.getAmount());
        details.put("amountReceived", amountReceived);
        details.put("itemType", item.type().name());
        details.put("itemReferenceId", item.id());
        details.put("itemCode", item.code());
        details.put("invoiceNumber", invoice.getInvoiceNumber());
        details.put("note", note);
        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.PAYMENT_CONFIRMED)
                .entityType(ENTITY_TYPE)
                .entityId(payment.getId())
                .entityCode(payment.getPaymentCode())
                .summary(truncate("Payment " + payment.getPaymentCode() + " confirmed: "
                        + payment.getAmount().toPlainString() + " by " + method
                        + ", " + item.code() + " activated, invoice " + invoice.getInvoiceNumber(), SUMMARY_MAX))
                .details(details)
                .build());

        LinkedItem refreshed = resolveItem(payment.getId()).orElse(item);
        return toResponse(payment, refreshed, invoice);
    }

    @Override
    @Transactional
    public StaffPaymentResponse failPayment(Long paymentId, FailPaymentRequest request, UserAccount actor) {
        requireStaff(actor);
        String reason = request != null ? trimToNull(request.getReason()) : null;
        if (reason == null) {
            throw new BusinessRuleException("Reason is required");
        }

        Payment payment = findPayment(paymentId);
        requirePending(payment, "marked as failed");

        Optional<LinkedItem> item = resolveItem(payment.getId());
        String failNote = "Payment failed, checked by user id " + actor.getId() + ": " + reason;
        if (item.isPresent()) {
            // Cancelling the request marks this payment FAILED; the member has to request the item again.
            if (item.get().type() == InvoiceItemType.MEMBERSHIP_CARD) {
                membershipCardService.cancelCard(item.get().id(), actor);
            } else {
                sportPackageService.cancelRegistration(item.get().id(), actor);
            }
            payment.setNotes(PaymentNotes.append(payment.getNotes(), failNote));
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setNotes(PaymentNotes.append(payment.getNotes(), failNote));
        }
        if (payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Payment " + payment.getPaymentCode() + " was not marked FAILED");
        }
        paymentRepository.saveAndFlush(payment);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("itemType", item.map(i -> i.type().name()).orElse(null));
        details.put("itemReferenceId", item.map(LinkedItem::id).orElse(null));
        details.put("itemCode", item.map(LinkedItem::code).orElse(null));
        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.PAYMENT_FAILED)
                .entityType(ENTITY_TYPE)
                .entityId(payment.getId())
                .entityCode(payment.getPaymentCode())
                .summary(truncate("Payment " + payment.getPaymentCode() + " marked FAILED: " + reason, SUMMARY_MAX))
                .details(details)
                .build());

        LinkedItem refreshed = resolveItem(payment.getId()).orElse(null);
        return toResponse(payment, refreshed, null);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByPayment(Long paymentId, UserAccount actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Actor cannot be null");
        }
        Payment payment = findPayment(paymentId);
        // MemberProfile shares its primary key with UserAccount (@MapsId user_id), so member id equals user id by design.
        boolean isOwner = payment.getMember() != null && payment.getMember().getId().equals(actor.getId());
        if (!isOwner && !SecurityUtils.isStaff(actor)) {
            throw new ResourceNotFoundException("Payment not found for id: " + paymentId);
        }
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId).stream()
                .min(Comparator.comparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new ResourceNotFoundException("No invoice has been issued for payment id: " + paymentId));
        return toInvoiceResponse(invoice, payment);
    }

    private Invoice issueInvoice(Payment payment, LinkedItem item) {
        BigDecimal total = payment.getAmount();
        BigDecimal unitPrice = item.originalPrice() != null && item.originalPrice().compareTo(total) > 0
                ? item.originalPrice()
                : total;
        BigDecimal discount = unitPrice.subtract(total);

        LocalDateTime now = LocalDateTime.now(clock);
        Invoice invoice = Invoice.builder()
                .invoiceNumber(codeFormatter.formatInvoiceNumber(invoiceRepository.getNextInvoiceNumberSequence()))
                .payment(payment)
                .member(payment.getMember())
                .subtotalAmount(unitPrice)
                .discountAmount(discount)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(total)
                .status(InvoiceStatus.PAID)
                .issueDate(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        invoice.addLine(InvoiceLine.builder()
                .itemType(item.type())
                .itemReferenceId(item.id())
                .description(truncate(item.invoiceDescription(), DESCRIPTION_MAX))
                .quantity(1)
                .unitPrice(unitPrice)
                .lineTotal(unitPrice)
                .createdAt(now)
                .build());
        return invoiceRepository.save(invoice);
    }

    private Optional<LinkedItem> resolveItem(Long paymentId) {
        return Optional.ofNullable(resolveItems(List.of(paymentId)).get(paymentId));
    }

    /** What each payment pays for: a membership card or a sport package registration. */
    private Map<Long, LinkedItem> resolveItems(Collection<Long> paymentIds) {
        Map<Long, LinkedItem> items = new HashMap<>();
        if (paymentIds.isEmpty()) {
            return items;
        }
        for (SportPackageRegistration reg : registrationRepository.findByPaymentIdIn(paymentIds)) {
            items.put(reg.getPayment().getId(), fromRegistration(reg));
        }
        for (MemberCard card : memberCardRepository.findByPaymentIdIn(paymentIds)) {
            items.putIfAbsent(card.getPayment().getId(), fromCard(card));
        }
        return items;
    }

    private Map<Long, Invoice> firstInvoiceByPayment(Collection<Long> paymentIds) {
        if (paymentIds.isEmpty()) {
            return Map.of();
        }
        return invoiceRepository.findByPaymentIdIn(paymentIds).stream()
                .collect(Collectors.toMap(i -> i.getPayment().getId(), Function.identity(),
                        (a, b) -> a.getId() <= b.getId() ? a : b));
    }

    private LinkedItem fromRegistration(SportPackageRegistration reg) {
        String name = reg.getSportPackage() != null ? reg.getSportPackage().getName() : "Sport package";
        return new LinkedItem(InvoiceItemType.SPORT_PACKAGE, reg.getId(), reg.getRegistrationCode(),
                name, reg.getStatus() != null ? reg.getStatus().name() : null,
                reg.getOriginalPrice(),
                name + " (" + reg.getRegistrationCode() + ")");
    }

    private LinkedItem fromCard(MemberCard card) {
        String tierName = card.getTier() != null ? card.getTier().getName() : null;
        String name = tierName != null ? tierName + " Card" : "Membership Card";
        BigDecimal tierPrice = card.getTier() != null ? card.getTier().getPrice() : null;
        boolean upgradeCredit = tierPrice != null && card.getPricePaid() != null
                && card.getPricePaid().compareTo(tierPrice) < 0;
        String description = name + " (" + card.getCardCode() + ")"
                + (upgradeCredit ? " - upgrade, credit for the current card applied" : "");
        return new LinkedItem(InvoiceItemType.MEMBERSHIP_CARD, card.getId(), card.getCardCode(),
                name, card.getStatus() != null ? card.getStatus().name() : null,
                tierPrice, description);
    }

    private StaffPaymentResponse toResponse(Payment payment, LinkedItem item, Invoice invoice) {
        MemberProfile member = payment.getMember();
        UserAccount account = member != null ? member.getUserAccount() : null;
        UserAccount confirmedBy = payment.getConfirmedByUser();
        BigDecimal original = item != null && item.originalPrice() != null ? item.originalPrice() : payment.getAmount();
        BigDecimal discount = original.compareTo(payment.getAmount()) > 0
                ? original.subtract(payment.getAmount())
                : BigDecimal.ZERO;
        return StaffPaymentResponse.builder()
                .id(payment.getId())
                .code(payment.getPaymentCode())
                .status(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .method(payment.getPaymentMethod())
                .referenceCode(payment.getReferenceCode())
                .notes(payment.getNotes())
                .paymentTime(payment.getPaymentTime())
                .memberId(member != null ? member.getId() : null)
                .memberCode(member != null ? member.getMemberCode() : null)
                .memberFullName(account != null ? account.getFullName() : null)
                .itemType(item != null ? item.type() : null)
                .itemReferenceId(item != null ? item.id() : null)
                .itemCode(item != null ? item.code() : null)
                .itemDescription(item != null ? item.name() : "Payment " + payment.getPaymentCode())
                .itemStatus(item != null ? item.status() : null)
                .originalPrice(original)
                .discount(discount)
                .confirmedByUserId(confirmedBy != null ? confirmedBy.getId() : null)
                .confirmedByName(confirmedBy != null ? confirmedBy.getFullName() : null)
                .invoiceId(invoice != null ? invoice.getId() : null)
                .invoiceNumber(invoice != null ? invoice.getInvoiceNumber() : null)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice, Payment payment) {
        MemberProfile member = invoice.getMember();
        UserAccount account = member != null ? member.getUserAccount() : null;
        List<InvoiceLineResponse> lines = invoice.getLines().stream()
                .sorted(Comparator.comparing(InvoiceLine::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(line -> InvoiceLineResponse.builder()
                        .id(line.getId())
                        .itemType(line.getItemType())
                        .itemReferenceId(line.getItemReferenceId())
                        .description(line.getDescription())
                        .quantity(line.getQuantity())
                        .unitPrice(line.getUnitPrice())
                        .lineTotal(line.getLineTotal())
                        .build())
                .toList();
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .status(invoice.getStatus())
                .issueDate(invoice.getIssueDate())
                .paymentId(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .paymentMethod(payment.getPaymentMethod())
                .paymentReference(payment.getReferenceCode())
                .paymentTime(payment.getPaymentTime())
                .memberId(member != null ? member.getId() : null)
                .memberCode(member != null ? member.getMemberCode() : null)
                .memberFullName(account != null ? account.getFullName() : null)
                .subtotalAmount(invoice.getSubtotalAmount())
                .discountAmount(invoice.getDiscountAmount())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .lines(lines)
                .build();
    }

    private Payment findPayment(Long paymentId) {
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
    }

    private void requirePending(Payment payment, String action) {
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Payment " + payment.getPaymentCode() + " cannot be " + action
                    + ": it is " + payment.getPaymentStatus() + ", not PENDING", CODE_PAYMENT_NOT_PENDING);
        }
    }

    private void requireStaff(UserAccount actor) {
        if (!SecurityUtils.isStaff(actor)) {
            throw new BusinessRuleException("Only staff users can verify payments");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    /**
     * The card or package registration a payment pays for.
     *
     * @param name               short name shown in the payment list
     * @param originalPrice      price before the card discount or the upgrade credit
     * @param invoiceDescription description written on the invoice line
     */
    private record LinkedItem(InvoiceItemType type, Long id, String code, String name, String status,
                              BigDecimal originalPrice, String invoiceDescription) {
    }
}
