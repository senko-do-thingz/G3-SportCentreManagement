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
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final MemberCardRepository memberCardRepository;
    private final SportPackageRegistrationRepository sportPackageRegistrationRepository;
    private final PaymentItemResolver itemResolver;
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
            payment.setNotes(PaymentNotes.append(payment.getNotes(), staffNote));
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
        payment.setNotes(PaymentNotes.append(payment.getNotes(), reason));

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment lockPayment(Long paymentId) {
        if (paymentId == null) {
            throw new BusinessRuleException("Payment ID cannot be null");
        }
        return paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
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

        PaymentItemResolver.PaymentItemSummary summary = itemResolver.summarize(payment);

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
}
