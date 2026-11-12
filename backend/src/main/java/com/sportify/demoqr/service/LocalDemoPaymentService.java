package com.sportify.demoqr.service;

import com.sportify.core.common.SecurityUtils;
import com.sportify.demoqr.config.LocalDemoSafetyGuard;
import com.sportify.demoqr.config.SimulatedSettlementEnabledCondition;
import com.sportify.demoqr.config.UnifiedDemoSafetyGuard;
import com.sportify.demoqr.dto.LocalDemoSettlementResponse;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.Invoice;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating single-operation localhost QR simulated settlements.
 * Reuses DemoPaymentReader domain settlement and SqlQrStore V35 transactional audit rows.
 */
@Slf4j
@Service
@Conditional(SimulatedSettlementEnabledCondition.class)
@RequiredArgsConstructor
public class LocalDemoPaymentService {

    private final PaymentRepository paymentRepository;
    private final DemoPaymentReader demoPaymentReader;
    private final SqlQrStore sqlQrStore;
    private final InvoiceRepository invoiceRepository;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();

    public boolean isEnabled() {
        try {
            if (environment == null) {
                return false;
            }
            if (UnifiedDemoSafetyGuard.requested(environment)) {
                return UnifiedDemoSafetyGuard.isEnabled(environment);
            }
            if (environment.matchesProfiles("prod") || environment.matchesProfiles("production")) {
                return false;
            }
            boolean profileActive = environment.matchesProfiles("local-demo") || environment.matchesProfiles("dev");
            String flag = environment.getProperty("app.payment.local-demo.enabled");
            if (flag == null || flag.isBlank()) {
                flag = environment.getProperty("PAYMENT_LOCAL_DEMO_ENABLED");
            }
            boolean flagActive = "true".equalsIgnoreCase(flag);
            if (!profileActive || !flagActive) {
                return false;
            }
            LocalDemoSafetyGuard.validateLocalDemoSafety(environment);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public LocalDemoSettlementResponse confirmDemoPayment(long paymentId, UserAccount actor) {
        if (!isEnabled()) {
            throw DemoQrException.unavailable();
        }
        if (actor == null || !SecurityUtils.isMember(actor) || actor.getId() == null || !"ACTIVE".equals(actor.getStatus())) {
            throw DemoQrException.unavailable();
        }
        if (paymentId <= 0) {
            throw DemoQrException.unavailable();
        }
        boolean unified = UnifiedDemoSafetyGuard.isEnabled(environment);
        if (unified && (!UnifiedDemoSafetyGuard.memberIds(environment).contains(actor.getId())
                || actor.getEmail() == null || !actor.getEmail().matches("[a-z0-9._+-]+@unified-demo\\.invalid"))) {
            throw DemoQrException.unavailable();
        }

        if (!sqlQrStore.spend(actor.getId(), true)) {
            throw new DemoQrException(HttpStatus.TOO_MANY_REQUESTS, "SIMULATOR_RATE_LIMIT",
                    "Simulation rate limit reached. Retry after 60 seconds.");
        }

        return sqlQrStore.transaction(() -> {
            try {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(DemoQrException::unavailable);

            if (payment.getMember() == null || !actor.getId().equals(payment.getMember().getId())) {
                throw DemoQrException.unavailable();
            }

            // The member-facing local completion action represents a bank-transfer QR only.
            // Cash stays pending until reception confirms that cash was actually received.
            if (payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
                throw DemoQrException.unavailable();
            }

            // SUCCESS describes the persisted payment, not current benefit availability. Replay never activates an item.
            if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
                if ("SIMULATED".equals(payment.getSettlementType())
                        && "SPORTIFY_SIMULATOR".equals(payment.getSettlementProvider())) {
                    List<Invoice> invoices = invoiceRepository.findByPaymentId(paymentId);
                    if (unified) {
                        sqlQrStore.requireUnifiedReplay(paymentId, actor.getId());
                    }
                    Invoice invoice = invoices.isEmpty() ? null : invoices.get(0);
                    Long invoiceId = invoice != null ? invoice.getId() : null;
                    String invoiceNumber = invoice != null ? invoice.getInvoiceNumber() : null;
                    return new LocalDemoSettlementResponse(
                            payment.getId(),
                            "SUCCESS",
                            "SIMULATED",
                            "SPORTIFY_SIMULATOR",
                            payment.getSimulatedTransactionId(),
                            payment.getReferenceCode(),
                            invoiceId,
                            invoiceNumber
                    );
                } else {
                    throw new DemoQrException(HttpStatus.CONFLICT, "SIMULATOR_ALREADY_SETTLED",
                            "Payment is already settled.");
                }
            }

            if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
                throw DemoQrException.unavailable();
            }

            if (unified) {
                demoPaymentReader.validateUnifiedPurchase(paymentId, actor);
            }

            if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
                throw DemoQrException.unavailable();
            }

            // Verify and validate associated item (card or package registration)
            demoPaymentReader.read(paymentId, actor);

            // Cancel any prior active sessions for this payment to satisfy triggers
            sqlQrStore.replace(paymentId, actor.getId());

            byte[] tokenBytes = new byte[32];
            random.nextBytes(tokenBytes);
            String syntheticToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
            String verifier = DemoQrService.digest(syntheticToken);
            UUID requestId = UUID.randomUUID();

            UUID sessionId = sqlQrStore.insert(
                    paymentId,
                    actor.getId(),
                    payment.getAmount(),
                    DemoQrService.Bank.VIETCOMBANK_DEMO,
                    verifier,
                    requestId,
                    300
            );

            SqlQrStore.Session session = sqlQrStore.findId(sessionId, actor.getId());

            // Insert simulated transaction into dbo.simulated_payment_transaction
            SqlQrStore.SimulatedTx tx = sqlQrStore.insertSettlement(session);

            // Settle payment, activate entitlement, create invoice and write activity log
            DemoPaymentReader.SettlementOutcome outcome = demoPaymentReader.settle(
                    paymentId,
                    actor,
                    DemoQrService.Bank.VIETCOMBANK_DEMO.name(),
                    tx.id(),
                    tx.transactionCode()
            );

            if (outcome == null || outcome.invoiceId() == null || outcome.invoiceNumber() == null
                    || outcome.invoiceNumber().isBlank()) {
                throw new IllegalStateException("Simulated settlement requires a persisted PAID invoice");
            }

            // Transition session to SETTLED
            sqlQrStore.settle(session);

            return new LocalDemoSettlementResponse(
                    payment.getId(),
                    "SUCCESS",
                    "SIMULATED",
                    "SPORTIFY_SIMULATOR",
                    tx.id(),
                    tx.transactionCode(),
                    outcome.invoiceId(),
                    outcome.invoiceNumber()
            );
            } catch (Throwable t) {
                log.error("Local demo confirm failure for paymentId={}: {}", paymentId, t.getMessage(), t);
                throw t;
            }
        });
    }
}
