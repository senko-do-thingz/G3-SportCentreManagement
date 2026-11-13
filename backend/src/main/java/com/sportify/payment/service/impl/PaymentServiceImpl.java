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
import com.sportify.core.exception.ConflictException;
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
    private final CashCaptureService cashCaptureService;

    @Override
    @Transactional
    public PaymentResponse submitForVerification(Long id, UserAccount actor) {
        Payment payment = ownedPendingPayment(id, actor);
        if (payment.getSubmittedForVerificationAt() == null) {
            payment.setSubmittedForVerificationAt(LocalDateTime.now(clock));
            paymentRepository.saveAndFlush(payment);
            auditPayment(com.sportify.core.audit.AuditAction.PAYMENT_SUBMITTED, payment, actor);
        }
        return mapToPaymentResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Long id, UserAccount actor) {
        Payment payment = ownedPendingPayment(id, actor);
        if (payment.getSubmittedForVerificationAt() != null || payment.getPaymentTime() != null
                || payment.getReferenceCode() != null || payment.getSettlementType() != null) {
            throw new ConflictException("This payment has been submitted or settlement has begun and can no longer be cancelled");
        }
        var registration = sportPackageRegistrationRepository.findByPaymentId(id);
        var card = memberCardRepository.findByPaymentId(id);
        if (registration.isPresent() == card.isPresent()) {
            throw new ConflictException("Payment must have exactly one linked pending item");
        }
        if (registration.isPresent()) {
            var item = registration.get();
            if (item.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT) {
                throw new ConflictException("Package registration is no longer pending");
            }
            item.setStatus(PackageRegistrationStatus.CANCELLED);
            item.setCancelledAt(LocalDateTime.now(clock));
            item.setCancelReason("Payment cancelled by member");
            sportPackageRegistrationRepository.save(item);
        } else {
            var item = card.get();
            if (item.getStatus() != CardStatus.PENDING_PAYMENT) {
                throw new ConflictException("Membership card is no longer pending");
            }
            item.setStatus(CardStatus.CANCELLED);
            memberCardRepository.save(item);
        }
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
        payment.setNotes(PaymentNotes.append(payment.getNotes(), "Payment cancelled by member user id " + actor.getId()));
        paymentRepository.saveAndFlush(payment);
        auditPayment(com.sportify.core.audit.AuditAction.PAYMENT_CANCELLED, payment, actor);
        PaymentResponse response = mapToPaymentResponse(payment);
        response.setNotes(null);
        return response;
    }

    private Payment ownedPendingPayment(Long id, UserAccount actor) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));
        if (actor == null || payment.getMember() == null || !payment.getMember().getId().equals(actor.getId())) {
            throw new ResourceNotFoundException("Payment not found for id: " + id);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Payment is " + payment.getPaymentStatus() + " and can no longer be changed");
        }
        return payment;
    }
    private com.sportify.catalog.service.OperationalMemberPolicy transferMemberPolicy;
    private com.sportify.core.audit.AuditService auditService;

    @org.springframework.beans.factory.annotation.Autowired
    void setAuditService(com.sportify.core.audit.AuditService auditService) {
        this.auditService = auditService;
    }

    private void auditPayment(String action, Payment payment, UserAccount actor) {
        if (auditService != null) auditService.record(com.sportify.core.audit.AuditEvent.builder()
                .actor(actor).action(action).entityType("PAYMENT").entityId(payment.getId())
                .entityCode(payment.getPaymentCode()).summary("Payment " + payment.getPaymentCode() + " "
                        + (action.equals(com.sportify.core.audit.AuditAction.PAYMENT_CANCELLED) ? "cancelled" : "submitted for verification"))
                .build());
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setTransferMemberPolicy(com.sportify.catalog.service.OperationalMemberPolicy policy) {
        this.transferMemberPolicy = policy;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment createPendingPayment(MemberProfile member, BigDecimal amount, PaymentMethod method) {
        validateNewPayment(member, amount);

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

    /**
     * Money taken at the desk (BE-19): the payment is created already SUCCESS, with payment_time now, the method and
     * reference the receptionist recorded and confirmed_by_user_id. Supported desk methods are CASH and BANK_TRANSFER.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Payment recordDeskPayment(MemberProfile member, BigDecimal amount, PaymentMethod method, String referenceCode,
                                     UserAccount confirmedBy) {
        validateNewPayment(member, amount);
        if (method == null) {
            throw new BusinessRuleException("Payment method is required for a desk payment");
        }
        if (!SUPPORTED_PAYMENT_METHODS.contains(method)) {
            throw new BusinessRuleException("Payment method " + method
                    + " is not supported. Supported methods: " + SUPPORTED_PAYMENT_METHODS);
        }
        if (confirmedBy == null || !SecurityUtils.isStaff(confirmedBy)) {
            throw new BusinessRuleException("Only staff users can record a desk payment");
        }

        var capturedAt = cashCaptureService.prepareCollection(0);
        Payment payment = Payment.builder()
                .paymentCode(codeFormatter.formatPaymentCode(paymentRepository.getNextPaymentCodeSequence()))
                .member(member)
                .amount(amount)
                .paymentMethod(method)
                .paymentStatus(PaymentStatus.SUCCESS)
                .paymentTime(capturedAt == null ? LocalDateTime.now(clock) : LocalDateTime.ofInstant(capturedAt, clock.getZone()))
                .referenceCode(referenceCode)
                .confirmedByUser(confirmedBy)
                .notes("Paid at the desk, confirmed by user id " + confirmedBy.getId())
                .build();
        markActualSettlement(payment, method);
        return paymentRepository.save(payment);
    }

    /**
     * Staff-confirmed money is a real (non-simulated) settlement. Recording it as ACTUAL lets
     * Reception and Manager screens show a verified receipt instead of "Legacy unverified".
     */
    private static void markActualSettlement(Payment payment, PaymentMethod method) {
        if (payment.getSettlementType() != null) {
            return;
        }
        payment.setSettlementType("ACTUAL");
        payment.setSettlementProvider(method == PaymentMethod.CASH ? "DESK_CASH" : "MANUAL_TRANSFER");
    }

    private static void validateNewPayment(MemberProfile member, BigDecimal amount) {
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

        PaymentMethod effectiveMethod = actualMethod != null ? actualMethod : payment.getPaymentMethod();
        if (effectiveMethod == null || !SETTLEMENT_PAYMENT_METHODS.contains(effectiveMethod)) {
            throw new BusinessRuleException("Payment method " + effectiveMethod
                    + " is not supported. Supported methods: " + SETTLEMENT_PAYMENT_METHODS);
        }
        payment.setPaymentMethod(effectiveMethod);

        var capturedAt = cashCaptureService.prepareCollection(paymentId);
        LocalDateTime now = capturedAt == null ? LocalDateTime.now(clock) : LocalDateTime.ofInstant(capturedAt, clock.getZone());
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentTime(now);
        markActualSettlement(payment, effectiveMethod);

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
    public Payment markPaymentCancelled(Long paymentId, String reason) {
        Payment payment = lockPayment(paymentId);
        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getSubmittedForVerificationAt() != null
                || payment.getPaymentTime() != null || payment.getReferenceCode() != null
                || payment.getSettlementType() != null) {
            throw new ConflictException("This payment can no longer be cancelled");
        }
        payment.setPaymentStatus(PaymentStatus.CANCELLED);
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
        if (payment.getSubmittedForVerificationAt() != null) {
            throw new ConflictException("Payment has been submitted for verification and cannot be changed");
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
        if (isStaffUser && !SUPPORTED_PAYMENT_METHODS.contains(newMethod)) {
            throw new BusinessRuleException("Payment method " + newMethod
                    + " is not supported. Supported methods: " + SUPPORTED_PAYMENT_METHODS);
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
            boolean payable = liveTransferAllowed(payment);
            instructions = BankTransferInstructions.builder()
                    .accountName(payable ? bankProperties.getAccountName() : "DEMO NON-PAYABLE")
                    .accountNumber(payable ? bankProperties.getAccountNumber() : "NOT CONFIGURED")
                    .bank(payable ? bankProperties.getBank() : "DEMO NON-PAYABLE")
                    .transferContent(payment.getPaymentCode())
                    .payable(payable)
                    .notice(payable
                            ? "Manual transfer instructions. Payment remains pending until staff verifies receipt."
                            : "DEMO NON-PAYABLE. No verified beneficiary is configured.")
                    .build();
        }

        PaymentItemResolver.PaymentItemSummary summary = itemResolver.summarize(payment);
        var invoiceOpt = itemResolver.findInvoice(payment.getId());
        Long invoiceId = invoiceOpt.map(com.sportify.payment.entity.Invoice::getId).orElse(null);
        String invoiceNumber = invoiceOpt.map(com.sportify.payment.entity.Invoice::getInvoiceNumber).orElse(null);

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
                .referenceCode(payment.getReferenceCode())
                .settlementType(payment.getSettlementType())
                .settlementProvider(payment.getSettlementProvider())
                .simulatedTransactionId(payment.getSimulatedTransactionId())
                .invoiceId(invoiceId)
                .invoiceNumber(invoiceNumber)
                .notes(payment.getNotes())
                .paymentTime(payment.getPaymentTime())
                .submittedForVerificationAt(payment.getSubmittedForVerificationAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private boolean liveTransferAllowed(Payment payment) {
        if (!bankProperties.isLiveInstructionsEnabled() || !bankProperties.isLiveBeneficiaryValid()
                || transferMemberPolicy == null || payment.getMember() == null
                || "SIMULATED".equals(payment.getSettlementType()) || payment.getSimulatedTransactionId() != null
                || "SPORTIFY_SIMULATOR".equals(payment.getSettlementProvider())) return false;
        try {
            transferMemberPolicy.requireRealMember(payment.getMember().getId());
            return true;
        } catch (BusinessRuleException quarantined) {
            return false;
        }
    }
}
