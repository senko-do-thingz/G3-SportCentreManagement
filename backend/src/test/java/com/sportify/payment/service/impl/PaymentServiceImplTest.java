package com.sportify.payment.service.impl;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.MembershipCardTier;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MemberCardRepository memberCardRepository;
    @Mock
    private SportPackageRegistrationRepository sportPackageRegistrationRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private BankTransferProperties bankProperties;

    @InjectMocks
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
                .paymentTime(LocalDateTime.of(2026, 10, 12, 10, 0, 0))
                .createdAt(LocalDateTime.of(2026, 10, 12, 10, 0, 0))
                .build();

        lenient().when(bankProperties.getAccountName()).thenReturn("Sportify Sports Center");
        lenient().when(bankProperties.getAccountNumber()).thenReturn("0000 1041 2460");
        lenient().when(bankProperties.getBank()).thenReturn("Demo Bank");
    }

    @Test
    void getPayment_AsOwner_Success() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());

        PaymentResponse res = paymentService.getPayment(10L, ownerUser);

        assertNotNull(res);
        assertEquals("PAY-1000", res.getCode());
        assertEquals(PaymentStatus.PENDING, res.getStatus());
        assertEquals(BigDecimal.valueOf(810000.00), res.getAmount());
        assertEquals(PaymentMethod.BANK_TRANSFER, res.getMethod());
        assertNotNull(res.getBankTransferInstructions());
        assertEquals("Sportify Sports Center", res.getBankTransferInstructions().getAccountName());
        assertEquals("0000 1041 2460", res.getBankTransferInstructions().getAccountNumber());
        assertEquals("Demo Bank", res.getBankTransferInstructions().getBank());
        assertEquals("PAY-1000", res.getBankTransferInstructions().getTransferContent());
    }

    @Test
    void getPayment_AsStaff_Success() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());

        PaymentResponse resRec = paymentService.getPayment(10L, receptionistUser);
        assertNotNull(resRec);
        assertEquals("PAY-1000", resRec.getCode());

        PaymentResponse resMgr = paymentService.getPayment(10L, managerUser);
        assertNotNull(resMgr);
        assertEquals("PAY-1000", resMgr.getCode());

        PaymentResponse resAdmin = paymentService.getPayment(10L, adminUser);
        assertNotNull(resAdmin);
        assertEquals("PAY-1000", resAdmin.getCode());
    }

    @Test
    void getPayment_AsOtherMember_ThrowsAccessDeniedException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        assertThrows(AccessDeniedException.class, () -> paymentService.getPayment(10L, otherUser));
    }

    @Test
    void getPayment_NotFound_ThrowsResourceNotFoundException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> paymentService.getPayment(999L, ownerUser));
    }

    @Test
    void getPayment_ForSportPackageRegistration_ResolvesPackageDescriptionAndDiscount() {
        SportPackage pkg = SportPackage.builder()
                .id(5L)
                .code("FB_30D")
                .name("Football / Coach-led / 30 days")
                .priceAmount(BigDecimal.valueOf(900000.00))
                .build();
        SportPackageRegistration reg = SportPackageRegistration.builder()
                .id(1041L)
                .sportPackage(pkg)
                .originalPrice(BigDecimal.valueOf(900000.00))
                .paidAmount(BigDecimal.valueOf(810000.00))
                .discountPercentage(10)
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.of(reg));

        PaymentResponse res = paymentService.getPayment(10L, ownerUser);

        assertEquals("Football / Coach-led / 30 days", res.getItemDescription());
        assertEquals(BigDecimal.valueOf(900000.00), res.getOriginalPrice());
        assertEquals(BigDecimal.valueOf(90000.00), res.getDiscount());
        assertEquals(BigDecimal.valueOf(810000.00), res.getAmount());
    }

    @Test
    void getPayment_ForMemberCard_ResolvesCardDescriptionAndDiscount() {
        MembershipCardTier tier = MembershipCardTier.builder()
                .id(2L)
                .code("GOLD")
                .name("Gold")
                .price(BigDecimal.valueOf(500000.00))
                .build();
        MemberCard card = MemberCard.builder()
                .id(20L)
                .tier(tier)
                .pricePaid(BigDecimal.valueOf(400000.00))
                .status(CardStatus.PENDING_PAYMENT)
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.of(card));

        PaymentResponse res = paymentService.getPayment(10L, ownerUser);

        assertEquals("Gold Card", res.getItemDescription());
        assertEquals(BigDecimal.valueOf(500000.00), res.getOriginalPrice());
        assertEquals(BigDecimal.valueOf(100000.00), res.getDiscount());
    }

    @Test
    void getPayment_ForInvoice_ResolvesInvoiceDetails() {
        Invoice invoice = Invoice.builder()
                .id(30L)
                .invoiceNumber("INV-1000")
                .subtotalAmount(BigDecimal.valueOf(600000.00))
                .discountAmount(BigDecimal.valueOf(50000.00))
                .totalAmount(BigDecimal.valueOf(550000.00))
                .build();

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(List.of(invoice));

        PaymentResponse res = paymentService.getPayment(10L, ownerUser);

        assertEquals("Invoice INV-1000", res.getItemDescription());
        assertEquals(BigDecimal.valueOf(600000.00), res.getOriginalPrice());
        assertEquals(BigDecimal.valueOf(50000.00), res.getDiscount());
    }

    @Test
    void getPayment_Fallback_ResolvesPaymentCodeAndAmount() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(invoiceRepository.findByPaymentId(10L)).thenReturn(Collections.emptyList());

        PaymentResponse res = paymentService.getPayment(10L, ownerUser);

        assertEquals("Payment PAY-1000", res.getItemDescription());
        assertEquals(BigDecimal.valueOf(810000.00), res.getOriginalPrice());
        assertEquals(BigDecimal.ZERO, res.getDiscount());
    }

    @Test
    void updatePaymentMethod_AsOwner_WhenPending_UpdatesMethod() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        UpdatePaymentMethodRequest req = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.MOMO)
                .build();

        PaymentResponse res = paymentService.updatePaymentMethod(10L, req, ownerUser);

        assertEquals(PaymentMethod.MOMO, res.getMethod());
        assertEquals(PaymentMethod.MOMO, pendingPayment.getPaymentMethod());
    }

    @Test
    void updatePaymentMethod_AsNonOwner_ThrowsAccessDeniedException() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        UpdatePaymentMethodRequest req = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .build();

        assertThrows(AccessDeniedException.class,
                () -> paymentService.updatePaymentMethod(10L, req, otherUser));
    }

    @Test
    void updatePaymentMethod_WhenNotPending_ThrowsBusinessRuleException() {
        Payment successPayment = Payment.builder()
                .id(11L)
                .member(ownerProfile)
                .paymentStatus(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(500000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .build();

        when(paymentRepository.findById(11L)).thenReturn(Optional.of(successPayment));

        UpdatePaymentMethodRequest req = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> paymentService.updatePaymentMethod(11L, req, ownerUser));
        assertEquals("Only PENDING payments can have their method changed", ex.getMessage());
    }

    @Test
    void updatePaymentMethod_NotFound_ThrowsResourceNotFoundException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        UpdatePaymentMethodRequest req = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.updatePaymentMethod(999L, req, ownerUser));
    }
}
