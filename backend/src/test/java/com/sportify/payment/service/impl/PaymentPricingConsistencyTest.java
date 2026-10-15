package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * GET /api/v1/payments/{id} (PaymentServiceImpl) and GET /api/v1/payments (PaymentVerificationServiceImpl) read
 * the item name, original price and discount through one PaymentItemResolver, so they always agree.
 */
@ExtendWith(MockitoExtension.class)
class PaymentPricingConsistencyTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private MemberCardRepository memberCardRepository;
    @Mock private SportPackageRegistrationRepository registrationRepository;
    @Mock private BankTransferProperties bankProperties;
    @Mock private MembershipCardService membershipCardService;
    @Mock private SportPackageService sportPackageService;
    @Mock private AuditService auditService;

    private PaymentServiceImpl paymentService;
    private PaymentVerificationServiceImpl verificationService;
    private UserAccount receptionist;
    private MemberProfile member;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        PaymentItemResolver resolver = new PaymentItemResolver(memberCardRepository, registrationRepository, invoiceRepository);
        CodeFormatter formatter = new CodeFormatter();
        paymentService = new PaymentServiceImpl(paymentRepository, memberCardRepository, registrationRepository,
                resolver, bankProperties, formatter, clock);
        verificationService = new PaymentVerificationServiceImpl(paymentRepository, invoiceRepository, resolver,
                paymentService, membershipCardService, sportPackageService, auditService, formatter, clock);

        receptionist = UserAccount.builder().id(7L).fullName("Rita Reception")
                .role(Role.builder().code("RECEPTIONIST").name("Receptionist").build()).build();
        UserAccount memberUser = UserAccount.builder().id(100L).fullName("Minh Member")
                .role(Role.builder().code("MEMBER").name("Member").build()).build();
        member = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-0100").build();
        lenient().when(invoiceRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
    }

    private Payment payment(String amount) {
        return Payment.builder().id(1L).paymentCode("PAY-1001").member(member)
                .amount(new BigDecimal(amount)).paymentMethod(PaymentMethod.CASH)
                .paymentStatus(PaymentStatus.PENDING).build();
    }

    @SuppressWarnings("unchecked")
    private void assertBothViewsAgree(Payment payment, String expectedName, String expectedOriginal, String expectedDiscount) {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 20), 1));

        PaymentResponse single = paymentService.getPayment(1L, receptionist);
        StaffPaymentResponse row = verificationService.listPayments(null, null, null, null, null, 0, 20).content().get(0);

        assertThat(row.getItemDescription()).isEqualTo(single.getItemDescription()).isEqualTo(expectedName);
        assertThat(row.getOriginalPrice()).isEqualByComparingTo(single.getOriginalPrice()).isEqualByComparingTo(expectedOriginal);
        assertThat(row.getDiscount()).isEqualByComparingTo(single.getDiscount()).isEqualByComparingTo(expectedDiscount);
    }

    private SportPackageRegistration registration(Payment payment, String paidAmount) {
        return SportPackageRegistration.builder().id(20L).registrationCode("REG-PKG-1020").member(member)
                .sportPackage(SportPackage.builder().id(5L).name("Swimming 30 Days").durationDays(30).build())
                .originalPrice(new BigDecimal("400000.00")).discountPercentage(5)
                .paidAmount(new BigDecimal(paidAmount))
                .status(PackageRegistrationStatus.PENDING_PAYMENT).payment(payment).build();
    }

    @Test
    @DisplayName("Package with a card discount: both views show 400000 and 20000")
    void registrationWithDiscount_SameValues() {
        Payment payment = payment("380000.00");
        SportPackageRegistration reg = registration(payment, "380000.00");
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.of(reg));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(reg));
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());

        assertBothViewsAgree(payment, "Swimming 30 Days", "400000", "20000");
    }

    @Test
    @DisplayName("Registration paid amount differs from the payment amount: both views still use the same rule")
    void registrationPaidAmountDiffersFromPayment_SameValues() {
        Payment payment = payment("390000.00");
        SportPackageRegistration reg = registration(payment, "380000.00");
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.of(reg));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(reg));
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());

        assertBothViewsAgree(payment, "Swimming 30 Days", "400000", "20000");
    }

    @Test
    @DisplayName("Card upgrade: both views show price_paid plus the stored credit, and the credit as the discount")
    void cardUpgrade_SameValues() {
        Payment payment = payment("1500000.00");
        MemberCard replaced = MemberCard.builder().id(29L).cardCode("CARD-1029").member(member)
                .status(CardStatus.ACTIVE).pricePaid(new BigDecimal("500000.00")).build();
        MemberCard card = MemberCard.builder().id(30L).cardCode("CARD-1030").member(member)
                .tier(MembershipCardTier.builder().id(3L).code("VIP").name("VIP Member")
                        .price(new BigDecimal("2000000.00")).durationMonths(12).discountPercentage(10).build())
                .status(CardStatus.PENDING_PAYMENT).pricePaid(new BigDecimal("1500000.00")).payment(payment)
                .upgradeRequest(true).replacesCard(replaced).build();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.of(card));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(card));

        assertBothViewsAgree(payment, "VIP Member Card", "2000000", "500000");
    }

    @Test
    @DisplayName("Tier price lowered after the request: both views show price_paid as the original price, no discount")
    void tierPriceLoweredAfterRequest_SameValues() {
        Payment payment = payment("1200000.00");
        MemberCard card = MemberCard.builder().id(30L).cardCode("CARD-1030").member(member)
                .tier(MembershipCardTier.builder().id(3L).code("VIP").name("VIP Member")
                        .price(new BigDecimal("1000000.00")).durationMonths(12).discountPercentage(10).build())
                .status(CardStatus.PENDING_PAYMENT).pricePaid(new BigDecimal("1200000.00")).payment(payment).build();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.of(card));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(card));

        assertBothViewsAgree(payment, "VIP Member Card", "1200000", "0");
    }

    @Test
    @DisplayName("Tier price raised after the request: both views show price_paid as the original price, no discount")
    void tierPriceRaisedAfterRequest_SameValues() {
        Payment payment = payment("1200000.00");
        MemberCard card = MemberCard.builder().id(30L).cardCode("CARD-1030").member(member)
                .tier(MembershipCardTier.builder().id(3L).code("VIP").name("VIP Member")
                        .price(new BigDecimal("1500000.00")).durationMonths(12).discountPercentage(10).build())
                .status(CardStatus.PENDING_PAYMENT).pricePaid(new BigDecimal("1200000.00")).payment(payment).build();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.of(card));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(card));

        assertBothViewsAgree(payment, "VIP Member Card", "1200000", "0");
    }
}
