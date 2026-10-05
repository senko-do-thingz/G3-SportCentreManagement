package com.sportify.catalog.service.impl;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.RefundRequest;
import com.sportify.catalog.entity.RefundStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.RefundRequestRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.catalog.service.RefundService;
import com.sportify.core.common.CodeFormatter;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final SportPackageRegistrationRepository registrationRepository;
    private final CodeFormatter codeFormatter;
    private final Clock clock;

    @Override
    @Transactional
    public RefundResponse submitRefund(RefundCreateRequest request, UserAccount actor) {
        SportPackageRegistration reg = registrationRepository.findById(request.getPackageRegistrationId())
                .orElseThrow(() -> new ResourceNotFoundException("Package registration not found for id: " + request.getPackageRegistrationId()));

        if (reg.getStatus() != PackageRegistrationStatus.ACTIVE) {
            throw new BusinessRuleException("Only active package registrations can be refunded");
        }

        if (reg.getRemainingSessions() <= 0) {
            throw new BusinessRuleException("Cannot refund package with zero remaining sessions");
        }

        // Pro-rated refund amount based on unused remaining sessions
        BigDecimal unusedFraction = BigDecimal.valueOf(reg.getRemainingSessions())
                .divide(BigDecimal.valueOf(reg.getTotalSessions()), 4, RoundingMode.HALF_UP);
        BigDecimal amountRequested = reg.getPaidAmount().multiply(unusedFraction).setScale(2, RoundingMode.HALF_UP);

        long seq = refundRequestRepository.getNextRefundCodeSequence();
        String refundCode = codeFormatter.formatRefundCode(seq);

        RefundRequest refund = RefundRequest.builder()
                .refundCode(refundCode)
                .packageRegistration(reg)
                .member(reg.getMember())
                .amountRequested(amountRequested)
                .reason(request.getReason())
                .status(RefundStatus.PENDING)
                .requestedBy(actor)
                .build();

        refund = refundRequestRepository.save(refund);
        return mapToRefundResponse(refund);
    }

    @Override
    @Transactional
    public RefundResponse reviewRefund(Long refundId, RefundReviewRequest request, UserAccount manager) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found for id: " + refundId));

        if (refund.getStatus() != RefundStatus.PENDING) {
            throw new BusinessRuleException("Refund request has already been reviewed");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        refund.setReviewedBy(manager);
        refund.setReviewedAt(now);
        refund.setManagerNote(request.getManagerNote());

        if (Boolean.TRUE.equals(request.getApproved())) {
            BigDecimal approvedAmount = request.getAmountApproved() != null
                    ? request.getAmountApproved()
                    : refund.getAmountRequested();
            refund.setAmountApproved(approvedAmount);
            refund.setStatus(RefundStatus.APPROVED);

            // Cancel the package registration upon refund approval
            SportPackageRegistration reg = refund.getPackageRegistration();
            reg.setStatus(PackageRegistrationStatus.CANCELLED);
            reg.setCancelledAt(now);
            reg.setCancelReason("Refund approved: " + refund.getRefundCode());
            registrationRepository.save(reg);
        } else {
            refund.setStatus(RefundStatus.REJECTED);
        }

        refund = refundRequestRepository.save(refund);
        return mapToRefundResponse(refund);
    }

    @Override
    @Transactional
    public RefundResponse completeRefund(Long refundId, UserAccount actor) {
        RefundRequest refund = refundRequestRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Refund request not found for id: " + refundId));

        if (refund.getStatus() != RefundStatus.APPROVED) {
            throw new BusinessRuleException("Only approved refunds can be completed");
        }

        refund.setStatus(RefundStatus.COMPLETED);
        refund = refundRequestRepository.save(refund);
        return mapToRefundResponse(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getPendingRefunds() {
        return refundRequestRepository.findByStatusOrderByCreatedAtAsc(RefundStatus.PENDING).stream()
                .map(this::mapToRefundResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getMyRefunds(UserAccount currentUser) {
        return getMemberRefunds(currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getMemberRefunds(Long memberId) {
        return refundRequestRepository.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(this::mapToRefundResponse)
                .collect(Collectors.toList());
    }

    private RefundResponse mapToRefundResponse(RefundRequest r) {
        return RefundResponse.builder()
                .id(r.getId())
                .refundCode(r.getRefundCode())
                .packageRegistrationId(r.getPackageRegistration().getId())
                .registrationCode(r.getPackageRegistration().getRegistrationCode())
                .memberId(r.getMember().getId())
                .memberCode(r.getMember().getMemberCode())
                .memberFullName(r.getMember().getUserAccount().getFullName())
                .amountRequested(r.getAmountRequested())
                .amountApproved(r.getAmountApproved())
                .reason(r.getReason())
                .status(r.getStatus())
                .requestedById(r.getRequestedBy().getId())
                .requestedByName(r.getRequestedBy().getFullName())
                .reviewedById(r.getReviewedBy() != null ? r.getReviewedBy().getId() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewedAt(r.getReviewedAt())
                .managerNote(r.getManagerNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
