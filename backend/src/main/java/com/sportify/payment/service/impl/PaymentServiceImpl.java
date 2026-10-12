package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.dto.BankTransferInstructions;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id, UserAccount actor) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));

        boolean isOwner = payment.getMember() != null && payment.getMember().getId().equals(actor.getId());
        boolean isStaffUser = isStaff(actor);

        if (!isOwner && !isStaffUser) {
            throw new AccessDeniedException("Cannot access another member's payment");
        }

        return mapToPaymentResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse updatePaymentMethod(Long id, UpdatePaymentMethodRequest request, UserAccount actor) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + id));

        boolean isOwner = payment.getMember() != null && payment.getMember().getId().equals(actor.getId());
        if (!isOwner) {
            throw new AccessDeniedException("Only the payment owner can change the payment method");
        }

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BusinessRuleException("Only PENDING payments can have their method changed");
        }

        payment.setPaymentMethod(request.getPaymentMethod());
        payment = paymentRepository.save(payment);
        return mapToPaymentResponse(payment);
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        BankTransferInstructions instructions = BankTransferInstructions.builder()
                .accountName(bankProperties.getAccountName())
                .accountNumber(bankProperties.getAccountNumber())
                .bank(bankProperties.getBank())
                .transferContent(payment.getPaymentCode())
                .build();

        String itemDescription;
        BigDecimal originalPrice;
        BigDecimal discount;

        Optional<SportPackageRegistration> regOpt = sportPackageRegistrationRepository.findByPaymentId(payment.getId());
        if (regOpt.isPresent()) {
            SportPackageRegistration reg = regOpt.get();
            SportPackage pkg = reg.getSportPackage();
            itemDescription = pkg != null ? pkg.getName() : "Sport Package Registration";
            originalPrice = reg.getOriginalPrice() != null ? reg.getOriginalPrice() : payment.getAmount();
            BigDecimal paid = reg.getPaidAmount() != null ? reg.getPaidAmount() : payment.getAmount();
            BigDecimal diff = originalPrice.subtract(paid);
            discount = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
        } else {
            Optional<MemberCard> cardOpt = memberCardRepository.findByPaymentId(payment.getId());
            if (cardOpt.isPresent()) {
                MemberCard card = cardOpt.get();
                MembershipCardTier tier = card.getTier();
                itemDescription = tier != null ? tier.getName() + " Card" : "Membership Card";
                originalPrice = tier != null && tier.getPrice() != null ? tier.getPrice() : payment.getAmount();
                BigDecimal paid = card.getPricePaid() != null ? card.getPricePaid() : payment.getAmount();
                BigDecimal diff = originalPrice.subtract(paid);
                discount = diff.compareTo(BigDecimal.ZERO) > 0 ? diff : BigDecimal.ZERO;
            } else {
                List<Invoice> invoices = invoiceRepository.findByPaymentId(payment.getId());
                if (!invoices.isEmpty()) {
                    Invoice invoice = invoices.get(0);
                    itemDescription = "Invoice " + invoice.getInvoiceNumber();
                    originalPrice = invoice.getSubtotalAmount() != null ? invoice.getSubtotalAmount() : payment.getAmount();
                    discount = invoice.getDiscountAmount() != null ? invoice.getDiscountAmount() : BigDecimal.ZERO;
                } else {
                    itemDescription = payment.getNotes() != null && !payment.getNotes().isBlank()
                            ? payment.getNotes()
                            : ("Payment " + payment.getPaymentCode());
                    originalPrice = payment.getAmount();
                    discount = BigDecimal.ZERO;
                }
            }
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .code(payment.getPaymentCode())
                .status(payment.getPaymentStatus())
                .amount(payment.getAmount())
                .method(payment.getPaymentMethod())
                .itemDescription(itemDescription)
                .originalPrice(originalPrice)
                .discount(discount)
                .bankTransferInstructions(instructions)
                .memberId(payment.getMember() != null ? payment.getMember().getId() : null)
                .memberCode(payment.getMember() != null ? payment.getMember().getMemberCode() : null)
                .notes(payment.getNotes())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private static boolean isStaff(UserAccount actor) {
        if (actor == null || actor.getRole() == null) {
            return false;
        }
        String role = actor.getRole().getCode();
        return "RECEPTIONIST".equals(role) || "MANAGER".equals(role) || "ADMIN".equals(role);
    }
}
