package com.sportify.demoqr.controller;

import com.sportify.core.common.SecurityUtils;
import com.sportify.demoqr.service.DemoQrException;
import com.sportify.demoqr.service.DemoQrService;
import com.sportify.demoqr.service.SqlQrStore;
import com.sportify.identity.entity.MemberProfile;
import com.sportify.identity.entity.UserAccount;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.impl.PaymentItemResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Reception Desk read-only simulated QR presentation controller.
 * Allows authenticated Receptionists and Managers to inspect the desk display projection
 * for a payment without exposing member confirmation tokens or mutating payment state.
 */
@RestController
@RequestMapping("/api/v1/payments/simulated-qr/desk-projection")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class ReceptionQrDisplayController {

    private final PaymentRepository paymentRepository;
    private final PaymentItemResolver itemResolver;
    private final SqlQrStore store;

    public record ActiveSessionDto(
            UUID sessionId,
            DemoQrService.Bank bankCode,
            String bankLabel,
            DemoQrService.State state,
            Instant createdAt,
            Instant expiresAt,
            Instant serverTime
    ) {}

    public record DeskProjectionResponse(
            long paymentId,
            String paymentCode,
            BigDecimal amount,
            String currency,
            PaymentStatus paymentStatus,
            String itemDescription,
            String memberFullName,
            String memberCode,
            Long memberId,
            String settlementType,
            String settlementProvider,
            Long simulatedTransactionId,
            ActiveSessionDto activeSession
    ) {}

    @GetMapping("/{paymentId}")
    @Transactional(readOnly = true)
    public DeskProjectionResponse getDeskProjection(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (paymentId == null || paymentId <= 0 || userDetails == null
                || !SecurityUtils.isStaff(userDetails.getUserAccount())) {
            throw DemoQrException.unavailable();
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(DemoQrException::unavailable);

        MemberProfile member = payment.getMember();
        UserAccount account = member != null ? member.getUserAccount() : null;
        String memberFullName = account != null ? account.getFullName() : null;
        String memberCode = member != null ? member.getMemberCode() : null;
        Long memberId = member != null ? member.getId() : null;

        PaymentItemResolver.PaymentItemSummary summary = itemResolver.summarize(payment);
        String itemDescription = summary != null ? summary.description() : null;

        ActiveSessionDto activeSessionDto = null;
        Optional<SqlQrStore.Session> sessionOpt = store.findLatestByPayment(paymentId);
        if (sessionOpt.isPresent()) {
            SqlQrStore.Session session = sessionOpt.get();
            Instant now = store.now();
            DemoQrService.State effectiveState = session.state();
            if (effectiveState == DemoQrService.State.PENDING && now.isAfter(session.expiresAt())) {
                effectiveState = DemoQrService.State.EXPIRED;
            }
            activeSessionDto = new ActiveSessionDto(
                    session.id(),
                    session.bank(),
                    session.bank().label,
                    effectiveState,
                    session.createdAt(),
                    session.expiresAt(),
                    now
            );
        }

        return new DeskProjectionResponse(
                payment.getId(),
                payment.getPaymentCode(),
                payment.getAmount(),
                "VND",
                payment.getPaymentStatus(),
                itemDescription,
                memberFullName,
                memberCode,
                memberId,
                payment.getSettlementType(),
                payment.getSettlementProvider(),
                payment.getSimulatedTransactionId(),
                activeSessionDto
        );
    }
}
