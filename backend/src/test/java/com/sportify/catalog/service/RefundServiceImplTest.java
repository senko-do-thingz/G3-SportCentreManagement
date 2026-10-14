package com.sportify.catalog.service;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.BookingRepository;
import com.sportify.catalog.repository.CheckInRepository;
import com.sportify.catalog.repository.RefundRequestRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.RefundServiceImpl;
import com.sportify.core.audit.AuditService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import org.springframework.security.access.AccessDeniedException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentRefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RefundServiceImplTest {

    @Mock
    private RefundRequestRepository refundRequestRepository;
    @Mock
    private SportPackageRegistrationRepository registrationRepository;
    @Mock
    private CodeFormatter codeFormatter;
    // BE-21: refunds are linked to the payment of the registration and cancel upcoming bookings on completion.
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentRefundService paymentRefundService;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CheckInRepository checkInRepository;
    @Mock
    private AuditService auditService;
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private RefundServiceImpl refundService;

    private UserAccount memberUser;
    private UserAccount managerUser;
    private MemberProfile memberProfile;
    private SportPackageRegistration activeRegistration;
    private Payment successPayment;

    @BeforeEach
    void setUp() {
        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        memberUser = UserAccount.builder().id(100L).role(memberRole).fullName("Member User").build();
        memberProfile = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-100").build();

        Role managerRole = Role.builder().id(2L).code("MANAGER").build();
        managerUser = UserAccount.builder().id(200L).role(managerRole).fullName("Manager User").build();

        // BE-21: a refund needs the SUCCESS payment that bought the registration.
        successPayment = Payment.builder()
                .id(70L)
                .paymentCode("PAY-0070")
                .amount(BigDecimal.valueOf(1000000.00))
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        activeRegistration = SportPackageRegistration.builder()
                .id(50L)
                .payment(successPayment)
                .member(memberProfile)
                .totalSessions(8)
                .remainingSessions(4)
                .paidAmount(BigDecimal.valueOf(1000000.00))
                .status(PackageRegistrationStatus.ACTIVE)
                .build();
    }

    @Test
    void submitRefund_AsOwner_Success_CalculatesProRatedAmount() {
        RefundCreateRequest req = RefundCreateRequest.builder()
                .packageRegistrationId(50L)
                .reason("Moving away")
                .build();

        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(successPayment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(activeRegistration));
        when(refundRequestRepository.existsByPaymentIdAndStatusIn(eq(70L), anyCollection())).thenReturn(false);
        when(bookingRepository.findUpcomingByPackageRegistration(eq(50L), any(), any(), any())).thenReturn(List.of());
        when(refundRequestRepository.getNextRefundCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatRefundCode(1L)).thenReturn("RFD-001");
        when(refundRequestRepository.saveAndFlush(any(RefundRequest.class))).thenAnswer(i -> {
            RefundRequest r = i.getArgument(0);
            r.setId(10L);
            return r;
        });

        RefundResponse res = refundService.submitRefund(req, memberUser);

        assertNotNull(res);
        assertEquals(RefundStatus.PENDING, res.getStatus());
        // 4 remaining out of 8 = 50% of 1,000,000 = 500,000
        assertEquals(new BigDecimal("500000.00"), res.getAmountRequested());
    }

    @Test
    void submitRefund_AsDifferentMember_ThrowsAccessDeniedException() {
        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        UserAccount otherMember = UserAccount.builder().id(999L).role(memberRole).build();

        RefundCreateRequest req = RefundCreateRequest.builder()
                .packageRegistrationId(50L)
                .reason("Moving away")
                .build();

        when(registrationRepository.findPaymentIdById(50L)).thenReturn(Optional.of(70L));
        when(paymentRepository.findByIdForUpdate(70L)).thenReturn(Optional.of(successPayment));
        when(registrationRepository.findById(50L)).thenReturn(Optional.of(activeRegistration));

        assertThrows(AccessDeniedException.class, () -> refundService.submitRefund(req, otherMember));
    }

    @Test
    void reviewRefund_Approve_SetsStatusApprovedAndPackageStatusRefunded() {
        RefundRequest refund = RefundRequest.builder()
                .id(10L)
                .member(memberProfile)
                .requestedBy(memberUser)
                .packageRegistration(activeRegistration)
                .amountRequested(BigDecimal.valueOf(500000.00))
                .status(RefundStatus.PENDING)
                .refundCode("RFD-001")
                .build();

        RefundReviewRequest reviewReq = RefundReviewRequest.builder()
                .approved(true)
                .amountApproved(BigDecimal.valueOf(500000.00))
                .managerNote("Approved")
                .build();

        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.empty());
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse res = refundService.reviewRefund(10L, reviewReq, managerUser);

        assertNotNull(res);
        assertEquals(RefundStatus.APPROVED, res.getStatus());
        assertEquals(PackageRegistrationStatus.REFUNDED, activeRegistration.getStatus());
        verify(registrationRepository).save(activeRegistration);
    }

    @Test
    void reviewRefund_Reject_SetsStatusRejected() {
        RefundRequest refund = RefundRequest.builder()
                .id(10L)
                .member(memberProfile)
                .requestedBy(memberUser)
                .packageRegistration(activeRegistration)
                .status(RefundStatus.PENDING)
                .build();

        RefundReviewRequest reviewReq = RefundReviewRequest.builder()
                .approved(false)
                .managerNote("Rejected")
                .build();

        when(refundRequestRepository.findPaymentIdById(10L)).thenReturn(Optional.empty());
        when(refundRequestRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse res = refundService.reviewRefund(10L, reviewReq, managerUser);

        assertNotNull(res);
        assertEquals(RefundStatus.REJECTED, res.getStatus());
        assertEquals(PackageRegistrationStatus.ACTIVE, activeRegistration.getStatus()); // Untouched
    }
}
