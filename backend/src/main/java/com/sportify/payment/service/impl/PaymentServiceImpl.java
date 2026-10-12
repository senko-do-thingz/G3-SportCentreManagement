package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.common.SecurityUtils;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
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
    private final MemberProfileRepository memberProfileRepository;
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
    public Payment createPendingPayment(UserAccount memberUser, BigDecimal amount, PaymentMethod method) {
        if (memberUser == null) {
            throw new BusinessRuleException("User account cannot be null when creating a payment");
        }
        MemberProfile member = memberProfileRepository.findById(memberUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Member profile not found for user id: " + memberUser.getId()));
        return createPendingPayment(member, amount, method);
    }

    @Override
    @Transactional
    public Payment markPaymentSuccess(Long paymentId, UserAccount confirmedBy) {
        if (paymentId == null) {
            return null;
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaymentTime(LocalDateTime.now(clock));
            if (confirmedBy != null) {
                payment.setConfirmedByUser(confirmedBy);
                String staffNote = "Confirmed by staff: " + confirmedBy.getFullName();
                payment.setNotes(appendNote(payment.getNotes(), staffNote));
            }
            return paymentRepository.save(payment);
        }
        return payment;
    }

    @Override
    @Transactional
    public Payment markPaymentFailed(Long paymentId, String reason) {
        if (paymentId == null) {
            return null;
        }
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));

        if (payment.getPaymentStatus() == PaymentStatus.PENDING) {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setNotes(truncateNotes(reason));
            return paymentRepository.save(payment);
        }
        return payment;
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id, UserAccount actor) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));

        boolean isOwner = payment.getMember() != null && payment.getMember().getId().equals(actor.getId());
        boolean isStaffUser = SecurityUtils.isStaff(actor);

        if (!isOwner && !isStaffUser) {
            throw new ResourceNotFoundException("Payment not found for id: " + id);
        }

        return mapToPaymentResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request, UserAccount actor) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));

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

        PaymentMethod newMethod = request.getPaymentMethod();
        if (newMethod == null || !ONLINE_ALLOWED_PAYMENT_METHODS.contains(newMethod)) {
            throw new BusinessRuleException("Payment method " + newMethod
                    + " is not allowed for online payment. Allowed methods: " + ONLINE_ALLOWED_PAYMENT_METHODS);
        }

        payment.setPaymentMethod(newMethod);
        payment = paymentRepository.save(payment);
        return mapToPaymentResponse(payment);
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
        if (append == null) {
            return existing;
        }
        String combined = (existing != null && !existing.isBlank())
                ? existing + " | " + append
                : append;
        return truncateNotes(combined);
    }

    private String truncateNotes(String note) {
        if (note == null) {
            return null;
        }
        return note.length() > 500 ? note.substring(0, 500) : note;
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
        Invoice invoice = invoices.get(0);
        String description = "Invoice " + invoice.getInvoiceNumber();
        BigDecimal original = invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : fallbackAmount;
        BigDecimal discount = invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO;
        return Optional.of(new PaymentItemSummary(description, original, discount));
    }

    private PaymentItemSummary defaultItemSummary(Payment payment) {
        String description = payment.getNotes() != null && !payment.getNotes().isBlank()
                ? payment.getNotes()
                : "Payment " + payment.getPaymentCode();
        return new PaymentItemSummary(description, payment.getAmount(), BigDecimal.ZERO);
    }
}
