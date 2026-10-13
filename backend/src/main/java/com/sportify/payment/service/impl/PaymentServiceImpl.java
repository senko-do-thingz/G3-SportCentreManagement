package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.dto.BankTransferInstructions;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberCardRepository memberCardRepository;
    private final SportPackageRegistrationRepository sportPackageRegistrationRepository;
    private final InvoiceRepository invoiceRepository;
    private final BankTransferProperties bankProperties;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment createPendingPayment(MemberProfile member, BigDecimal amount, PaymentMethod method) {
        if (member == null) {
            throw new BusinessRuleException("Member cannot be null when creating a payment");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Payment amount must be greater than zero. Attempted amount: " + amount);
        }
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) {
            throw new BusinessRuleException("Payment amount cannot have more than 2 decimal places. Attempted amount: " + amount);
        }
        if (amount.toBigInteger().toString().length() > 10) {
            throw new BusinessRuleException("Payment amount cannot have more than 10 integer digits. Attempted amount: " + amount);
        }

        PaymentMethod chosenMethod = (method != null) ? method : PaymentMethod.BANK_TRANSFER;
        if (!ONLINE_ALLOWED_PAYMENT_METHODS.contains(chosenMethod)) {
            throw new BusinessRuleException("Payment method " + chosenMethod
                    + " is not allowed for online requests. Allowed methods: " + ONLINE_ALLOWED_PAYMENT_METHODS);
        }

        Long seq = paymentRepository.getNextPaymentCodeSequence();
        String code = codeFormatter.formatPaymentCode(seq);

        Payment payment = Payment.builder()
                .paymentCode(code)
                .member(member)
                .amount(amount)
                .paymentMethod(chosenMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentTime(null)
                .build();

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment markPaymentSuccess(Long paymentId, UserAccount confirmedBy) {
        return markPaymentSuccess(paymentId, confirmedBy, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment markPaymentSuccess(Long paymentId, UserAccount confirmedBy, PaymentMethod actualMethod) {
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        if (confirmedBy != null && !SecurityUtils.isStaff(confirmedBy)) {
            throw new BusinessRuleException("Only staff users can confirm payment");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException("Cannot mark payment as success: payment is in status "
                    + payment.getPaymentStatus() + ", expected PENDING");
        }

        if (actualMethod != null) {
            payment.setPaymentMethod(actualMethod);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentTime(now);

        if (confirmedBy != null) {
            payment.setConfirmedByUser(confirmedBy);
            String staffNote = "Confirmed by user id " + confirmedBy.getId();
            payment.setNotes(appendNote(payment.getNotes(), staffNote));
        }

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment markPaymentFailed(Long paymentId, String reason) {
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException("Cannot mark payment as failed: payment is in status "
                    + payment.getPaymentStatus() + ", expected PENDING");
        }

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setNotes(appendNote(payment.getNotes(), reason));

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id, UserAccount actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Actor cannot be null");
        }
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));

        // MemberProfile shares its primary key with UserAccount (@MapsId user_id), so member id equals user id by design.
        boolean isOwner = payment.getMember() != null && payment.getMember().getId().equals(actor.getId());
        boolean isStaffUser = SecurityUtils.isStaff(actor);

        if (!isOwner && !isStaffUser) {
            throw new ResourceNotFoundException("Payment not found for id: " + id);
        }

        PaymentResponse response = mapToPaymentResponse(payment);
        if (!isStaffUser) {
            response.setNotes(null);
        }
        return response;
    }

    @Override
    @Transactional
    public PaymentResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request, UserAccount actor) {
        if (actor == null) {
            throw new IllegalArgumentException("Actor cannot be null");
        }
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));

        // MemberProfile shares its primary key with UserAccount (@MapsId user_id), so member id equals user id by design.
        boolean isOwner = payment.getMember() != null && payment.getMember().getId().equals(actor.getId());
        boolean isStaffUser = SecurityUtils.isStaff(actor);

        if (!isOwner && !isStaffUser) {
            throw new ResourceNotFoundException("Payment not found for id: " + id);
        }

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException("Only pending payments can have their payment method updated");
        }

        memberCardRepository.findByPaymentId(payment.getId()).ifPresent(card -> {
            if (card.getStatus() != CardStatus.PENDING_PAYMENT) {
                throw new BusinessRuleException("Cannot update payment method because linked membership card is "
                        + card.getStatus() + ", not PENDING_PAYMENT");
            }
        });

        sportPackageRegistrationRepository.findByPaymentId(payment.getId()).ifPresent(reg -> {
            if (reg.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT) {
                throw new BusinessRuleException("Cannot update payment method because linked package registration is "
                        + reg.getStatus() + ", not PENDING_PAYMENT");
            }
        });

        PaymentMethod newMethod = request != null ? request.getPaymentMethod() : null;
        if (newMethod == null) {
            throw new BusinessRuleException("Payment method cannot be null");
        }
        if (!isStaffUser && !ONLINE_ALLOWED_PAYMENT_METHODS.contains(newMethod)) {
            throw new BusinessRuleException("Payment method " + newMethod
                    + " is not allowed for online payment. Allowed methods: " + ONLINE_ALLOWED_PAYMENT_METHODS);
        }

        payment.setPaymentMethod(newMethod);
        payment = paymentRepository.save(payment);

        PaymentResponse response = mapToPaymentResponse(payment);
        if (!isStaffUser) {
            response.setNotes(null);
        }
        return response;
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        BankTransferInstructions instructions = null;
        if (payment.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                && payment.getPaymentStatus() == PaymentStatus.PENDING) {
            instructions = BankTransferInstructions.builder()
                    .accountName(bankProperties.getAccountName())
                    .accountNumber(bankProperties.getAccountNumber())
                    .bank(bankProperties.getBank())
                    .transferContent(payment.getPaymentCode())
                    .build();
        }

        PaymentItemSummary summary = resolveItemSummary(payment);

        return PaymentResponse.builder()
                .id(payment.getId())
                .code(payment.getPaymentCode())
                .status(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .method(payment.getPaymentMethod())
                .itemDescription(summary.description())
                .originalPrice(summary.originalPrice())
                .discount(summary.discount())
                .bankTransferInstructions(instructions)
                .memberId(payment.getMember() != null ? payment.getMember().getId() : null)
                .memberCode(payment.getMember() != null ? payment.getMember().getMemberCode() : null)
                .notes(payment.getNotes())
                .paymentTime(payment.getPaymentTime())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private String appendNote(String existing, String append) {
        if (append == null || append.isBlank()) {
            return truncateSingleNote(existing);
        }
        if (existing == null || existing.isBlank()) {
            return truncateSingleNote(append);
        }

        // If append alone exceeds 500 characters, cut append to 500 safely and drop existing.
        if (append.length() > 500) {
            return truncateSingleNote(append);
        }

        // Here append is <= 500 characters and must be preserved intact.
        String candidate = existing + " | " + append;
        if (candidate.length() <= 500) {
            return candidate;
        }

        // Drop oldest segments separated by " | " until candidate length fits or no more separators.
        String curExisting = existing;
        while (curExisting.contains(" | ") && (curExisting.length() + 3 + append.length() > 500)) {
            int firstSep = curExisting.indexOf(" | ");
            curExisting = curExisting.substring(firstSep + 3);
        }

        if (curExisting.length() + 3 + append.length() <= 500) {
            return curExisting + " | " + append;
        }

        // If even the remaining single segment is too long to fit with " | " + append:
        int remainingBudget = 500 - 3 - append.length();
        if (remainingBudget <= 0) {
            return append;
        }

        // Cut oldest characters from curExisting (keep tail) to fit remainingBudget
        int start = curExisting.length() - remainingBudget;
        if (Character.isLowSurrogate(curExisting.charAt(start))) {
            start++;
        }
        String trimmedExisting = curExisting.substring(start);
        return trimmedExisting + " | " + append;
    }

    private String truncateSingleNote(String note) {
        if (note == null) {
            return null;
        }
        if (note.length() <= 500) {
            return note;
        }
        int cutoff = 500;
        if (Character.isHighSurrogate(note.charAt(cutoff - 1))) {
            cutoff--;
        }
        return note.substring(0, cutoff);
    }

    private record PaymentItemSummary(String description, BigDecimal originalPrice, BigDecimal discount) {}

    private PaymentItemSummary resolveItemSummary(Payment payment) {
        Long paymentId = payment.getId();
        BigDecimal amount = payment.getAmount();

        Optional<PaymentItemSummary> pkgSummary = resolvePackageSummary(paymentId, amount);
        if (pkgSummary.isPresent()) {
            return pkgSummary.get();
        }

        Optional<PaymentItemSummary> cardSummary = resolveCardSummary(paymentId, amount);
        if (cardSummary.isPresent()) {
            return cardSummary.get();
        }

        Optional<PaymentItemSummary> invSummary = resolveInvoiceSummary(paymentId, amount);
        if (invSummary.isPresent()) {
            return invSummary.get();
        }

        return defaultItemSummary(payment);
    }

    private Optional<PaymentItemSummary> resolvePackageSummary(Long paymentId, BigDecimal fallbackAmount) {
        return sportPackageRegistrationRepository.findByPaymentId(paymentId)
                .map(reg -> {
                    String description = reg.getSportPackage() != null
                            ? reg.getSportPackage().getName()
                            : "Sport Package Registration";
                    BigDecimal original = reg.getOriginalPrice() != null ? reg.getOriginalPrice() : fallbackAmount;
                    BigDecimal paid = reg.getPaidAmount() != null ? reg.getPaidAmount() : fallbackAmount;
                    BigDecimal diff = original.subtract(paid);
                    BigDecimal discount = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                    return new PaymentItemSummary(description, original, discount);
                });
    }

    private Optional<PaymentItemSummary> resolveCardSummary(Long paymentId, BigDecimal fallbackAmount) {
        return memberCardRepository.findByPaymentId(paymentId)
                .map(card -> {
                    String description = card.getTier() != null
                            ? card.getTier().getName() + " Card"
                            : "Membership Card";
                    BigDecimal original = card.getTier() != null && card.getTier().getPrice() != null
                            ? card.getTier().getPrice()
                            : fallbackAmount;
                    BigDecimal paid = card.getPricePaid() != null ? card.getPricePaid() : fallbackAmount;
                    BigDecimal diff = original.subtract(paid);
                    BigDecimal discount = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
                    return new PaymentItemSummary(description, original, discount);
                });
    }

    private Optional<PaymentItemSummary> resolveInvoiceSummary(Long paymentId, BigDecimal fallbackAmount) {
        List<Invoice> invoices = invoiceRepository.findByPaymentId(paymentId);
        if (invoices.isEmpty()) {
            return Optional.empty();
        }
        Invoice invoice = invoices.stream()
                .min(java.util.Comparator.comparing(Invoice::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElse(invoices.get(0));
        String description = "Invoice " + invoice.getInvoiceNumber();
        BigDecimal original = invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : fallbackAmount;
        BigDecimal discount = invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO;
        return Optional.of(new PaymentItemSummary(description, original, discount));
    }

    private PaymentItemSummary defaultItemSummary(Payment payment) {
        return new PaymentItemSummary("Payment " + payment.getPaymentCode(), payment.getAmount(), BigDecimal.ZERO);
    }
}
