package com.sportify.catalog.service;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundQuoteResponse;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.Booking;
import com.sportify.catalog.entity.BookingStatus;
import com.sportify.catalog.entity.CheckInResult;
import com.sportify.catalog.entity.ClassSession;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.entity.SportPackage;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CheckInRepository;
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
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BE-21: refunds linked to payments. Quote, submit rules (409 on a second refund, cap at the payment amount),
 * review cap and completion side effects.
 */
@ExtendWith(MockitoExtension.class)
class RefundPaymentLinkTest {

    /** 2026-10-15 10:00 in Ho Chi Minh City. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private SportPackageRegistrationRepository registrationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentRefundService paymentRefundService;
    @Mock private BookingRepository bookingRepository;
    @Mock private CheckInRepository checkInRepository;
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
                paymentRefundService, bookingRepository, checkInRepository, auditService, codeFormatter, CLOCK);

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
    private void stubQuoteReads(List<Booking> upcoming) {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findById(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(false);
        when(bookingRepository.findUpcomingByPackageRegistration(eq(50L), eq(BookingStatus.CONFIRMED),
                eq(LocalDate.of(2026, 10, 15)), eq(LocalTime.of(10, 0)))).thenReturn(upcoming);
    }

    /** Stubs the reads of submit (payment locked). */
    private void stubSubmitReads() {
        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(registration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(false);
        when(bookingRepository.findUpcomingByPackageRegistration(eq(50L), any(), any(), any())).thenReturn(List.of());
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

    private Booking upcomingBooking(Long id) {
        ClassSession session = ClassSession.builder().id(300L + id).sessionDate(LocalDate.of(2026, 10, 20))
                .startTime(LocalTime.of(18, 0)).bookedCount(5).build();
        return Booking.builder().id(id).session(session).member(member).packageRegistration(registration)
                .status(BookingStatus.CONFIRMED).build();
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
    @DisplayName("Quote counts upcoming bookings as unused, except one already checked in")
    void quote_UpcomingBookingsAreUnused() {
        registration.setRemainingSessions(3);
        Booking first = upcomingBooking(1L);
        Booking checkedIn = upcomingBooking(2L);
        stubQuoteReads(List.of(first, checkedIn));
        when(checkInRepository.existsByBookingIdAndResult(1L, CheckInResult.ALLOWED)).thenReturn(false);
        when(checkInRepository.existsByBookingIdAndResult(2L, CheckInResult.ALLOWED)).thenReturn(true);

        RefundQuoteResponse quote = service.quoteRefund(50L, receptionist);

        assertThat(quote.getRemainingSessions()).isEqualTo(3);
        assertThat(quote.getUpcomingBookings()).isEqualTo(1);
        assertThat(quote.getUnusedSessions()).isEqualTo(4);
        assertThat(quote.getSuggestedAmount()).isEqualByComparingTo("360000.00");
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
        Booking upcoming = upcomingBooking(1L);
        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(payment));
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(paymentRefundService.markRefunded(70L, "RFD-0001", new BigDecimal("450000.00"))).thenAnswer(i -> {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            return payment;
        });
        when(bookingRepository.findUpcomingByPackageRegistration(eq(50L), eq(BookingStatus.CONFIRMED), any(), any()))
                .thenReturn(List.of(upcoming));
        when(checkInRepository.existsByBookingIdAndResult(1L, CheckInResult.ALLOWED)).thenReturn(false);
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.completeRefund(10L, receptionist);

        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.getCancelledBookings()).isEqualTo(1);
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(registration.getStatus()).isEqualTo(PackageRegistrationStatus.REFUNDED);
        assertThat(upcoming.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(upcoming.getCancelledBy()).isSameAs(receptionist);
        assertThat(upcoming.getCancelReason()).isEqualTo("Refund completed: RFD-0001");
        assertThat(upcoming.getSession().getBookedCount()).isEqualTo(4);
        assertThat(registration.getRemainingSessions()).isEqualTo(10);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).record(event.capture());
        assertThat(event.getValue().getAction()).isEqualTo(AuditAction.REFUND_COMPLETED);
        assertThat(event.getValue().getEntityCode()).isEqualTo("RFD-0001");

        InOrder order = inOrder(paymentRepository, refundRequestRepository, paymentRefundService, bookingRepository);
        order.verify(paymentRepository).findByIdForUpdate(70L);
        order.verify(refundRequestRepository).findByIdForUpdate(10L);
        order.verify(paymentRefundService).markRefunded(anyLong(), anyString(), any());
        order.verify(bookingRepository).save(upcoming);
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
        when(bookingRepository.findUpcomingByPackageRegistration(eq(50L), any(), any(), any())).thenReturn(List.of());
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
        verify(bookingRepository, never()).findUpcomingByPackageRegistration(anyLong(), any(), any(), any());
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
        when(bookingRepository.findUpcomingByPackageRegistration(eq(50L), any(), any(), any())).thenReturn(List.of());
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse response = service.completeRefund(10L, manager);

        assertThat(response.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.getCancelledBookings()).isZero();
        verify(paymentRefundService, never()).markRefunded(anyLong(), anyString(), any());
    }
}
