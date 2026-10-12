package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.repository.MemberProfileRepository;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.entity.Invoice;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MemberCardRepository memberCardRepository;
    @Mock
    private SportPackageRegistrationRepository sportPackageRegistrationRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private MemberProfileRepository memberProfileRepository;
    @Mock
    private BankTransferProperties bankProperties;
    @Mock
    private CodeFormatter codeFormatter;

    private Clock clock;
    private PaymentServiceImpl paymentService;

    private UserAccount ownerUser;
    private UserAccount otherUser;
    private UserAccount receptionistUser;
    private UserAccount managerUser;
    private UserAccount adminUser;
    private MemberProfile ownerProfile;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("UTC"));
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                memberCardRepository,
                sportPackageRegistrationRepository,
                invoiceRepository,
                memberProfileRepository,
                bankProperties,
                codeFormatter,
                clock
        );

        Role memberRole = Role.builder().id(1L).code("MEMBER").name("Member").build();
        Role recRole = Role.builder().id(2L).code("RECEPTIONIST").name("Receptionist").build();
        Role mgrRole = Role.builder().id(3L).code("MANAGER").name("Manager").build();
        Role adminRole = Role.builder().id(4L).code("ADMIN").name("Admin").build();

        ownerUser = UserAccount.builder().id(100L).email("owner@example.com").fullName("Owner Member").role(memberRole).build();
        otherUser = UserAccount.builder().id(200L).email("other@example.com").fullName("Other Member").role(memberRole).build();
        receptionistUser = UserAccount.builder().id(300L).email("rec@sportify.com").fullName("Desk Staff").role(recRole).build();
        managerUser = UserAccount.builder().id(400L).email("mgr@sportify.com").fullName("Center Mgr").role(mgrRole).build();
        adminUser = UserAccount.builder().id(500L).email("admin@sportify.com").fullName("Admin User").role(adminRole).build();

        ownerProfile = MemberProfile.builder().id(100L).userAccount(ownerUser).memberCode("MEM-0100").build();

        pendingPayment = Payment.builder()
                .id(10L)
                .paymentCode("PAY-1000")
                .member(ownerProfile)
                .amount(BigDecimal.valueOf(810000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentTime(null)
                .createdAt(LocalDateTime.of(2026, 10, 12, 10, 0, 0))
                .build();

        lenient().when(bankProperties.getAccountName()).thenReturn("Sportify Sports Center");
        lenient().when(bankProperties.getAccountNumber()).thenReturn("0000 1041 2460");
        lenient().when(bankProperties.getBank()).thenReturn("Demo Bank");
    }

    @Test
    @DisplayName("Create pending payment with valid online method succeeds and keeps paymentTime null")
    void createPendingPayment_ValidOnlineMethod_Succeeds() {
        when(paymentRepository.getNextPaymentCodeSequence()).thenReturn(1001L);
        when(codeFormatter.formatPaymentCode(1001L)).thenReturn("PAY-1001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(11L);
            return p;
        });

        Payment payment = paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(300000.00), PaymentMethod.MOMO);

        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentCode()).isEqualTo("PAY-1001");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.MOMO);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentTime()).isNull();
    }

    @Test
    @DisplayName("Create pending payment with real CodeFormatter generates code matching PAY-%04d format")
    void createPendingPayment_WithRealCodeFormatter_GeneratesFormattedCode() {
        CodeFormatter realFormatter = new CodeFormatter();
        PaymentServiceImpl serviceWithRealFormatter = new PaymentServiceImpl(
                paymentRepository,
                memberCardRepository,
                sportPackageRegistrationRepository,
                invoiceRepository,
                memberProfileRepository,
                bankProperties,
                realFormatter,
                clock
        );

        when(paymentRepository.getNextPaymentCodeSequence()).thenReturn(1042L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = serviceWithRealFormatter.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.BANK_TRANSFER);

        assertThat(payment.getPaymentCode()).isEqualTo("PAY-1042");
        assertThat(payment.getPaymentCode()).matches("^PAY-\\d{4,}$");
    }

    @Test
    @DisplayName("Create pending payment with cash or credit card throws BusinessRuleException")
    void createPendingPayment_DisallowedOnlineMethod_ThrowsBusinessRuleException() {
        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.CASH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed for online requests");

        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.CREDIT_CARD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed for online requests");
    }

    @Test
    @DisplayName("Create pending payment with amount <= 0 throws BusinessRuleException")
    void createPendingPayment_ZeroOrNegativeAmount_ThrowsBusinessRuleException() {
        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.ZERO, PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(-50000.00), PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("Mark payment success sets SUCCESS status, paymentTime, and staff notes")
    void markPaymentSuccess_UpdatesStatusTimeAndNotes() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment success = paymentService.markPaymentSuccess(10L, receptionistUser);

        assertThat(success.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(success.getPaymentTime()).isEqualTo(LocalDateTime.now(clock));
        assertThat(success.getNotes()).contains("Confirmed by staff: Desk Staff");
    }

    @Test
    @DisplayName("Mark payment failed sets FAILED status and notes")
    void markPaymentFailed_UpdatesStatusAndNotes() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment failed = paymentService.markPaymentFailed(10L, "Payment cancelled by member: Owner Member");

        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getNotes()).isEqualTo("Payment cancelled by member: Owner Member");
    }

    @Test
    @DisplayName("GET payment by owner returns PaymentResponse")
    void getPayment_Owner_ReturnsResponse() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        PaymentResponse response = paymentService.getPayment(10L, ownerUser);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("PAY-1000");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("GET payment by staff returns PaymentResponse")
    void getPayment_Staff_ReturnsResponse() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        PaymentResponse recResponse = paymentService.getPayment(10L, receptionistUser);
        PaymentResponse mgrResponse = paymentService.getPayment(10L, managerUser);
        PaymentResponse adminResponse = paymentService.getPayment(10L, adminUser);

        assertThat(recResponse).isNotNull();
        assertThat(mgrResponse).isNotNull();
        assertThat(adminResponse).isNotNull();
    }

    @Test
    @DisplayName("GET payment by other non-owner member throws 404 ResourceNotFoundException (M5)")
    void getPayment_OtherMember_ThrowsResourceNotFoundException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.getPayment(10L, otherUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found for id: 10");
    }

    @Test
    @DisplayName("Bank transfer instructions visible only when method is BANK_TRANSFER and status is PENDING")
    void getPayment_InstructionsVisibility_OnlyBankTransferAndPending() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        PaymentResponse responsePendingBank = paymentService.getPayment(10L, ownerUser);
        assertThat(responsePendingBank.getBankTransferInstructions()).isNotNull();
        assertThat(responsePendingBank.getBankTransferInstructions().getTransferContent()).isEqualTo("PAY-1000");

        pendingPayment.setPaymentMethod(PaymentMethod.MOMO);
        PaymentResponse responseMomo = paymentService.getPayment(10L, ownerUser);
        assertThat(responseMomo.getBankTransferInstructions()).isNull();

        pendingPayment.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        pendingPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        PaymentResponse responseSuccess = paymentService.getPayment(10L, ownerUser);
        assertThat(responseSuccess.getBankTransferInstructions()).isNull();
    }

    @Test
    @DisplayName("PUT payment method by owner when pending updates method")
    void updatePaymentMethod_OwnerPending_UpdatesMethod() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        PaymentResponse response = paymentService.updatePaymentMethod(10L, request, ownerUser);

        assertThat(response.getMethod()).isEqualTo(PaymentMethod.MOMO);
        verify(paymentRepository).save(pendingPayment);
    }

    @Test
    @DisplayName("PUT payment method by non-owner member throws 404 ResourceNotFoundException (M5)")
    void updatePaymentMethod_OtherMember_ThrowsResourceNotFoundException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        assertThatThrownBy(() -> paymentService.updatePaymentMethod(10L, request, otherUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found for id: 10");
    }

    @Test
    @DisplayName("PUT payment method when status is not PENDING throws BusinessRuleException")
    void updatePaymentMethod_NotPending_ThrowsBusinessRuleException() {
        pendingPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        assertThatThrownBy(() -> paymentService.updatePaymentMethod(10L, request, ownerUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Only pending payments can have their payment method updated");
    }

    @Test
    @DisplayName("PUT payment method when linked card is not PENDING_PAYMENT throws BusinessRuleException")
    void updatePaymentMethod_LinkedCardActive_ThrowsBusinessRuleException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        MemberCard activeCard = MemberCard.builder().id(1L).status(CardStatus.ACTIVE).build();
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.of(activeCard));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        assertThatThrownBy(() -> paymentService.updatePaymentMethod(10L, request, ownerUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("linked membership card is ACTIVE, not PENDING_PAYMENT");
    }

    @Test
    @DisplayName("PUT payment method when linked registration is not PENDING_PAYMENT throws BusinessRuleException")
    void updatePaymentMethod_LinkedRegistrationActive_ThrowsBusinessRuleException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        SportPackageRegistration activeReg = SportPackageRegistration.builder().id(2L).status(PackageRegistrationStatus.ACTIVE).build();
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.of(activeReg));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        assertThatThrownBy(() -> paymentService.updatePaymentMethod(10L, request, ownerUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("linked package registration is ACTIVE, not PENDING_PAYMENT");
    }

    @Test
    @DisplayName("PUT payment method with cash or credit card throws BusinessRuleException")
    void updatePaymentMethod_DisallowedMethod_ThrowsBusinessRuleException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        assertThatThrownBy(() -> paymentService.updatePaymentMethod(10L, request, ownerUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed for online payment");
    }

    @Test
    @DisplayName("Dynamic item resolution resolves sport package details")
    void getPayment_ResolvesSportPackageRegistrationDetails() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        SportPackage pkg = SportPackage.builder().id(5L).name("Swimming 30 Days").build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(20L)
                .sportPackage(pkg)
                .originalPrice(BigDecimal.valueOf(1000000.00))
                .paidAmount(BigDecimal.valueOf(810000.00))
                .build();
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.of(reg));

        PaymentResponse response = paymentService.getPayment(10L, ownerUser);

        assertThat(response.getItemDescription()).isEqualTo("Swimming 30 Days");
        assertThat(response.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000000.00));
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.valueOf(190000.00));
    }

    @Test
    @DisplayName("Dynamic item resolution resolves membership card tier details")
    void getPayment_ResolvesMemberCardDetails() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());

        MembershipCardTier tier = MembershipCardTier.builder().id(3L).name("Gold").price(BigDecimal.valueOf(1000000.00)).build();
        MemberCard card = MemberCard.builder()
                .id(30L)
                .tier(tier)
                .pricePaid(BigDecimal.valueOf(810000.00))
                .build();
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.of(card));

        PaymentResponse response = paymentService.getPayment(10L, ownerUser);

        assertThat(response.getItemDescription()).isEqualTo("Gold Card");
        assertThat(response.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1000000.00));
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.valueOf(190000.00));
    }

    @Test
    @DisplayName("Dynamic item resolution resolves invoice details")
    void getPayment_ResolvesInvoiceDetails() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());

        Invoice invoice = Invoice.builder()
                .id(40L)
                .invoiceNumber("INV-1001")
                .subtotalAmount(BigDecimal.valueOf(900000.00))
                .discountAmount(BigDecimal.valueOf(90000.00))
                .build();
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(List.of(invoice));

        PaymentResponse response = paymentService.getPayment(10L, ownerUser);

        assertThat(response.getItemDescription()).isEqualTo("Invoice INV-1001");
        assertThat(response.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(900000.00));
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.valueOf(90000.00));
    }
}
