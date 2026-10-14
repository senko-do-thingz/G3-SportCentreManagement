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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private BankTransferProperties bankProperties;
    @Mock
    private CodeFormatter codeFormatter;

    private Clock clock;
    private PaymentServiceImpl paymentService;

    private UserAccount ownerUser;
    private UserAccount otherUser;
    private UserAccount receptionistUser;
    private UserAccount managerUser;
    private MemberProfile ownerProfile;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("UTC"));
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                memberCardRepository,
                sportPackageRegistrationRepository,
                new PaymentItemResolver(memberCardRepository, sportPackageRegistrationRepository, invoiceRepository),
                bankProperties,
                codeFormatter,
                clock
        );

        Role memberRole = Role.builder().id(1L).code("MEMBER").name("Member").build();
        Role recRole = Role.builder().id(2L).code("RECEPTIONIST").name("Receptionist").build();
        Role mgrRole = Role.builder().id(3L).code("MANAGER").name("Manager").build();

        ownerUser = UserAccount.builder().id(100L).email("owner@example.com").fullName("Owner Member").role(memberRole).build();
        otherUser = UserAccount.builder().id(200L).email("other@example.com").fullName("Other Member").role(memberRole).build();
        receptionistUser = UserAccount.builder().id(300L).email("rec@sportify.com").fullName("Desk Staff").role(recRole).build();
        managerUser = UserAccount.builder().id(400L).email("mgr@sportify.com").fullName("Center Mgr").role(mgrRole).build();

        ownerProfile = MemberProfile.builder().id(100L).userAccount(ownerUser).memberCode("MEM-0100").build();

        pendingPayment = Payment.builder()
                .id(10L)
                .paymentCode("PAY-1000")
                .member(ownerProfile)
                .amount(BigDecimal.valueOf(810000.00))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentTime(null)
                .notes("Existing payment notes")
                .createdAt(LocalDateTime.of(2026, 10, 12, 10, 0, 0))
                .updatedAt(LocalDateTime.of(2026, 10, 12, 10, 0, 0))
                .build();

        lenient().when(bankProperties.getAccountName()).thenReturn("Sportify Sports Center");
        lenient().when(bankProperties.getAccountNumber()).thenReturn("0000 1041 2460");
        lenient().when(bankProperties.getBank()).thenReturn("Demo Bank");
    }

    @Test
    @DisplayName("Create pending payment with valid online method succeeds and initializes pending status")
    void createPendingPayment_ValidOnlineMethod_Succeeds() {
        when(paymentRepository.getNextPaymentCodeSequence()).thenReturn(1001L);
        when(codeFormatter.formatPaymentCode(1001L)).thenReturn("PAY-1001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(300000.00), PaymentMethod.MOMO);

        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentCode()).isEqualTo("PAY-1001");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.MOMO);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getPaymentTime()).isNull();
    }

    @Test
    @DisplayName("Clock consistency: markPaymentSuccess sets paymentTime from Clock bean")
    void timeSource_PaymentTime_UsesClock() {
        when(paymentRepository.getNextPaymentCodeSequence()).thenReturn(1002L);
        when(codeFormatter.formatPaymentCode(1002L)).thenReturn("PAY-1002");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(1002L);
            }
            return p;
        });

        Payment payment = paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.BANK_TRANSFER);
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        Payment successfulPayment = paymentService.markPaymentSuccess(payment.getId(), receptionistUser);

        assertThat(successfulPayment.getPaymentTime()).isEqualTo(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("Create pending payment with real CodeFormatter generates formatted paymentCode")
    void createPendingPayment_WithRealCodeFormatter_GeneratesFormattedCode() {
        CodeFormatter realFormatter = new CodeFormatter();
        PaymentServiceImpl serviceWithRealFormatter = new PaymentServiceImpl(
                paymentRepository,
                memberCardRepository,
                sportPackageRegistrationRepository,
                new PaymentItemResolver(memberCardRepository, sportPackageRegistrationRepository, invoiceRepository),
                bankProperties,
                realFormatter,
                clock
        );

        when(paymentRepository.getNextPaymentCodeSequence()).thenReturn(1000L);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment payment = serviceWithRealFormatter.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.BANK_TRANSFER);

        assertThat(payment.getPaymentCode()).isEqualTo("PAY-1000");
    }

    @Test
    @DisplayName("Create pending payment with disallowed online methods throws BusinessRuleException")
    void createPendingPayment_DisallowedOnlineMethod_ThrowsBusinessRuleException() {
        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.CASH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed for online requests");

        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(100000.00), PaymentMethod.CREDIT_CARD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not allowed for online requests");
    }

    @Test
    @DisplayName("Create pending payment with zero or negative amount throws BusinessRuleException")
    void createPendingPayment_ZeroOrNegativeAmount_ThrowsBusinessRuleException() {
        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.ZERO, PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, BigDecimal.valueOf(-50000.00), PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("Amount rounding down to zero is rejected (TODO-BE17-DISCOUNT)")
    void createPendingPayment_AmountRoundsToZero_ThrowsBusinessRuleException() {
        BigDecimal roundedZero = BigDecimal.valueOf(0.004).setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(roundedZero).isEqualByComparingTo(BigDecimal.ZERO);

        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, roundedZero, PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("Payment amount with more than 2 decimal places throws BusinessRuleException")
    void createPendingPayment_MoreThanTwoDecimalPlaces_ThrowsBusinessRuleException() {
        BigDecimal invalidScale = new BigDecimal("100.555");
        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, invalidScale, PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount cannot have more than 2 decimal places");
    }

    @Test
    @DisplayName("Payment amount with more than 10 integer digits throws BusinessRuleException")
    void createPendingPayment_MoreThanTenIntegerDigits_ThrowsBusinessRuleException() {
        BigDecimal invalidPrecision = new BigDecimal("10000000000.00");
        assertThatThrownBy(() -> paymentService.createPendingPayment(ownerProfile, invalidPrecision, PaymentMethod.BANK_TRANSFER))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Payment amount cannot have more than 10 integer digits");
    }

    @Test
    @DisplayName("Mark payment success with null id throws BusinessRuleException")
    void markPaymentSuccess_NullPaymentId_ThrowsBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () -> paymentService.markPaymentSuccess(null, receptionistUser));
    }

    @Test
    @DisplayName("Mark payment success when status is not PENDING throws BusinessRuleException")
    void markPaymentSuccess_NotPendingStatus_ThrowsBusinessRuleException() {
        pendingPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.markPaymentSuccess(10L, receptionistUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot mark payment as success: payment is in status SUCCESS, expected PENDING");
    }

    @Test
    @DisplayName("Mark payment success updates status, paymentTime, confirmedByUser, and appends user id note")
    void markPaymentSuccess_UpdatesStatusAndNotes() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment success = paymentService.markPaymentSuccess(10L, receptionistUser);

        assertThat(success.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(success.getPaymentTime()).isEqualTo(LocalDateTime.now(clock));
        assertThat(success.getConfirmedByUser()).isEqualTo(receptionistUser);
        assertThat(success.getNotes()).contains("Existing payment notes | Confirmed by user id 300");
    }

    @Test
    @DisplayName("Mark payment success with staff specifying CASH updates payment method")
    void markPaymentSuccess_WithStaffAndCashMethod_UpdatesPaymentMethodToCash() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment success = paymentService.markPaymentSuccess(10L, receptionistUser, PaymentMethod.CASH);

        assertThat(success.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(success.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(success.getConfirmedByUser()).isEqualTo(receptionistUser);
        assertThat(success.getNotes()).contains("Confirmed by user id 300");
    }

    @Test
    @DisplayName("Mark payment success with non-staff confirmedBy throws BusinessRuleException before repository lookup or mutation")
    void markPaymentSuccess_WithNonStaffConfirmedBy_ThrowsBusinessRuleException() {
        assertThatThrownBy(() -> paymentService.markPaymentSuccess(10L, ownerUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only staff users can confirm payment");

        verify(paymentRepository, never()).findById(any());
        verify(paymentRepository, never()).save(any());
        assertThat(pendingPayment.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(pendingPayment.getPaymentTime()).isNull();
        assertThat(pendingPayment.getConfirmedByUser()).isNull();
    }

    @Test
    @DisplayName("Mark payment success with null confirmedBy succeeds and leaves confirmedByUser null")
    void markPaymentSuccess_WithNullConfirmedBy_SucceedsWithoutStaffNote() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment success = paymentService.markPaymentSuccess(10L, null);

        assertThat(success.getPaymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(success.getConfirmedByUser()).isNull();
        assertThat(success.getNotes()).isEqualTo("Existing payment notes");
    }

    @Test
    @DisplayName("Mark payment failed with null id throws BusinessRuleException")
    void markPaymentFailed_NullPaymentId_ThrowsBusinessRuleException() {
        assertThrows(BusinessRuleException.class, () -> paymentService.markPaymentFailed(null, "some reason"));
    }

    @Test
    @DisplayName("Mark payment failed when status is not PENDING throws BusinessRuleException")
    void markPaymentFailed_NotPendingStatus_ThrowsBusinessRuleException() {
        pendingPayment.setPaymentStatus(PaymentStatus.SUCCESS);
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        assertThatThrownBy(() -> paymentService.markPaymentFailed(10L, "some reason"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot mark payment as failed: payment is in status SUCCESS, expected PENDING");
    }

    @Test
    @DisplayName("Mark payment failed sets FAILED status and appends reason to notes")
    void markPaymentFailed_UpdatesStatusAndAppendsNotes() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment failed = paymentService.markPaymentFailed(10L, "Payment cancelled by member user id 100");

        assertThat(failed.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getNotes()).isEqualTo("Existing payment notes | Payment cancelled by member user id 100");
    }

    @Test
    @DisplayName("truncateNotes does not split surrogate pairs")
    void truncateNotes_DoesNotSplitSurrogatePairs() {
        // Build a string of 499 ASCII characters followed by a 2-char surrogate pair (\uD83D\uDE00)
        String prefix = "A".repeat(499);
        String surrogatePair = "\uD83D\uDE00";
        String longNote = prefix + surrogatePair;

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        pendingPayment.setNotes(null);

        Payment failed = paymentService.markPaymentFailed(10L, longNote);

        // Character at index 499 would be \uD83D (high surrogate). Truncation must back up to 499 chars.
        assertThat(failed.getNotes().length()).isEqualTo(499);
        assertThat(failed.getNotes()).isEqualTo(prefix);
    }

    @Test
    @DisplayName("appendNote with existing 495 characters preserves staff note intact within 500 characters")
    void appendNote_Existing495Chars_PreservesStaffNoteIntact() {
        String existing495 = "E".repeat(495);
        pendingPayment.setNotes(existing495);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Payment success = paymentService.markPaymentSuccess(10L, receptionistUser);

        String staffNote = "Confirmed by user id " + receptionistUser.getId();
        assertThat(success.getNotes()).isNotNull();
        assertThat(success.getNotes().length()).isLessThanOrEqualTo(500);
        assertThat(success.getNotes()).endsWith(" | " + staffNote);
    }

    @Test
    @DisplayName("appendNote with single 600 character note is cut to 500 without splitting surrogate pair")
    void appendNote_Single600CharsWithSurrogatePair_CutSafelyTo500() {
        String prefix = "B".repeat(499);
        String surrogatePair = "\uD83D\uDE00";
        String suffix = "C".repeat(99);
        String note600 = prefix + surrogatePair + suffix;

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        pendingPayment.setNotes(null);

        Payment failed = paymentService.markPaymentFailed(10L, note600);

        assertThat(failed.getNotes()).isNotNull();
        assertThat(failed.getNotes().length()).isEqualTo(499);
        assertThat(failed.getNotes()).isEqualTo(prefix);
    }

    @Test
    @DisplayName("appendNote handles null and blank inputs cleanly")
    void appendNote_NullAndBlankInputs_HandledCleanly() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        pendingPayment.setNotes("Initial note");
        Payment resNullAppend = paymentService.markPaymentSuccess(10L, null);
        assertThat(resNullAppend.getNotes()).isEqualTo("Initial note");

        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        Payment resBlankAppend = paymentService.markPaymentFailed(10L, "   ");
        assertThat(resBlankAppend.getNotes()).isEqualTo("Initial note");

        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        pendingPayment.setNotes(null);
        Payment resNullExisting = paymentService.markPaymentFailed(10L, "New reason");
        assertThat(resNullExisting.getNotes()).isEqualTo("New reason");

        pendingPayment.setPaymentStatus(PaymentStatus.PENDING);
        pendingPayment.setNotes(null);
        Payment resBothNullOrBlank = paymentService.markPaymentFailed(10L, "   ");
        assertThat(resBothNullOrBlank.getNotes()).isNull();
    }

    @Test
    @DisplayName("appendNote when trimmed existing segment becomes empty due to low surrogate boundary returns append without separator")
    void appendNote_TrimmedExistingBecomesEmptyOnLowSurrogate_ReturnsAppendWithoutLeadingSeparator() {
        String existing = "\uD83D\uDE00";
        String append496 = "A".repeat(496);

        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pendingPayment.setNotes(existing);
        Payment failed = paymentService.markPaymentFailed(10L, append496);

        assertThat(failed.getNotes()).isEqualTo(append496);
    }

    @Test
    @DisplayName("getPayment and updatePaymentMethod with null actor throws IllegalArgumentException")
    void paymentMethods_NullActor_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.getPayment(10L, null));
        assertThrows(IllegalArgumentException.class, () -> paymentService.updatePaymentMethod(10L, mock(UpdatePaymentMethodRequest.class), null));
    }

    @Test
    @DisplayName("GET payment by owner returns PaymentResponse with notes concealed")
    void getPayment_Owner_ReturnsResponseWithNotesConcealed() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        PaymentResponse response = paymentService.getPayment(10L, ownerUser);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("PAY-1000");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getNotes()).isNull();
    }

    @Test
    @DisplayName("GET payment by staff returns PaymentResponse with notes visible")
    void getPayment_Staff_ReturnsResponseWithNotesVisible() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));

        PaymentResponse recResponse = paymentService.getPayment(10L, receptionistUser);
        PaymentResponse mgrResponse = paymentService.getPayment(10L, managerUser);

        assertThat(recResponse).isNotNull();
        assertThat(recResponse.getNotes()).isEqualTo("Existing payment notes");
        assertThat(mgrResponse).isNotNull();
        assertThat(mgrResponse.getNotes()).isEqualTo("Existing payment notes");
    }

    @Test
    @DisplayName("GET payment by other non-owner member throws 404 ResourceNotFoundException")
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
        assertThat(response.getNotes()).isNull();
        verify(paymentRepository).save(pendingPayment);
    }

    @Test
    @DisplayName("PUT payment method by staff allows setting CASH")
    void updatePaymentMethod_StaffWithCash_Succeeds() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePaymentMethodRequest request = UpdatePaymentMethodRequest.builder()
                .paymentMethod(PaymentMethod.CASH)
                .build();

        PaymentResponse response = paymentService.updatePaymentMethod(10L, request, receptionistUser);

        assertThat(response.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(response.getNotes()).isEqualTo("Existing payment notes");
        verify(paymentRepository).save(pendingPayment);
    }

    @Test
    @DisplayName("PUT payment method by non-owner member throws 404 ResourceNotFoundException")
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
    @DisplayName("PUT payment method by member with cash or credit card throws BusinessRuleException")
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

    @Test
    @DisplayName("Dynamic item resolution resolves invoice picking lowest id deterministically when multiple exist")
    void getPayment_ResolvesInvoiceDetails_PicksLowestIdDeterministically() {
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(pendingPayment));
        when(sportPackageRegistrationRepository.findByPaymentId(10L)).thenReturn(Optional.empty());
        when(memberCardRepository.findByPaymentId(10L)).thenReturn(Optional.empty());

        Invoice higherIdInvoice = Invoice.builder()
                .id(99L)
                .invoiceNumber("INV-0099")
                .subtotalAmount(BigDecimal.valueOf(990000.00))
                .discountAmount(BigDecimal.valueOf(99000.00))
                .build();
        Invoice lowerIdInvoice = Invoice.builder()
                .id(42L)
                .invoiceNumber("INV-0042")
                .subtotalAmount(BigDecimal.valueOf(420000.00))
                .discountAmount(BigDecimal.valueOf(42000.00))
                .build();

        when(invoiceRepository.findByPaymentId(10L)).thenReturn(List.of(higherIdInvoice, lowerIdInvoice));

        PaymentResponse response = paymentService.getPayment(10L, ownerUser);

        assertThat(response.getItemDescription()).isEqualTo("Invoice INV-0042");
        assertThat(response.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(420000.00));
        assertThat(response.getDiscount()).isEqualByComparingTo(BigDecimal.valueOf(42000.00));
    }

    @Test
    @DisplayName("lockPayment reads the payment with the row lock query")
    void lockPayment_Found_ReturnsLockedPayment() {
        when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingPayment));

        Payment locked = paymentService.lockPayment(10L);

        assertThat(locked).isSameAs(pendingPayment);
        verify(paymentRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("lockPayment on a missing payment throws ResourceNotFoundException")
    void lockPayment_NotFound_Throws() {
        when(paymentRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.lockPayment(404L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
