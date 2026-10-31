package com.sportify.demoqr.service;

import com.sportify.catalog.entity.CardStatus;
import com.sportify.catalog.entity.MemberCard;
import com.sportify.catalog.entity.PackageRegistrationStatus;
import com.sportify.catalog.entity.SportPackageRegistration;
import com.sportify.catalog.repository.MemberCardRepository;
import com.sportify.catalog.repository.SportPackageRegistrationRepository;
import com.sportify.core.common.SecurityUtils;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentVerificationService;
import com.sportify.payment.service.impl.PaymentNotes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Payment domain boundary for the QR payment simulator: locks Payment first, validates ownership and item eligibility,
 * and performs atomic simulated settlement (Payment SUCCESS + SIMULATED provenance, item activation, PAID invoice,
 * and PAYMENT_CONFIRMED activity log) without creating actual cash collection tickets or cash_event rows.
 */
@Service
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class DemoPaymentReader {
    private final PaymentRepository payments;
    private final MemberCardRepository cards;
    private final SportPackageRegistrationRepository registrations;
    private final PaymentVerificationService verificationService;
    private final ObjectProvider<Clock> clock;
    private final ObjectProvider<EntityManager> entityManager;

    public DemoPaymentReader(PaymentRepository payments,
                             MemberCardRepository cards,
                             SportPackageRegistrationRepository registrations,
                             PaymentVerificationService verificationService) {
        this(payments, cards, registrations, verificationService, null, null);
    }

    @Autowired
    public DemoPaymentReader(PaymentRepository payments,
                             MemberCardRepository cards,
                             SportPackageRegistrationRepository registrations,
                             PaymentVerificationService verificationService,
                             ObjectProvider<Clock> clock,
                             ObjectProvider<EntityManager> entityManager) {
        this.payments = Objects.requireNonNull(payments, "PaymentRepository is required");
        this.cards = Objects.requireNonNull(cards, "MemberCardRepository is required");
        this.registrations = Objects.requireNonNull(registrations, "SportPackageRegistrationRepository is required");
        this.verificationService = Objects.requireNonNull(
                verificationService, "PaymentVerificationService is required for simulated QR settlement");
        this.clock = clock;
        this.entityManager = entityManager;
    }

    public record Snapshot(long paymentId, BigDecimal amount, String currency) { }

    public record SettlementOutcome(Long invoiceId, String invoiceNumber) { }

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
                    || !actor.getId().equals(item.getMember().getId())
                    || (item.getPricePaid() != null && item.getPricePaid().compareTo(payment.getAmount()) != 0)) {
                throw DemoQrException.unavailable();
            }
        });
        registration.ifPresent(item -> {
            if (item.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT || item.getMember() == null
                    || !actor.getId().equals(item.getMember().getId())
                    || (item.getPaidAmount() != null && item.getPaidAmount().compareTo(payment.getAmount()) != 0)) {
                throw DemoQrException.unavailable();
            }
        });
        return new Snapshot(paymentId, payment.getAmount(), "VND");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public SettlementOutcome settle(long paymentId, UserAccount actor, String bankCode,
                                    long simulatedTxId, String transactionCode) {
        if (paymentId <= 0 || !SecurityUtils.isMember(actor) || !"ACTIVE".equals(actor.getStatus())) {
            throw DemoQrException.unavailable();
        }
        var payment = requireOwner(paymentId, actor);
        if (payment.getPaymentStatus() != PaymentStatus.PENDING
                || payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw DemoQrException.unavailable();
        }
        Clock activeClock = clock != null && clock.getIfAvailable() != null
                ? clock.getIfAvailable() : Clock.systemUTC();
        LocalDate today = LocalDate.now(activeClock);
        LocalDateTime now = LocalDateTime.now(activeClock);
        EntityManager em = entityManager != null ? entityManager.getIfAvailable() : null;

        var cardOpt = cards.findByPaymentId(paymentId);
        var regOpt = registrations.findByPaymentId(paymentId);
        if (cardOpt.isPresent() == regOpt.isPresent()) {
            throw DemoQrException.unavailable();
        }

        if (cardOpt.isPresent()) {
            MemberCard card = cardOpt.get();
            if (em != null && em.contains(card)) {
                em.refresh(card, LockModeType.PESSIMISTIC_WRITE);
            }
            if (card.getStatus() != CardStatus.PENDING_PAYMENT
                    || card.getMember() == null || !actor.getId().equals(card.getMember().getId())) {
                throw DemoQrException.unavailable();
            }
            if (card.getStartDate() == null || card.getStartDate().isBefore(today)) {
                card.setStartDate(today);
            }
            Integer months = card.getTier() != null ? card.getTier().getDurationMonths() : null;
            if (months != null && months > 0 && card.getEndDate() == null) {
                card.setEndDate(card.getStartDate().plusMonths(months).minusDays(1));
            }
            if (card.isUpgradeRequest() && card.getReplacesCard() != null) {
                MemberCard replaced = card.getReplacesCard();
                if (em != null && em.contains(replaced)) {
                    em.refresh(replaced, LockModeType.PESSIMISTIC_WRITE);
                }
                if (replaced.getStatus() != CardStatus.ACTIVE) {
                    throw DemoQrException.unavailable();
                }
                replaced.setStatus(CardStatus.REPLACED);
                cards.save(replaced);
            }
            card.setStatus(CardStatus.ACTIVE);
            cards.save(card);
        } else {
            SportPackageRegistration reg = regOpt.get();
            if (em != null && em.contains(reg)) {
                em.refresh(reg, LockModeType.PESSIMISTIC_WRITE);
            }
            if (reg.getStatus() != PackageRegistrationStatus.PENDING_PAYMENT
                    || reg.getMember() == null || !actor.getId().equals(reg.getMember().getId())) {
                throw DemoQrException.unavailable();
            }
            if (reg.getStartDate() == null || reg.getStartDate().isBefore(today)) {
                reg.setStartDate(today);
                Integer days = reg.getSportPackage() != null ? reg.getSportPackage().getDurationDays() : null;
                if (days != null && days > 0) {
                    reg.setEndDate(today.plusDays(days - 1L));
                }
            }
            reg.setStatus(PackageRegistrationStatus.ACTIVE);
            reg.setActivatedAt(now);
            registrations.save(reg);
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setPaymentTime(now);
        payment.setReferenceCode(transactionCode);
        payment.setSettlementType("SIMULATED");
        payment.setSettlementProvider("SPORTIFY_SIMULATOR");
        payment.setSimulatedTransactionId(simulatedTxId);
        String simNote = "Simulated QR settlement " + transactionCode + " via " + bankCode
                + " confirmed by member user id " + actor.getId();
        payment.setNotes(PaymentNotes.append(payment.getNotes(), simNote));
        payments.saveAndFlush(payment);

        Invoice invoice = verificationService.recordPaymentConfirmed(payment.getId(), actor,
                "Simulated QR settlement " + transactionCode + " (" + bankCode + ")");
        if (invoice == null || invoice.getId() == null
                || invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            throw new IllegalStateException("Simulated settlement requires a persisted PAID invoice and audit record");
        }
        payments.flush();
        if (em != null) {
            em.flush();
        }
        return new SettlementOutcome(invoice.getId(), invoice.getInvoiceNumber());
    }
}
