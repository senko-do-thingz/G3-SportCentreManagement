package com.sportify.payment.service.impl;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.dto.MemberPaymentResponse;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.InvoiceLine;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.ReceiptService;
import com.sportify.payment.service.impl.PaymentItemResolver.PaymentItem;
import com.sportify.payment.service.impl.PaymentItemResolver.PaymentItemSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    /** Newest first; id breaks ties between payments created in the same instant, so pages never overlap. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentItemResolver itemResolver;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MemberPaymentResponse> listMyPayments(PaymentStatus status, int page, int size,
                                                               UserAccount actor) {
        requireActor(actor);
        if (!SecurityUtils.isMember(actor)) {
            throw new AccessDeniedException("Only members have a payment history");
        }
        if (page < 0) {
            throw new BusinessRuleException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessRuleException("Size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // MemberProfile shares its primary key with UserAccount (@MapsId user_id), so member id equals user id.
        Long memberId = actor.getId();
        PageRequest pageRequest = PageRequest.of(page, size, NEWEST_FIRST);
        Page<Payment> result = status != null
                ? paymentRepository.findByMemberIdAndPaymentStatus(memberId, status, pageRequest)
                : paymentRepository.findByMemberId(memberId, pageRequest);

        List<Payment> payments = result.getContent();
        Map<Long, PaymentItem> items = itemResolver.findItems(payments);
        Map<Long, Invoice> invoices = itemResolver.findFirstInvoices(payments.stream().map(Payment::getId).toList());

        List<MemberPaymentResponse> content = payments.stream()
                .map(p -> toMemberResponse(p, items.get(p.getId()), invoices.get(p.getId())))
                .toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long invoiceId, UserAccount actor) {
        requireActor(actor);
        if (invoiceId == null) {
            throw new BusinessRuleException("Invoice ID cannot be null");
        }
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found for id: " + invoiceId));
        requireOwnerOrStaff(invoice.getMember(), actor, "invoice");
        return toCheckedResponse(invoice);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByPayment(Long paymentId, UserAccount actor) {
        requireActor(actor);
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
        // Ownership is checked before the invoice lookup, so another member cannot learn whether a receipt exists.
        requireOwnerOrStaff(payment.getMember(), actor, "payment");
        Invoice invoice = invoiceRepository.findByPaymentId(paymentId).stream()
                .min(Comparator.comparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new ResourceNotFoundException("No invoice has been issued for payment id: " + paymentId));
        return toCheckedResponse(invoice);
    }

    /**
     * Totals rule of the receipt screens: total = sum of line totals - discount + tax. The invoice is checked when it
     * is issued (PaymentVerificationServiceImpl.issueInvoice), so a difference here means the rows were changed by
     * hand. The receipt is still returned as stored (it is the legal record), and the difference is logged.
     */
    private InvoiceResponse toCheckedResponse(Invoice invoice) {
        BigDecimal linesTotal = invoice.getLines().stream()
                .map(InvoiceLine::getLineTotal)
                .map(ReceiptServiceImpl::zeroIfNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expected = linesTotal
                .subtract(zeroIfNull(invoice.getDiscountAmount()))
                .add(zeroIfNull(invoice.getTaxAmount()));
        if (invoice.getTotalAmount() == null || expected.compareTo(invoice.getTotalAmount()) != 0
                || (invoice.getSubtotalAmount() != null && linesTotal.compareTo(invoice.getSubtotalAmount()) != 0)) {
            log.warn("Invoice {} totals do not add up: lines {} subtotal {} discount {} tax {} total {}",
                    invoice.getInvoiceNumber(), linesTotal, invoice.getSubtotalAmount(), invoice.getDiscountAmount(),
                    invoice.getTaxAmount(), invoice.getTotalAmount());
        }
        return InvoiceResponseMapper.toResponse(invoice);
    }

    private MemberPaymentResponse toMemberResponse(Payment payment, PaymentItem item, Invoice invoice) {
        MemberProfile member = payment.getMember();
        PaymentItemSummary summary = itemResolver.summarize(payment, item, invoice);
        return MemberPaymentResponse.builder()
                .id(payment.getId())
                .code(payment.getPaymentCode())
                .status(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .method(payment.getPaymentMethod())
                .itemType(item != null ? item.type() : null)
                .itemReferenceId(item != null ? item.id() : null)
                .itemCode(item != null ? item.code() : null)
                .itemDescription(summary.description())
                .originalPrice(summary.originalPrice())
                .discount(summary.discount())
                .time(payment.getPaymentTime() != null ? payment.getPaymentTime() : payment.getCreatedAt())
                .paymentTime(payment.getPaymentTime())
                .createdAt(payment.getCreatedAt())
                .memberId(member != null ? member.getId() : null)
                .memberCode(member != null ? member.getMemberCode() : null)
                .referenceCode(payment.getReferenceCode())
                .settlementType(payment.getSettlementType())
                .settlementProvider(payment.getSettlementProvider())
                .simulatedTransactionId(payment.getSimulatedTransactionId())
                .invoiceId(invoice != null ? invoice.getId() : null)
                .invoiceNumber(invoice != null ? invoice.getInvoiceNumber() : null)
                .build();
    }

    /** Staff read every receipt; a member only their own. Mapped to 403 by GlobalExceptionHandler. */
    private static void requireOwnerOrStaff(MemberProfile owner, UserAccount actor, String what) {
        if (SecurityUtils.isStaff(actor)) {
            return;
        }
        boolean isOwner = SecurityUtils.isMember(actor) && owner != null && owner.getId() != null
                && owner.getId().equals(actor.getId());
        if (!isOwner) {
            throw new AccessDeniedException("This " + what + " belongs to another member");
        }
    }

    private static void requireActor(UserAccount actor) {
        if (actor == null || actor.getId() == null) {
            throw new AccessDeniedException("Sign in to see payments and receipts");
        }
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
