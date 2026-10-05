package com.sportify.catalog.service;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.RefundRequestRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.impl.RefundServiceImpl;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.Role;
import com.sportify.identity.entity.UserAccount;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("UTC"));

    @InjectMocks
    private RefundServiceImpl refundService;

    private UserAccount memberUser;
    private UserAccount managerUser;
    private MemberProfile memberProfile;
    private SportPackageRegistration activeRegistration;

    @BeforeEach
    void setUp() {
        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        memberUser = UserAccount.builder().id(100L).role(memberRole).fullName("Member User").build();
        memberProfile = MemberProfile.builder().id(100L).userAccount(memberUser).memberCode("MEM-100").build();

        Role managerRole = Role.builder().id(2L).code("MANAGER").build();
        managerUser = UserAccount.builder().id(200L).role(managerRole).fullName("Manager User").build();

        activeRegistration = SportPackageRegistration.builder()
                .id(50L)
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

        when(registrationRepository.findById(50L)).thenReturn(Optional.of(activeRegistration));
        when(refundRequestRepository.getNextRefundCodeSequence()).thenReturn(1L);
        when(codeFormatter.formatRefundCode(1L)).thenReturn("RFD-001");
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> {
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
    void submitRefund_AsDifferentMember_ThrowsBusinessRuleException() {
        Role memberRole = Role.builder().id(1L).code("MEMBER").build();
        UserAccount otherMember = UserAccount.builder().id(999L).role(memberRole).build();

        RefundCreateRequest req = RefundCreateRequest.builder()
                .packageRegistrationId(50L)
                .reason("Moving away")
                .build();

        when(registrationRepository.findById(50L)).thenReturn(Optional.of(activeRegistration));

        assertThrows(BusinessRuleException.class, () -> refundService.submitRefund(req, otherMember));
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

        when(refundRequestRepository.findById(10L)).thenReturn(Optional.of(refund));
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

        when(refundRequestRepository.findById(10L)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any(RefundRequest.class))).thenAnswer(i -> i.getArgument(0));

        RefundResponse res = refundService.reviewRefund(10L, reviewReq, managerUser);

        assertNotNull(res);
        assertEquals(RefundStatus.REJECTED, res.getStatus());
        assertEquals(PackageRegistrationStatus.ACTIVE, activeRegistration.getStatus()); // Untouched
    }
}
