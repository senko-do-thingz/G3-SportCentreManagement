package com.sportify.demoqr.service;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.SecurityUtils;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** This boundary has reads only. No payment service or financial mutation is injected. */
@Service
@RequiredArgsConstructor
@Profile("(dev | qr-demo) & !prod & !production")
@ConditionalOnProperty(name = "app.demo.qr.enabled", havingValue = "true")
public class DemoPaymentReader {
    private final PaymentRepository payments;
    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;

    public record Snapshot(long paymentId, BigDecimal amount) { }

    @Transactional(readOnly = true)
    public Snapshot read(long paymentId, UserAccount actor) {
        if (paymentId <= 0 || !SecurityUtils.isMember(actor) || !"ACTIVE".equals(actor.getStatus())) {
            throw DemoQrException.unavailable();
        }
        var payment = payments.findById(paymentId).orElseThrow(DemoQrException::unavailable);
        if (payment.getMember() == null || !actor.getId().equals(payment.getMember().getId())
                || payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getAmount() == null
                || payment.getAmount().signum() <= 0) throw DemoQrException.unavailable();
        cards.findByPaymentId(paymentId).ifPresent(card -> {
            if (card.getStatus() != CardStatus.PENDING_PAYMENT) throw DemoQrException.unavailable();
        });
        registrations.findByPaymentId(paymentId).ifPresent(registration -> {
            if (registration.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT) throw DemoQrException.unavailable();
        });
        return new Snapshot(paymentId, payment.getAmount());
    }
}
