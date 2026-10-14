package com.sportify.payment.service.impl;

import com.sportify.catalog.dto.PageResponse;
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
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.FailPaymentRequest;
import com.sportify.payment.dto.InvoiceResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentVerificationServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private MemberCardRepository memberCardRepository;
    @Mock
    private SportPackageRegistrationRepository registrationRepository;
    @Mock
    private MembershipCardService membershipCardService;
    @Mock
    private SportPackageService sportPackageService;
    @Mock
    private AuditService auditService;

    private PaymentVerificationServiceImpl service;
    private UserAccount receptionist;
    private UserAccount memberUser;
    private MemberProfile member;
    private Payment payment;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-14T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        PaymentItemResolver resolver = new PaymentItemResolver(memberCardRepository, registrationRepository, invoiceRepository);
        service = new PaymentVerificationServiceImpl(paymentRepository, invoiceRepository, resolver,
                membershipCardService, sportPackageService, auditService, new CodeFormatter(), clock);

        receptionist = UserAccount.builder().id(7L).fullName("Rita Reception")
                .role(Role.builder().code("RECEPTIONIST").name("Receptionist").build()).build();
        memberUser = UserAccount.builder().id(100L).fullName("Minh Member")
                .role(Role.builder().code("MEMBER").name("Member").build()).build();
        member = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-0100").build();
        payment = Payment.builder()
                .id(1L)
                .paymentCode("PAY-1001")
                .member(member)
                .amount(new BigDecimal("380000.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .version(0)
                .build();
    }

    private SportPackageRegistration pendingRegistration() {
        SportPackage pkg = SportPackage.builder().id(5L).name("Swimming 30 Days").durationDays(30).build();
        return SportPackageRegistration.builder()
                .id(20L)
                .registrationCode("REG-PKG-1020")
                .member(member)
                .sportPackage(pkg)
                .originalPrice(new BigDecimal("400000.00"))
                .discountPercentage(5)
                .paidAmount(new BigDecimal("380000.00"))
                .status(PackageRegistrationStatus.PENDING_PAYMENT)
                .payment(payment)
                .build();
    }

    private MemberCard pendingCard(BigDecimal pricePaid) {
        MembershipCardTier tier = MembershipCardTier.builder().id(3L).code("VIP").name("VIP Member")
                .price(new BigDecimal("2000000.00")).durationMonths(12).discountPercentage(10).build();
        return MemberCard.builder()
                .id(30L)
                .cardCode("CARD-1030")
                .member(member)
                .tier(tier)
                .status(CardStatus.PENDING_PAYMENT)
                .pricePaid(pricePaid)
                .payment(payment)
                .build();
    }

    private ConfirmPaymentRequest confirmRequest(PaymentMethod method, String reference, String amount) {
        return ConfirmPaymentRequest.builder()
                .method(method)
                .referenceCode(reference)
                .amountReceived(new BigDecimal(amount))
                .note("Checked bank app")
                .build();
    }

    /** The real activation marks the payment SUCCESS through PaymentService; the mocks do the same. */
    private void markSuccessOnActivation() {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentTime(LocalDateTime.of(2026, 10, 14, 10, 0));
        payment.setConfirmedByUser(receptionist);
    }

    private void stubLockedPayment() {
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(payment));
    }

    private void stubInvoiceSave() {
        when(invoiceRepository.findByPaymentId(1L)).thenReturn(List.of());
        when(invoiceRepository.getNextInvoiceNumberSequence()).thenReturn(1001L);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> {
            Invoice invoice = inv.getArgument(0);
            invoice.setId(55L);
            return invoice;
        });
    }

    @Test
    @DisplayName("Confirm with the exact amount: locks the payment, activates the registration, one PAID invoice and one PAYMENT_CONFIRMED")
    void confirm_ExactAmount_Registration_ActivatesAndIssuesInvoice() {
        SportPackageRegistration reg = pendingRegistration();
        stubLockedPayment();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.of(reg));
        when(sportPackageService.activateRegistrationForPaymentConfirmation(eq(20L), eq(receptionist), eq(PaymentMethod.BANK_TRANSFER)))
                .thenAnswer(inv -> {
                    markSuccessOnActivation();
                    reg.setStatus(PackageRegistrationStatus.ACTIVE);
                    return null;
                });
        stubInvoiceSave();

        StaffPaymentResponse response = service.confirmPayment(1L,
                confirmRequest(PaymentMethod.BANK_TRANSFER, "  FT2610140001 ", "380000"), receptionist);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice invoice = invoiceCaptor.getValue();
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-1001");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo("400000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("20000");
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("380000");
        assertThat(invoice.getMember()).isSameAs(member);
        assertThat(invoice.getLines()).hasSize(1);
        InvoiceLine line = invoice.getLines().get(0);
        assertThat(line.getItemType()).isEqualTo(InvoiceItemType.SPORT_PACKAGE);
        assertThat(line.getItemReferenceId()).isEqualTo(20L);
        assertThat(line.getDescription()).isEqualTo("Swimming 30 Days (REG-PKG-1020)");
        assertThat(line.getQuantity()).isEqualTo(1);
        assertThat(line.getUnitPrice()).isEqualByComparingTo("400000");
        assertThat(line.getLineTotal()).isEqualByComparingTo("400000");

        assertThat(payment.getReferenceCode()).isEqualTo("FT2610140001");
        assertThat(payment.getNotes()).contains("Front desk note: Checked bank app");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-1001");
        assertThat(response.getInvoiceId()).isEqualTo(55L);
        assertThat(response.getItemType()).isEqualTo(InvoiceItemType.SPORT_PACKAGE);
        assertThat(response.getItemStatus()).isEqualTo("ACTIVE");
        assertThat(response.getPaymentReference()).isEqualTo("FT2610140001");
        verify(paymentRepository).findByIdForUpdate(1L);
        verify(membershipCardService, never()).activateCardForPaymentConfirmation(anyLong(), any(), any());
        verify(sportPackageService, never()).activateRegistration(anyLong(), any(), any());
        verify(paymentRepository).flush();

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.PAYMENT_CONFIRMED);
        assertThat(auditCaptor.getValue().getEntityCode()).isEqualTo("PAY-1001");
        assertThat(auditCaptor.getValue().getDetails())
                .containsEntry("invoiceNumber", "INV-1001")
                .containsEntry("referenceCode", "FT2610140001")
                .containsEntry("note", "Checked bank app")
                .doesNotContainKey("amountReceived");
    }

    @Test
    @DisplayName("Confirm a card upgrade: line at the tier price, the upgrade credit as the invoice discount")
    void confirm_ExactAmount_CardUpgrade_InvoiceShowsCredit() {
        payment.setAmount(new BigDecimal("1500000.00"));
        MemberCard card = pendingCard(new BigDecimal("1500000.00"));
        stubLockedPayment();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.of(card));
        when(membershipCardService.activateCardForPaymentConfirmation(eq(30L), eq(receptionist), eq(PaymentMethod.CASH)))
                .thenAnswer(inv -> {
                    markSuccessOnActivation();
                    card.setStatus(CardStatus.ACTIVE);
                    return null;
                });
        stubInvoiceSave();

        StaffPaymentResponse response = service.confirmPayment(1L,
                confirmRequest(PaymentMethod.CASH, null, "1500000"), receptionist);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice invoice = invoiceCaptor.getValue();
        assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo("2000000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("500000");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1500000");
        assertThat(invoice.getLines().get(0).getItemType()).isEqualTo(InvoiceItemType.MEMBERSHIP_CARD);
        assertThat(invoice.getLines().get(0).getDescription())
                .isEqualTo("VIP Member Card (CARD-1030) - upgrade, credit for the current card applied");
        assertThat(payment.getReferenceCode()).isNull();
        assertThat(response.getItemCode()).isEqualTo("CARD-1030");
        assertThat(response.getOriginalPrice()).isEqualByComparingTo("2000000");
        assertThat(response.getDiscount()).isEqualByComparingTo("500000");
        verify(sportPackageService, never()).activateRegistrationForPaymentConfirmation(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Wrong amount: 409 PAYMENT_AMOUNT_MISMATCH, note and PAYMENT_AMOUNT_MISMATCH log, nothing else changes")
    void confirm_AmountMismatch_KeepsPendingAndRecordsTrace() {
        payment.setNotes("Member says paid");
        stubLockedPayment();

        assertThatThrownBy(() -> service.confirmPayment(1L,
                confirmRequest(PaymentMethod.BANK_TRANSFER, "FT2610140001", "350000"), receptionist))
                .isInstanceOf(PaymentAmountMismatchException.class)
                .hasMessageContaining("350000")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_AMOUNT_MISMATCH"));

        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentTime()).isNull();
        assertThat(payment.getReferenceCode()).isNull();
        assertThat(payment.getNotes()).startsWith("Member says paid | Amount mismatch: received 350000, expected 380000.00");
        assertThat(payment.getNotes()).contains("Note: Checked bank app");
        verify(paymentRepository).flush();

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        AuditEvent event = auditCaptor.getValue();
        assertThat(event.getAction()).isEqualTo(AuditAction.PAYMENT_AMOUNT_MISMATCH);
        assertThat(event.isRequiresNew()).isFalse();
        assertThat(event.getDetails())
                .containsEntry("amount", new BigDecimal("380000.00"))
                .containsEntry("amountReceived", new BigDecimal("350000"))
                .containsEntry("method", "BANK_TRANSFER")
                .containsEntry("referenceCode", "FT2610140001")
                .containsEntry("note", "Checked bank app");
        verifyNoInteractions(membershipCardService, sportPackageService, registrationRepository, memberCardRepository);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("Confirm a payment that is not PENDING returns 409 PAYMENT_NOT_PENDING")
    void confirm_NotPending_Conflict() {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        stubLockedPayment();

        assertThatThrownBy(() -> service.confirmPayment(1L,
                confirmRequest(PaymentMethod.BANK_TRANSFER, "FT1", "380000"), receptionist))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_NOT_PENDING"));
        verifyNoInteractions(membershipCardService, sportPackageService, auditService);
    }

    @Test
    @DisplayName("Bank transfer without a reference is rejected before the payment is read")
    void confirm_BankTransferWithoutReference_BadRequest() {
        assertThatThrownBy(() -> service.confirmPayment(1L,
                confirmRequest(PaymentMethod.BANK_TRANSFER, "   ", "380000"), receptionist))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Reference code is required");
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Cash needs no reference")
    void confirm_CashWithoutReference_PassesReferenceCheck() {
        when(paymentRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmPayment(1L,
                confirmRequest(PaymentMethod.CASH, null, "380000"), receptionist))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("A payment linked to no card and no registration cannot be confirmed")
    void confirm_NoLinkedItem_BadRequest() {
        stubLockedPayment();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmPayment(1L,
                confirmRequest(PaymentMethod.BANK_TRANSFER, "FT1", "380000"), receptionist))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not linked");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("A member actor reaching confirm gets AccessDeniedException (403)")
    void confirm_MemberActor_Refused() {
        assertThatThrownBy(() -> service.confirmPayment(1L,
                confirmRequest(PaymentMethod.CASH, null, "380000"), memberUser))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("A member actor reaching fail gets AccessDeniedException (403)")
    void fail_MemberActor_Refused() {
        assertThatThrownBy(() -> service.failPayment(1L,
                FailPaymentRequest.builder().reason("No transfer found").build(), memberUser))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Fail cancels the linked card request, records the reason and writes the activity log")
    void fail_PendingCard_CancelsCardAndRecordsReason() {
        MemberCard card = pendingCard(new BigDecimal("2000000.00"));
        stubLockedPayment();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.of(card));
        when(membershipCardService.cancelCard(30L, receptionist)).thenAnswer(inv -> {
            card.setStatus(CardStatus.CANCELLED);
            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setNotes("Payment cancelled due to card cancellation by staff user id 7");
            return null;
        });

        StaffPaymentResponse response = service.failPayment(1L,
                FailPaymentRequest.builder().reason("No transfer found").build(), receptionist);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getItemStatus()).isEqualTo("CANCELLED");
        assertThat(payment.getNotes()).endsWith("Payment failed, checked by user id 7: No transfer found");
        verify(paymentRepository).flush();
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo(AuditAction.PAYMENT_FAILED);
        assertThat(auditCaptor.getValue().getDetails()).containsEntry("reason", "No transfer found");
    }

    @Test
    @DisplayName("Fail on a registration passes the reason to cancelRegistration so it lands in cancel_reason")
    void fail_PendingRegistration_PassesReasonToCancel() {
        SportPackageRegistration reg = pendingRegistration();
        stubLockedPayment();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.of(reg));
        when(sportPackageService.cancelRegistration(20L, receptionist, "No transfer found")).thenAnswer(inv -> {
            reg.setStatus(PackageRegistrationStatus.CANCELLED);
            reg.setCancelReason("No transfer found");
            payment.setPaymentStatus(PaymentStatus.FAILED);
            return null;
        });

        StaffPaymentResponse response = service.failPayment(1L,
                FailPaymentRequest.builder().reason("  No transfer found ").build(), receptionist);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(sportPackageService).cancelRegistration(20L, receptionist, "No transfer found");
        verify(sportPackageService, never()).cancelRegistration(anyLong(), any());
    }

    @Test
    @DisplayName("Fail on a payment that is not PENDING returns 409 PAYMENT_NOT_PENDING")
    void fail_NotPending_Conflict() {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        stubLockedPayment();

        assertThatThrownBy(() -> service.failPayment(1L,
                FailPaymentRequest.builder().reason("Duplicate").build(), receptionist))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_NOT_PENDING"));
        verifyNoInteractions(membershipCardService, sportPackageService, auditService);
    }

    @Test
    @DisplayName("An optimistic lock failure on flush answers 409 PAYMENT_CONCURRENT_UPDATE")
    void fail_OptimisticLockOnFlush_ConcurrentUpdateCode() {
        stubLockedPayment();
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        doThrow(new ObjectOptimisticLockingFailureException(Payment.class, 1L)).when(paymentRepository).flush();

        assertThatThrownBy(() -> service.failPayment(1L,
                FailPaymentRequest.builder().reason("Duplicate").build(), receptionist))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_CONCURRENT_UPDATE"));
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("recordPaymentConfirmed refuses a second invoice with 409 INVOICE_ALREADY_ISSUED")
    void recordPaymentConfirmed_InvoiceAlreadyIssued_Conflict() {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.of(pendingRegistration()));
        when(invoiceRepository.findByPaymentId(1L)).thenReturn(List.of(Invoice.builder().id(9L).build()));

        assertThatThrownBy(() -> service.recordPaymentConfirmed(1L, receptionist, null))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("INVOICE_ALREADY_ISSUED"));
        verify(invoiceRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("recordPaymentConfirmed refuses a payment that is not SUCCESS")
    void recordPaymentConfirmed_NotSuccess_IllegalState() {
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.recordPaymentConfirmed(1L, receptionist, null))
                .isInstanceOf(IllegalStateException.class);
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("List maps member, item and invoice of each row")
    @SuppressWarnings("unchecked")
    void list_MapsRows() {
        SportPackageRegistration reg = pendingRegistration();
        when(paymentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payment), PageRequest.of(0, 10), 1));
        when(registrationRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of(reg));
        when(memberCardRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());
        when(invoiceRepository.findByPaymentIdIn(anyCollection())).thenReturn(List.of());

        PageResponse<StaffPaymentResponse> page = service.listPayments(PaymentStatus.PENDING, null, "minh",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 14), 0, 10);

        assertThat(page.totalElements()).isEqualTo(1);
        StaffPaymentResponse row = page.content().get(0);
        assertThat(row.getMemberFullName()).isEqualTo("Minh Member");
        assertThat(row.getMemberCode()).isEqualTo("MEM-0100");
        assertThat(row.getItemType()).isEqualTo(InvoiceItemType.SPORT_PACKAGE);
        assertThat(row.getItemCode()).isEqualTo("REG-PKG-1020");
        assertThat(row.getItemDescription()).isEqualTo("Swimming 30 Days");
        assertThat(row.getOriginalPrice()).isEqualByComparingTo("400000");
        assertThat(row.getDiscount()).isEqualByComparingTo("20000");
        assertThat(row.getInvoiceNumber()).isNull();
    }

    @Test
    @DisplayName("List rejects a page size above the limit and a from date after the to date")
    void list_InvalidParameters_BadRequest() {
        assertThatThrownBy(() -> service.listPayments(null, null, null, null, null, 0, 101))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.listPayments(null, null, null,
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 1), 0, 10))
                .isInstanceOf(BusinessRuleException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("Invoice by payment: staff read it, another member gets 404")
    void invoiceByPayment_Visibility() {
        Invoice invoice = Invoice.builder().id(55L).invoiceNumber("INV-1001").payment(payment).member(member)
                .subtotalAmount(new BigDecimal("400000")).discountAmount(new BigDecimal("20000"))
                .taxAmount(BigDecimal.ZERO).totalAmount(new BigDecimal("380000")).status(InvoiceStatus.PAID).build();
        invoice.addLine(InvoiceLine.builder().id(1L).itemType(InvoiceItemType.SPORT_PACKAGE).itemReferenceId(20L)
                .description("Swimming 30 Days (REG-PKG-1020)").quantity(1)
                .unitPrice(new BigDecimal("400000")).lineTotal(new BigDecimal("400000")).build());
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(invoiceRepository.findByPaymentId(1L)).thenReturn(List.of(invoice));

        InvoiceResponse response = service.getInvoiceByPayment(1L, receptionist);
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-1001");
        assertThat(response.getMemberFullName()).isEqualTo("Minh Member");
        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("380000");

        UserAccount otherMember = UserAccount.builder().id(999L)
                .role(Role.builder().code("MEMBER").name("Member").build()).build();
        assertThatThrownBy(() -> service.getInvoiceByPayment(1L, otherMember))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Stored item price differs from the payment amount: 409 PAYMENT_ITEM_AMOUNT_MISMATCH, no invoice saved")
    void recordPaymentConfirmed_ItemPriceDiffersFromPayment_ConflictAndNoInvoice() {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("390000.00"));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.of(pendingRegistration()));
        when(invoiceRepository.findByPaymentId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.recordPaymentConfirmed(1L, receptionist, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("does not equal the payment amount 390000.00")
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_ITEM_AMOUNT_MISMATCH"));
        verify(invoiceRepository, never()).save(any());
        verify(invoiceRepository, never()).getNextInvoiceNumberSequence();
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("Tier price lowered after the request: confirm succeeds, invoice at price_paid with no discount")
    void confirm_TierPriceLoweredAfterRequest_InvoiceAtPricePaid() {
        payment.setAmount(new BigDecimal("1200000.00"));
        MemberCard card = pendingCard(new BigDecimal("1200000.00"));
        card.getTier().setPrice(new BigDecimal("1000000.00"));
        stubLockedPayment();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findByPaymentId(1L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(1L)).thenReturn(Optional.of(card));
        when(membershipCardService.activateCardForPaymentConfirmation(eq(30L), eq(receptionist), eq(PaymentMethod.CASH)))
                .thenAnswer(inv -> {
                    markSuccessOnActivation();
                    card.setStatus(CardStatus.ACTIVE);
                    return null;
                });
        stubInvoiceSave();

        StaffPaymentResponse response = service.confirmPayment(1L,
                confirmRequest(PaymentMethod.CASH, null, "1200000"), receptionist);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());
        Invoice invoice = invoiceCaptor.getValue();
        assertThat(invoice.getSubtotalAmount()).isEqualByComparingTo("1200000");
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1200000");
        assertThat(invoice.getLines().get(0).getDescription()).isEqualTo("VIP Member Card (CARD-1030)");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getOriginalPrice()).isEqualByComparingTo("1200000");
        assertThat(response.getDiscount()).isEqualByComparingTo("0");
    }
}
