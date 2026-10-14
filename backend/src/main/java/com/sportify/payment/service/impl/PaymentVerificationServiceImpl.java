package com.sportify.payment.service.impl;

import com.sportify.catalog.dto.PageResponse;
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
import com.sportify.payment.service.impl.PaymentItemResolver.PaymentItem;
import com.sportify.payment.service.impl.PaymentItemResolver.PaymentItemSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentVerificationServiceImpl implements PaymentVerificationService {

    static final String CODE_PAYMENT_NOT_PENDING = "PAYMENT_NOT_PENDING";
    static final String CODE_INVOICE_ALREADY_ISSUED = "INVOICE_ALREADY_ISSUED";
    static final String CODE_PAYMENT_CONCURRENT_UPDATE = "PAYMENT_CONCURRENT_UPDATE";
    static final String CODE_PAYMENT_ITEM_AMOUNT_MISMATCH = "PAYMENT_ITEM_AMOUNT_MISMATCH";
    static final String ENTITY_TYPE = "PAYMENT";
    private static final int DESCRIPTION_MAX = 255;
    private static final int SUMMARY_MAX = 255;

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentItemResolver itemResolver;
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
        Map<Long, PaymentItem> items = itemResolver.findItems(payments);
        Map<Long, Invoice> invoices = itemResolver.findFirstInvoices(payments.stream().map(Payment::getId).toList());

        List<StaffPaymentResponse> content = payments.stream()
                .map(p -> toResponse(p, items.get(p.getId()), invoices.get(p.getId())))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Runs in one transaction that starts by locking the payment row, so a second confirm or fail on the same
     * payment waits and then gets 409 PAYMENT_NOT_PENDING. A mismatch commits its note and its
     * PAYMENT_AMOUNT_MISMATCH log (noRollbackFor) and changes nothing else; every other error rolls back.
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

        Payment payment = lockPayment(paymentId);
        requirePending(payment, "confirmed");

        BigDecimal amountReceived = request.getAmountReceived();
        if (amountReceived.compareTo(payment.getAmount()) != 0) {
            recordMismatch(payment, method, reference, amountReceived, note, actor);
            throw new PaymentAmountMismatchException("Amount received " + amountReceived.toPlainString()
                    + " does not match the payment amount " + payment.getAmount().toPlainString()
                    + ". The payment stays PENDING and needs review.");
        }

        PaymentItem item = itemResolver.findItem(payment)
                .orElseThrow(() -> new BusinessRuleException("Payment " + payment.getPaymentCode()
                        + " is not linked to a membership card or a package registration"));

        payment.setReferenceCode(reference);
        if (note != null) {
            payment.setNotes(PaymentNotes.append(payment.getNotes(), "Front desk note: " + note));
        }

        // Activation recomputes the card or package dates and marks this payment SUCCESS with payment_time now.
        if (item.type() == InvoiceItemType.MEMBERSHIP_CARD) {
            membershipCardService.activateCardForPaymentConfirmation(item.id(), actor, method);
        } else {
            sportPackageService.activateRegistrationForPaymentConfirmation(item.id(), actor, method);
        }
        // Self-invocation: no proxy is involved, so this runs in the current transaction as plain code.
        Invoice invoice = recordPaymentConfirmed(payment.getId(), actor, note);
        flushPayment();

        PaymentItem refreshed = itemResolver.findItem(payment).orElse(item);
        return toResponse(payment, refreshed, invoice);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice recordPaymentConfirmed(Long paymentId, UserAccount actor, String note) {
        Payment payment = findPayment(paymentId);
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Payment " + payment.getPaymentCode()
                    + " must be SUCCESS before its invoice is issued, but is " + payment.getPaymentStatus());
        }
        PaymentItem item = itemResolver.findItem(payment)
                .orElseThrow(() -> new BusinessRuleException("Payment " + payment.getPaymentCode()
                        + " is not linked to a membership card or a package registration"));
        if (!invoiceRepository.findByPaymentId(payment.getId()).isEmpty()) {
            throw new ConflictException("An invoice already exists for payment " + payment.getPaymentCode(),
                    CODE_INVOICE_ALREADY_ISSUED);
        }

        Invoice invoice = issueInvoice(payment, item);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("method", payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null);
        details.put("referenceCode", payment.getReferenceCode());
        details.put("amount", payment.getAmount());
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
                        + payment.getAmount().toPlainString() + " by " + payment.getPaymentMethod()
                        + ", " + item.code() + " activated, invoice " + invoice.getInvoiceNumber(), SUMMARY_MAX))
                .details(details)
                .build());
        return invoice;
    }

    @Override
    @Transactional
    public StaffPaymentResponse failPayment(Long paymentId, FailPaymentRequest request, UserAccount actor) {
        requireStaff(actor);
        String reason = request != null ? trimToNull(request.getReason()) : null;
        if (reason == null) {
            throw new BusinessRuleException("Reason is required");
        }

        Payment payment = lockPayment(paymentId);
        requirePending(payment, "marked as failed");

        Optional<PaymentItem> item = itemResolver.findItem(payment);
        String failNote = "Payment failed, checked by user id " + actor.getId() + ": " + reason;
        if (item.isPresent()) {
            // Cancelling the request marks this payment FAILED; the member has to request the item again.
            if (item.get().type() == InvoiceItemType.MEMBERSHIP_CARD) {
                membershipCardService.cancelCard(item.get().id(), actor);
            } else {
                sportPackageService.cancelRegistration(item.get().id(), actor, reason);
            }
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
        }
        payment.setNotes(PaymentNotes.append(payment.getNotes(), failNote));
        if (payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new IllegalStateException("Payment " + payment.getPaymentCode() + " was not marked FAILED");
        }
        flushPayment();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", reason);
        details.put("itemType", item.map(i -> i.type().name()).orElse(null));
        details.put("itemReferenceId", item.map(PaymentItem::id).orElse(null));
        details.put("itemCode", item.map(PaymentItem::code).orElse(null));
        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.PAYMENT_FAILED)
                .entityType(ENTITY_TYPE)
                .entityId(payment.getId())
                .entityCode(payment.getPaymentCode())
                .summary(truncate("Payment " + payment.getPaymentCode() + " marked FAILED: " + reason, SUMMARY_MAX))
                .details(details)
                .build());

        PaymentItem refreshed = itemResolver.findItem(payment).orElse(null);
        return toResponse(payment, refreshed, null);
    }

    /**
     * S3-Mismatch: keeps the payment PENDING and records what the front desk saw, as a note on the payment and a
     * PAYMENT_AMOUNT_MISMATCH activity log row, both committed with the 409 answer.
     */
    private void recordMismatch(Payment payment, PaymentMethod method, String reference, BigDecimal amountReceived,
                                String note, UserAccount actor) {
        String mismatchNote = "Amount mismatch: received " + amountReceived.toPlainString()
                + ", expected " + payment.getAmount().toPlainString()
                + " by " + method + (reference != null ? " reference " + reference : "")
                + ", checked by user id " + actor.getId()
                + (note != null ? ". Note: " + note : "");
        payment.setNotes(PaymentNotes.append(payment.getNotes(), mismatchNote));
        flushPayment();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("amount", payment.getAmount());
        details.put("amountReceived", amountReceived);
        details.put("method", method.name());
        details.put("referenceCode", reference);
        details.put("note", note);
        auditService.record(AuditEvent.builder()
                .actor(actor)
                .action(AuditAction.PAYMENT_AMOUNT_MISMATCH)
                .entityType(ENTITY_TYPE)
                .entityId(payment.getId())
                .entityCode(payment.getPaymentCode())
                .summary(truncate("Payment " + payment.getPaymentCode() + " needs review: received "
                        + amountReceived.toPlainString() + ", expected " + payment.getAmount().toPlainString(), SUMMARY_MAX))
                .details(details)
                .build());
    }

    /**
     * Amounts come from PaymentItemResolver (the same rule as the payment screens): subtotal = original price,
     * discount = resolver discount, total = payment amount. If subtotal - discount does not equal the payment amount,
     * the price stored on the item (paid_amount or price_paid) differs from the payment amount: corrupted data. The
     * call answers 409 PAYMENT_ITEM_AMOUNT_MISMATCH and the transaction rolls back, so nothing is written.
     */
    private Invoice issueInvoice(Payment payment, PaymentItem item) {
        BigDecimal total = payment.getAmount();
        BigDecimal unitPrice = item.originalPrice() != null ? item.originalPrice() : total;
        BigDecimal discount = item.discount() != null ? item.discount() : BigDecimal.ZERO;
        if (unitPrice.subtract(discount).compareTo(total) != 0) {
            throw new ConflictException("Cannot issue the invoice of payment " + payment.getPaymentCode()
                    + ": original price " + unitPrice.toPlainString() + " - discount " + discount.toPlainString()
                    + " does not equal the payment amount " + total.toPlainString()
                    + ". The price stored on " + item.code() + " and the payment disagree; check the data.",
                    CODE_PAYMENT_ITEM_AMOUNT_MISMATCH);
        }

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

    /**
     * Writes the pending changes now, so a failed version check surfaces inside the call. Every path that changes a
     * payment and its card or registration takes the payment row lock first, so this is only a backstop for a write
     * that skips the lock (for example a future code path or a manual database change).
     */
    private void flushPayment() {
        try {
            paymentRepository.flush();
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("The payment was changed by another request. Reload it and try again.",
                    CODE_PAYMENT_CONCURRENT_UPDATE);
        }
    }

    private StaffPaymentResponse toResponse(Payment payment, PaymentItem item, Invoice invoice) {
        MemberProfile member = payment.getMember();
        UserAccount account = member != null ? member.getUserAccount() : null;
        UserAccount confirmedBy = payment.getConfirmedByUser();
        PaymentItemSummary summary = itemResolver.summarize(payment, item, invoice);
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
                .itemDescription(summary.description())
                .itemStatus(item != null ? item.status() : null)
                .originalPrice(summary.originalPrice())
                .discount(summary.discount())
                .confirmedByUserId(confirmedBy != null ? confirmedBy.getId() : null)
                .confirmedByName(confirmedBy != null ? confirmedBy.getFullName() : null)
                .invoiceId(invoice != null ? invoice.getId() : null)
                .invoiceNumber(invoice != null ? invoice.getInvoiceNumber() : null)
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private Payment lockPayment(Long paymentId) {
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
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

    /** Mapped to 403 by GlobalExceptionHandler, like the @PreAuthorize check on the controller. */
    private void requireStaff(UserAccount actor) {
        if (!SecurityUtils.isStaff(actor)) {
            throw new AccessDeniedException("Only staff users can verify payments");
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
}
