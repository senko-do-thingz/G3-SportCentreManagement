package com.sportify.catalog.service;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundQuoteResponse;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.BookingRepository.RefundableBookingRow;
import com.sportify.catalog.repository.ClassSessionRepository;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.RefundRequestRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.RefundServiceImpl;
import com.sportify.core.audit.AuditAction;
import com.sportify.core.audit.AuditEvent;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceLineRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BE-21: refunds linked to payments. Quote, submit rules (409 on a second refund, cap at the payment amount),
 * review cap and completion side effects. Review round 1: session lock order and atomic seat release (P1-02),
 * payment-to-registration checks (P1-03) and the set-based upcoming booking query (P2-01).
 */
@ExtendWith(MockitoExtension.class)
class RefundPaymentLinkTest {

    /** 2026-10-15 10:00 in Ho Chi Minh City. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);
    private static final String CANCEL_REASON = "Refund completed: RFD-0001";

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private SportPackageRegistrationRepository registrationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentRefundService paymentRefundService;
    @Mock private BookingRepository bookingRepository;
    @Mock private ClassSessionRepository sessionRepository;
    @Mock private MemberCardRepository memberCardRepository;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private AuditService auditService;
    @Mock private CodeFormatter codeFormatter;

    private RefundServiceImpl service;
    private UserAccount memberUser;
    private UserAccount receptionist;
    private UserAccount manager;
    private MemberProfile member;
    private Payment payment;
    private SportPackageRegistration registration;

    @BeforeEach
    void setUp() {
        service = new RefundServiceImpl(refundRequestRepository, registrationRepository, paymentRepository,
                paymentRefundService, bookingRepository, sessionRepository, memberCardRepository, invoiceLineRepository,
                auditService, codeFormatter, CLOCK, org.mockito.Mockito.mock(com.sportify.payment.service.impl.CashCaptureService.class));

        memberUser = account(100L, "MEMBER", "Minh Member");
        receptionist = account(7L, "RECEPTIONIST", "Rita Reception");
        manager = account(8L, "MANAGER", "Mai Manager");
        member = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-0100").build();
        payment = Payment.builder()
                .id(70L).paymentCode("PAY-0070").member(member)
                .amount(new BigDecimal("900000.00"))
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();
        registration = SportPackageRegistration.builder()
                .id(50L).registrationCode("REG-PKG-0050").member(member)
                .sportPackage(SportPackage.builder().id(5L).name("Swimming 30 Days").build())
                .totalSessions(10).remainingSessions(10)
                .paidAmount(new BigDecimal("900000.00"))
                .status(PackageRegistrationStatus.ACTIVE)
                .payment(payment)
                .build();
    }

    private static UserAccount account(Long id, String role, String name) {
        return UserAccount.builder().id(id).fullName(name).email(role.toLowerCase() + "@sportify.com")
                .role(Role.builder().code(role).name(role).build()).build();
    }

    /** Stubs the reads of quote (no lock). */
    private void stubQuoteReads(List<RefundableBookingRow> upcoming) {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findById(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(false);
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), eq(BookingStatus.CONFIRMED),
                eq(CheckInResult.ALLOWED), eq(LocalDate.of(2026, 10, 15)), eq(LocalTime.of(10, 0)))).thenReturn(upcoming);
    }

    /** Stubs the reads of submit (payment locked). */
    private void stubSubmitReads() {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(false);
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any())).thenReturn(List.of());
    }

    /** Stubs the reads of submit up to the payment-to-registration check (P1-03), without the booking query. */
    private void stubSubmitReadsUntilLinkCheck() {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
    }

    private void stubSave() {
        when(refundRequestRepository.getNextRefundCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatRefundCode(1L)).thenReturn("RFD-0001");
        when(refundRequestRepository.saveAndFlush(any(RefundRequest.class))).thenAnswer(i -> {
            RefundRequest r = i.getArgument(0);
            r.setId(10L);
            return r;
        });
    }

    /** One row of the set-based upcoming booking query. */
    private static RefundableBookingRow row(long bookingId, long sessionId) {
        return new RefundableBookingRow() {
            @Override
            public Long getBookingId() {
                return bookingId;
            }

            @Override
            public Long getSessionId() {
                return sessionId;
            }
        };
    }

    private static ClassSession session(long id) {
        return ClassSession.builder().id(id).sessionDate(LocalDate.of(2026, 10, 20))
                .startTime(LocalTime.of(18, 0)).bookedCount(5).build();
    }

    private static void assertConflict(Throwable ex, String code) {
        assertThat(ex).isInstanceOf(ConflictException.class);
        assertThat(((ConflictException) ex).getCode()).isEqualTo(code);
    }

    // ---------------------------------------------------------------- quote

    @ParameterizedTest(name = "remaining {0} of 10 -> {1}")
    @CsvSource({
            "10, 900000.00, 0",     // no session used: full amount
            "5, 450000.00, 5",      // half used: half
            "0, 0.00, 10"           // every session used: nothing
    })
    @DisplayName("Quote for 0, half and all sessions used")
    void quote_ZeroHalfAll(int remaining, String expected, int used) {
        registration.setRemainingSessions(remaining);
        stubQuoteReads(List.of());

        RefundQuoteResponse quote = service.quoteRefund(50L, memberUser);

        assertThat(quote.getSuggestedAmount()).isEqualByComparingTo(expected);
        assertThat(quote.getSuggestedAmount().scale()).isEqualTo(2);
        assertThat(quote.getUnusedSessions()).isEqualTo(remaining);
        assertThat(quote.getUsedSessions()).isEqualTo(used);
        assertThat(quote.getTotalSessions()).isEqualTo(10);
        assertThat(quote.getPaymentId()).isEqualTo(70L);
        assertThat(quote.getPaymentCode()).isEqualTo("PAY-0070");
        assertThat(quote.getPaymentAmount()).isEqualByComparingTo("900000");
        assertThat(quote.getPackageName()).isEqualTo("Swimming 30 Days");
    }

    @Test
    @DisplayName("Quote counts upcoming bookings returned by the query (already without checked-in ones) as unused")
    void quote_UpcomingBookingsAreUnused() {
        registration.setRemainingSessions(3);
        stubQuoteReads(List.of(row(1L, 301L)));

        RefundQuoteResponse quote = service.quoteRefund(50L, receptionist);

        assertThat(quote.getRemainingSessions()).isEqualTo(3);
        assertThat(quote.getUpcomingBookings()).isEqualTo(1);
        assertThat(quote.getUnusedSessions()).isEqualTo(4);
        assertThat(quote.getSuggestedAmount()).isEqualByComparingTo("360000.00");
    }

    @Test
    @DisplayName("P2-01: quote reads upcoming bookings and their check-in state with one query, whatever the number of bookings")
    void quote_UpcomingBookings_OneQuery() {
        registration.setRemainingSessions(2);
        stubQuoteReads(List.of(row(1L, 301L), row(2L, 302L), row(3L, 303L)));

        RefundQuoteResponse quote = service.quoteRefund(50L, receptionist);

        assertThat(quote.getUpcomingBookings()).isEqualTo(3);
        verify(bookingRepository, times(1)).findRefundableUpcomingBookings(anyLong(), any(), any(), any(), any());
        verifyNoMoreInteractions(bookingRepository);
    }

    @Test
    @DisplayName("Quote rounds half up to 2 decimals (1 of 3 sessions unused)")
    void quote_RoundsHalfUp() {
        registration.setTotalSessions(3);
        registration.setRemainingSessions(1);
        registration.setPaidAmount(new BigDecimal("100000.00"));
        payment.setAmount(new BigDecimal("100000.00"));
        stubQuoteReads(List.of());

        assertThat(service.quoteRefund(50L, memberUser).getSuggestedAmount()).isEqualByComparingTo("33333.33");
    }

    @Test
    void quote_ExactHalfCentTie_RoundsUp() {
        registration.setTotalSessions(8);
        registration.setRemainingSessions(1);
        registration.setPaidAmount(new BigDecimal("1.00"));
        payment.setAmount(new BigDecimal("1.00"));
        stubQuoteReads(List.of());

        // 1.00 / 8 = 0.125 exactly; HALF_EVEN would return 0.12.
        assertThat(service.quoteRefund(50L, memberUser).getSuggestedAmount()).isEqualTo(new BigDecimal("0.13"));
    }

    @Test
    @DisplayName("Quote never exceeds the payment amount")
    void quote_CappedAtPaymentAmount() {
        registration.setPaidAmount(new BigDecimal("1000000.00"));
        stubQuoteReads(List.of());

        assertThat(service.quoteRefund(50L, memberUser).getSuggestedAmount()).isEqualByComparingTo("900000.00");
    }

    @Test
    @DisplayName("Quote of another member's registration is 403")
    void quote_OtherMember_Forbidden() {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findById(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.quoteRefund(50L, account(999L, "MEMBER", "Other")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Quote without registrationId is 400")
    void quote_MissingRegistration_BadRequest() {
        assertThatThrownBy(() -> service.quoteRefund(null, memberUser)).isInstanceOf(BusinessRuleException.class);
    }

    // ---------------------------------------------------------------- submit

    @Test
    @DisplayName("Submit links the payment, uses the quote and locks the payment before reading the registration")
    void submit_LinksPaymentAndLocksFirst() {
        registration.setRemainingSessions(5);
        stubSubmitReads();
        stubSave();

        RefundResponse response = service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("Moving away").build(), memberUser);

        assertThat(response.getPaymentId()).isEqualTo(70L);
        assertThat(response.getPaymentCode()).isEqualTo("PAY-0070");
        assertThat(response.getAmountRequested()).isEqualByComparingTo("450000.00");
        ArgumentCaptor<RefundRequest> saved = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundRequestRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPayment()).isSameAs(payment);

        InOrder order = inOrder(paymentRepository, registrationRepository);
        order.verify(paymentRepository).findByIdForUpdate(70L);
        order.verify(registrationRepository).findById(50L);
    }

    @Test
    @DisplayName("Submit accepts an amount up to the payment amount")
    void submit_CustomAmountAtPaymentAmount() {
        stubSubmitReads();
        stubSave();

        RefundResponse response = service.submitRefund(RefundCreateRequest.builder().packageRegistrationId(50L)
                .reason("Injury").amountRequested(new BigDecimal("900000")).build(), memberUser);

        assertThat(response.getAmountRequested()).isEqualByComparingTo("900000.00");
    }

    @Test
    @DisplayName("Submit with an amount above the payment amount is 400 and saves nothing")
    void submit_AmountAbovePayment_BadRequest() {
        stubSubmitReads();

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder().packageRegistrationId(50L)
                .reason("Injury").amountRequested(new BigDecimal("900000.01")).build(), memberUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot exceed the payment amount");
        verify(refundRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Second refund on the same payment (one is PENDING, APPROVED or COMPLETED) is 409 PAYMENT_ALREADY_REFUNDED")
    void submit_SecondRefund_Conflict() {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(true);

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("Again").build(), memberUser))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_ALREADY_REFUNDED"));
        verify(refundRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Refund on a REFUNDED payment is 409 even though the registration is no longer ACTIVE")
    void submit_PaymentRefunded_ConflictBeforeStatusCheck() {
        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        registration.setStatus(PackageRegistrationStatus.REFUNDED);
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("Again").build(), memberUser))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_ALREADY_REFUNDED"));
    }

    @Test
    @DisplayName("The set of live statuses is PENDING, APPROVED, COMPLETED (a REJECTED request does not block)")
    @SuppressWarnings("unchecked")
    void submit_LiveStatuses() {
        stubSubmitReads();
        stubSave();

        service.submitRefund(RefundCreateRequest.builder().packageRegistrationId(50L).reason("x").build(), memberUser);

        ArgumentCaptor<java.util.Collection<RefundStatus>> statuses = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(refundRequestRepository).existsByPaymentIdAndStatusIn(eq(70L), statuses.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(RefundStatus.PENDING, RefundStatus.APPROVED, RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("Registration without a payment is 409 PAYMENT_NOT_REFUNDABLE")
    void submit_NoPayment_Conflict() {
        registration.setPayment(null);
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.empty());
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("x").build(), receptionist))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_NOT_REFUNDABLE"));
    }

    @Test
    @DisplayName("Payment still PENDING is 409 PAYMENT_NOT_REFUNDABLE")
    void submit_PaymentPending_Conflict() {
        payment.setPaymentStatus(PaymentStatus.PENDING);
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(false);

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("x").build(), memberUser))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).getCode()).isEqualTo("PAYMENT_NOT_REFUNDABLE"));
    }

    @Test
    @DisplayName("Every session used: submit is 400")
    void submit_AllUsed_BadRequest() {
        registration.setRemainingSessions(0);
        stubSubmitReads();

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("x").build(), memberUser))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("zero remaining sessions");
    }

    // ---------------------------------------------------------------- review

    private RefundRequest pendingRefund() {
        return RefundRequest.builder().id(10L).refundCode("RFD-0001").payment(payment)
                .packageRegistration(registration).member(member).requestedBy(memberUser)
                .amountRequested(new BigDecimal("450000.00")).status(RefundStatus.PENDING).build();
    }

    @Test
    @DisplayName("Approve above the payment amount is 400")
    void review_ApproveAbovePayment_BadRequest() {
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingRefund()));

        assertThatThrownBy(() -> service.reviewRefund(10L, RefundReviewRequest.builder().approved(true)
                .amountApproved(new BigDecimal("1000000")).build(), manager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot exceed the payment amount");
    }

    @Test
    @DisplayName("Approve locks the payment first, then the refund; registration REFUNDED (unchanged rule)")
    void review_Approve_LocksPaymentThenRefund() {
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingRefund()));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.reviewRefund(10L, RefundReviewRequest.builder().approved(true).build(), manager);

        assertThat(response.getStatus()).isEqualTo(RefundStatus.APPROVED);
        assertThat(response.getAmountApproved()).isEqualByComparingTo("450000.00");
        assertThat(registration.getStatus()).isEqualTo(PackageRegistrationStatus.REFUNDED);
        InOrder order = inOrder(paymentRepository, refundRequestRepository);
        order.verify(paymentRepository).findByIdForUpdate(70L);
        order.verify(refundRequestRepository).findByIdForUpdate(10L);
    }

    // ---------------------------------------------------------------- complete

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "100.005"})
    void review_ExcessPrecision_RejectsExplicitAndFallbackAmount(String amount) {
        RefundRequest refund = pendingRefund();
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.reviewRefund(10L, RefundReviewRequest.builder().approved(true)
                .amountApproved(new BigDecimal(amount)).build(), manager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Amount approved must have at most 2 decimal places");
        refund.setAmountRequested(new BigDecimal(amount));
        assertThatThrownBy(() -> service.reviewRefund(10L, RefundReviewRequest.builder().approved(true).build(), manager))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Amount approved must have at most 2 decimal places");
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.PENDING);
        verify(refundRequestRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.00", "100.000"})
    void review_NormalizesApprovedAmountWithoutRounding(String amount) {
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingRefund()));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.reviewRefund(10L, RefundReviewRequest.builder().approved(true)
                .amountApproved(new BigDecimal(amount)).build(), manager);

        assertThat(response.getAmountApproved()).isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void review_LegacyWithoutPayment_CapsExplicitAndFallbackAmountAtPaidAmount() {
        RefundRequest refund = pendingRefund();
        refund.setPayment(null);
        registration.setPaidAmount(new BigDecimal("100.00"));
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.empty());
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.reviewRefund(10L, RefundReviewRequest.builder().approved(true)
                .amountApproved(new BigDecimal("100.01")).build(), manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot exceed the registration paid amount");
        assertThatThrownBy(() -> service.reviewRefund(10L, RefundReviewRequest.builder().approved(true).build(), manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cannot exceed the registration paid amount");
        assertThat(registration.getStatus()).isEqualTo(PackageRegistrationStatus.ACTIVE);
        verify(refundRequestRepository, never()).save(any());
        verify(registrationRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"100.00"})
    void review_LegacyAtPaidAmountOrWithoutKnownPaidAmount_Accepts(String paidAmount) {
        RefundRequest refund = pendingRefund();
        refund.setPayment(null);
        registration.setPaidAmount(paidAmount == null ? null : new BigDecimal(paidAmount));
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.empty());
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.reviewRefund(10L, RefundReviewRequest.builder().approved(true)
                .amountApproved(new BigDecimal("100")).build(), manager).getStatus()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    void review_LinkedRefund_StillCapsAtPaymentRatherThanRegistrationPaidAmount() {
        registration.setPaidAmount(new BigDecimal("100.00"));
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingRefund()));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.reviewRefund(10L, RefundReviewRequest.builder().approved(true).build(), manager)
                .getAmountApproved()).isEqualTo(new BigDecimal("450000.00"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "100.005"})
    void complete_InvalidResolvedAmount_RejectsLinkedAndLegacyRefunds(String value) {
        RefundRequest refund = approvedRefund();
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        refund.setAmountApproved(amount);
        refund.setAmountRequested(amount);
        stubCompleteLocks(refund);

        assertThatThrownBy(() -> service.completeRefund(10L, receptionist)).isInstanceOf(BusinessRuleException.class)
                .hasMessage(value != null && amount.signum() > 0
                        ? "Amount refunded must have at most 2 decimal places" : "Amount refunded must be greater than zero");
        refund.setPayment(null);
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.completeRefund(10L, receptionist)).isInstanceOf(BusinessRuleException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        verifyNoInteractions(paymentRefundService, bookingRepository, sessionRepository, auditService);
        verify(refundRequestRepository, never()).save(any());
    }

    private RefundRequest approvedRefund() {
        RefundRequest refund = pendingRefund();
        refund.setStatus(RefundStatus.APPROVED);
        refund.setAmountApproved(new BigDecimal("450000.00"));
        registration.setStatus(PackageRegistrationStatus.REFUNDED);
        return refund;
    }

    @Test
    @DisplayName("Complete: payment and invoice REFUNDED through PaymentRefundService, registration REFUNDED, upcoming bookings cancelled, seats released, log written")
    void complete_UpdatesEverything() {
        RefundRequest refund = approvedRefund();
        stubCompleteLocks(refund);
        when(paymentRefundService.markRefunded(70L, "RFD-0001", new BigDecimal("450000.00"))).thenAnswer(i -> {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            return payment;
        });
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), eq(BookingStatus.CONFIRMED),
                eq(CheckInResult.ALLOWED), any(), any())).thenReturn(List.of(row(1L, 301L)));
        when(sessionRepository.findForUpdate(301L)).thenReturn(Optional.of(session(301L)));
        when(bookingRepository.cancelConfirmedSessionBookings(List.of(1L), 301L, 7L, CANCEL_REASON, NOW)).thenReturn(1);
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.completeRefund(10L, receptionist);

        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.getCancelledBookings()).isEqualTo(1);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(registration.getStatus()).isEqualTo(PackageRegistrationStatus.REFUNDED);
        assertThat(registration.getRemainingSessions()).isEqualTo(10);
        verify(sessionRepository).releaseSeats(301L, 1, NOW);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo(AuditAction.REFUND_COMPLETED);
        assertThat(event.getValue().getEntityCode()).isEqualTo("RFD-0001");
        assertThat(event.getValue().getDetails()).containsEntry("cancelledBookingIds", List.of(1L));

        InOrder order = inOrder(paymentRepository, refundRequestRepository, paymentRefundService, sessionRepository,
                bookingRepository);
        order.verify(paymentRepository).findByIdForUpdate(70L);
        order.verify(refundRequestRepository).findByIdForUpdate(10L);
        order.verify(paymentRefundService).markRefunded(anyLong(), anyString(), any());
        order.verify(sessionRepository).findForUpdate(301L);
        order.verify(bookingRepository).cancelConfirmedSessionBookings(any(), eq(301L), any(), any(), any());
        order.verify(sessionRepository).releaseSeats(301L, 1, NOW);
    }

    private void stubCompleteLocks(RefundRequest refund) {
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
    }

    @Test
    @DisplayName("P1-02: complete locks every session in ascending id order before any booking change, then releases each session's seats atomically, then writes the registration")
    void complete_LocksSessionsAscendingThenBookingsThenRegistration() {
        RefundRequest refund = approvedRefund();
        registration.setStatus(PackageRegistrationStatus.ACTIVE);
        stubCompleteLocks(refund);
        when(paymentRefundService.markRefunded(anyLong(), anyString(), any())).thenReturn(payment);
        List<RefundableBookingRow> rows = List.of(row(3L, 305L), row(1L, 301L), row(4L, 305L));
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any())).thenReturn(rows);
        when(sessionRepository.findForUpdate(301L)).thenReturn(Optional.of(session(301L)));
        when(sessionRepository.findForUpdate(305L)).thenReturn(Optional.of(session(305L)));
        when(bookingRepository.cancelConfirmedSessionBookings(List.of(1L), 301L, 8L, CANCEL_REASON, NOW)).thenReturn(1);
        when(bookingRepository.cancelConfirmedSessionBookings(List.of(3L, 4L), 305L, 8L, CANCEL_REASON, NOW)).thenReturn(2);
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.completeRefund(10L, manager);

        assertThat(response.getCancelledBookings()).isEqualTo(3);
        InOrder order = inOrder(sessionRepository, bookingRepository, registrationRepository);
        order.verify(sessionRepository).findForUpdate(301L);
        order.verify(sessionRepository).findForUpdate(305L);
        order.verify(bookingRepository).cancelConfirmedSessionBookings(List.of(1L), 301L, 8L, CANCEL_REASON, NOW);
        order.verify(sessionRepository).releaseSeats(301L, 1, NOW);
        order.verify(bookingRepository).cancelConfirmedSessionBookings(List.of(3L, 4L), 305L, 8L, CANCEL_REASON, NOW);
        order.verify(sessionRepository).releaseSeats(305L, 2, NOW);
        order.verify(registrationRepository).save(registration);
        // Bookings are re-read once the sessions are locked.
        verify(bookingRepository, times(2)).findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-02: a booking cancelled by its member before the session lock is skipped and releases no seat")
    void complete_BookingCancelledBeforeLock_Skipped() {
        RefundRequest refund = approvedRefund();
        stubCompleteLocks(refund);
        when(paymentRefundService.markRefunded(anyLong(), anyString(), any())).thenReturn(payment);
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any()))
                .thenReturn(List.of(row(1L, 301L), row(2L, 301L)))
                .thenReturn(List.of(row(1L, 301L)));
        when(sessionRepository.findForUpdate(301L)).thenReturn(Optional.of(session(301L)));
        when(bookingRepository.cancelConfirmedSessionBookings(List.of(1L), 301L, 8L, CANCEL_REASON, NOW)).thenReturn(1);
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.completeRefund(10L, manager);

        assertThat(response.getCancelledBookings()).isEqualTo(1);
        verify(sessionRepository).releaseSeats(301L, 1, NOW);
    }

    @Test
    @DisplayName("P1-02: a booking checked in between the read and the update is 409 REFUND_BOOKINGS_CHANGED and no seat is released")
    void complete_CheckedInDuringUpdate_ConflictNoSeatRelease() {
        RefundRequest refund = approvedRefund();
        stubCompleteLocks(refund);
        when(paymentRefundService.markRefunded(anyLong(), anyString(), any())).thenReturn(payment);
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any()))
                .thenReturn(List.of(row(1L, 301L)));
        when(sessionRepository.findForUpdate(301L)).thenReturn(Optional.of(session(301L)));
        when(bookingRepository.cancelConfirmedSessionBookings(List.of(1L), 301L, 8L, CANCEL_REASON, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.completeRefund(10L, manager))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_REFUND_BOOKINGS_CHANGED));
        verify(sessionRepository, never()).releaseSeats(anyLong(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(auditService, never()).record(any());
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
    }

    @Test
    @DisplayName("P1-02: a new booking on a session that was not locked is 409 REFUND_BOOKINGS_CHANGED; nothing is cancelled")
    void complete_NewBookingOnUnlockedSession_Conflict() {
        RefundRequest refund = approvedRefund();
        stubCompleteLocks(refund);
        when(paymentRefundService.markRefunded(anyLong(), anyString(), any())).thenReturn(payment);
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any()))
                .thenReturn(List.of(row(1L, 301L)))
                .thenReturn(List.of(row(1L, 301L), row(9L, 302L)));
        when(sessionRepository.findForUpdate(301L)).thenReturn(Optional.of(session(301L)));

        assertThatThrownBy(() -> service.completeRefund(10L, manager))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_REFUND_BOOKINGS_CHANGED));
        verify(sessionRepository, never()).findForUpdate(302L);
        verify(bookingRepository, never()).cancelConfirmedSessionBookings(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("P1-03: complete when the registration is paid by another payment is 409 PAYMENT_REGISTRATION_MISMATCH; the payment is not refunded")
    void complete_RegistrationPaidByAnotherPayment_Conflict() {
        RefundRequest refund = approvedRefund();
        registration.setPayment(Payment.builder().id(71L).paymentCode("PAY-0071").member(member).build());
        stubCompleteLocks(refund);

        assertThatThrownBy(() -> service.completeRefund(10L, manager))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_PAYMENT_REGISTRATION_MISMATCH));
        verify(paymentRefundService, never()).markRefunded(anyLong(), anyString(), any());
        verify(bookingRepository, never()).findRefundableUpcomingBookings(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Complete: a registration still ACTIVE (legacy path) becomes REFUNDED")
    void complete_RegistrationStillActive_BecomesRefunded() {
        RefundRequest refund = approvedRefund();
        registration.setStatus(PackageRegistrationStatus.ACTIVE);
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(paymentRefundService.markRefunded(anyLong(), anyString(), any())).thenReturn(payment);
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any())).thenReturn(List.of());
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        service.completeRefund(10L, manager);

        assertThat(registration.getStatus()).isEqualTo(PackageRegistrationStatus.REFUNDED);
        assertThat(registration.getCancelReason()).isEqualTo("Refund completed: RFD-0001");
        verify(registrationRepository).save(registration);
    }

    @Test
    @DisplayName("Complete when the payment is already REFUNDED: 409 from PaymentRefundService, nothing else changes")
    void complete_PaymentAlreadyRefunded_Conflict() {
        RefundRequest refund = approvedRefund();
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(paymentRefundService.markRefunded(anyLong(), anyString(), any()))
                .thenThrow(new ConflictException("already", PaymentRefundService.CODE_PAYMENT_ALREADY_REFUNDED));

        assertThatThrownBy(() -> service.completeRefund(10L, manager)).isInstanceOf(ConflictException.class);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
        verify(bookingRepository, never()).findRefundableUpcomingBookings(any(), any(), any(), any(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    @DisplayName("Complete a refund that is not APPROVED is 400 and the payment is not touched")
    void complete_NotApproved_BadRequest() {
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingRefund()));

        assertThatThrownBy(() -> service.completeRefund(10L, manager)).isInstanceOf(BusinessRuleException.class);
        verify(paymentRefundService, never()).markRefunded(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("Complete a legacy refund without a payment: no payment call, registration and bookings still handled")
    void complete_LegacyWithoutPayment() {
        RefundRequest refund = approvedRefund();
        refund.setPayment(null);
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.empty());
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(bookingRepository.findRefundableUpcomingBookings(eq(50L), any(), any(), any(), any())).thenReturn(List.of());
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.completeRefund(10L, manager);

        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.getCancelledBookings()).isZero();
        verify(paymentRefundService, never()).markRefunded(anyLong(), anyString(), any());
    }

    // ---------------------------------------------------------------- P1-03 payment ownership

    @Test
    @DisplayName("P1-03: quote when the payment belongs to another member is 409 PAYMENT_REGISTRATION_MISMATCH")
    void quote_PaymentOfAnotherMember_Conflict() {
        payment.setMember(MemberProfile.builder().id(101L).build());
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findById(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> service.quoteRefund(50L, receptionist))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_PAYMENT_REGISTRATION_MISMATCH));
    }

    @Test
    @DisplayName("P1-03: submit when the same payment also pays a membership card is 409 PAYMENT_REGISTRATION_MISMATCH")
    void submit_PaymentAlsoPaysMemberCard_Conflict() {
        stubSubmitReadsUntilLinkCheck();
        when(memberCardRepository.existsByPaymentId(70L)).thenReturn(true);

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("x").build(), memberUser))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_PAYMENT_REGISTRATION_MISMATCH));
        verify(refundRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("P1-03: submit when an invoice line of the payment bills another item is 409 PAYMENT_REGISTRATION_MISMATCH")
    void submit_InvoiceLineForOtherItem_Conflict() {
        stubSubmitReadsUntilLinkCheck();
        when(invoiceLineRepository.countPaymentLinesForOtherItems(70L, InvoiceItemType.SPORT_PACKAGE, 50L)).thenReturn(1L);

        assertThatThrownBy(() -> service.submitRefund(RefundCreateRequest.builder()
                .packageRegistrationId(50L).reason("x").build(), memberUser))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_PAYMENT_REGISTRATION_MISMATCH));
        verify(refundRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("P1-03: submit checks the invoice lines against this registration's id and the SPORT_PACKAGE type")
    void submit_ChecksInvoiceLinesOfThisRegistration() {
        stubSubmitReads();
        stubSave();

        service.submitRefund(RefundCreateRequest.builder().packageRegistrationId(50L).reason("x").build(), memberUser);

        verify(memberCardRepository).existsByPaymentId(70L);
        verify(invoiceLineRepository).countPaymentLinesForOtherItems(70L, InvoiceItemType.SPORT_PACKAGE, 50L);
    }

    @Test
    @DisplayName("P1-03: approving a refund whose payment also pays a membership card is 409 and the registration is unchanged")
    void review_ApprovePaymentMismatch_Conflict() {
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingRefund()));
        when(memberCardRepository.existsByPaymentId(70L)).thenReturn(true);

        assertThatThrownBy(() -> service.reviewRefund(10L, RefundReviewRequest.builder().approved(true).build(), manager))
                .satisfies(ex -> assertConflict(ex, PaymentRefundService.CODE_PAYMENT_REGISTRATION_MISMATCH));
        assertThat(registration.getStatus()).isEqualTo(PackageRegistrationStatus.ACTIVE);
        verify(registrationRepository, never()).save(any());
    }
}
