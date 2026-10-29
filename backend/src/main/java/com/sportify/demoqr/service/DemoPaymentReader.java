package com.sportify.demoqr.service;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.SecurityUtils;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.entity.Payment;
import org.springframework.transaction.annotation.Propagation;
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
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class DemoPaymentReader {
    private final PaymentRepository payments;
    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;

    public record Snapshot(long paymentId, BigDecimal amount, String currency) { }

    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = DemoQrException.class)
    public Payment requireOwner(long paymentId, UserAccount actor) {
        var payment = payments.findByIdForUpdate(paymentId).orElseThrow(DemoQrException::unavailable);
        if (payment.getMember() == null || !payment.getMember().getId().equals(actor.getId())) throw DemoQrException.unavailable();
        return payment;
    }

    // The caller commits session revocation on an expected eligibility denial. SQL failures still roll back.
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = DemoQrException.class)
    public Snapshot read(long paymentId, UserAccount actor) {
        if (paymentId <= 0 || !SecurityUtils.isMember(actor) || !"ACTIVE".equals(actor.getStatus())) {
            throw DemoQrException.unavailable();
        }
        var payment = requireOwner(paymentId, actor);
        if (payment.getMember() == null || !actor.getId().equals(payment.getMember().getId())
                || payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getAmount() == null
                || payment.getAmount().signum() <= 0) throw DemoQrException.unavailable();
        var card = cards.findByPaymentId(paymentId);
        var registration = registrations.findByPaymentId(paymentId);
        if (card.isPresent() == registration.isPresent()) throw DemoQrException.unavailable();
        card.ifPresent(item -> {
            if (item.getStatus() != CardStatus.PENDING_PAYMENT || item.getMember() == null
                    || !actor.getId().equals(item.getMember().getId())) throw DemoQrException.unavailable();
        });
        registration.ifPresent(item -> {
            if (item.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT || item.getMember() == null
                    || !actor.getId().equals(item.getMember().getId())) throw DemoQrException.unavailable();
        });
        return new Snapshot(paymentId, payment.getAmount(), "VND");
    }
}
