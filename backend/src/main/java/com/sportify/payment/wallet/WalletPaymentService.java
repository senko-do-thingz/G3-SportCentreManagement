package com.sportify.payment.wallet;

import com.sportify.catalog.service.MembershipCardService;
import com.sportify.catalog.service.OperationalMemberPolicy;
import com.sportify.catalog.service.SportPackageService;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ConflictException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.entity.InvoiceItemType;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.InvoiceRepository;
import com.sportify.payment.repository.PaymentRepository;
import com.sportify.payment.service.PaymentVerificationService;
import com.sportify.payment.service.impl.PaymentItemResolver;
import com.sportify.payment.wallet.WalletLedgerService.EntryType;
import com.sportify.payment.wallet.WalletLedgerService.Operation;
import com.sportify.payment.wallet.WalletLedgerService.Pool;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MW-05: settle one of the Member's own PENDING purchases (membership card or sport package) from the ACTUAL wallet
 * balance. Debit, item activation, payment SUCCESS and invoice are one transaction, so an insufficient balance or any
 * activation error leaves no debit and no entitlement. A replay of an already wallet-settled payment returns it again
 * without debiting twice. Booking a session still uses package credits; this service never sells a seat.
 */
@Service
@RequiredArgsConstructor
public class WalletPaymentService {

    public record Result(long paymentId, String paymentCode, String status, String method, BigDecimal amount,
                         long ledgerEntryId, BigDecimal availableAfter, Long invoiceId, String invoiceNumber, boolean replay) {}

    private final PaymentRepository payments;
    private final PaymentItemResolver items;
    private final MembershipCardService cards;
    private final SportPackageService packages;
    private final PaymentVerificationService verification;
    private final InvoiceRepository invoices;
    private final WalletLedgerService ledger;
    private final OperationalMemberPolicy operational;
    private final WalletPendingOperations pending;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /**
     * Lock order follows the CODEOWNER wallet rule: the member_wallet row first, then the payment row (the same row
     * staff confirmation locks, so the two cannot both settle it), then the item and account inside activation. A wallet
     * purchase completes in one transaction, so it claims no slot but refuses while another wallet operation (a pending
     * top-up or withdrawal) is in flight.
     */
    @Transactional
    public Result pay(UserAccount member, long paymentId) {
        ledger.ensureWallet(member.getId());
        pending.lockWallet(member.getId());
        Payment payment = payments.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (payment.getMember() == null || !member.getId().equals(payment.getMember().getId())) {
            throw new ResourceNotFoundException("Payment not found");
        }
        List<Long> settled = jdbc.queryForList("SELECT ledger_entry_id FROM dbo.payment_wallet_settlement WHERE payment_id=?",
                Long.class, paymentId);
        if (!settled.isEmpty()) {
            return result(payment, settled.getFirst(), true);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new ConflictException("Payment " + payment.getPaymentCode() + " is already " + payment.getPaymentStatus(),
                    "PAYMENT_NOT_PENDING");
        }
        if ("SIMULATED".equals(payment.getSettlementType()) || payment.getSimulatedTransactionId() != null) {
            throw new BusinessRuleException("A simulated payment cannot be settled from the wallet", "WALLET_PAYMENT_NOT_ELIGIBLE");
        }
        pending.requireNone(member.getId());
        operational.requireRealMember(member.getId());
        var item = items.findItem(payment).orElseThrow(() -> new BusinessRuleException("Payment " + payment.getPaymentCode()
                + " is not linked to a membership card or a package registration", "WALLET_PAYMENT_NOT_ELIGIBLE"));

        var debit = ledger.apply(new Operation(member.getId(), EntryType.PURCHASE_DEBIT, Pool.ACTUAL, payment.getAmount(),
                "PAY-" + paymentId, "PAYMENT", paymentId, "Payment " + payment.getPaymentCode() + " - " + item.code(), member.getId()));
        payment.setReferenceCode("WALLET-" + debit.entry().id());
        // Activation recomputes dates and marks the payment SUCCESS with method WALLET; confirmedBy stays empty
        // because no staff member confirmed it.
        if (item.type() == InvoiceItemType.MEMBERSHIP_CARD) {
            cards.activateCardForPaymentConfirmation(item.id(), null, PaymentMethod.WALLET);
        } else {
            packages.activateRegistrationForPaymentConfirmation(item.id(), null, PaymentMethod.WALLET);
        }
        verification.recordPaymentConfirmed(paymentId, member, "Paid from Sportify Wallet");
        jdbc.update("INSERT INTO dbo.payment_wallet_settlement (payment_id, ledger_entry_id, created_at) VALUES (?,?,?)",
                paymentId, debit.entry().id(), Timestamp.valueOf(LocalDateTime.now(clock)));
        payments.flush();
        return result(payments.findById(paymentId).orElseThrow(), debit.entry().id(), false);
    }

    private Result result(Payment payment, long ledgerEntryId, boolean replay) {
        var invoice = invoices.findByPaymentId(payment.getId()).stream().findFirst();
        BigDecimal after = jdbc.queryForObject("SELECT available_after FROM dbo.wallet_ledger_entry WHERE id=?", BigDecimal.class, ledgerEntryId);
        return new Result(payment.getId(), payment.getPaymentCode(), payment.getPaymentStatus().name(),
                payment.getPaymentMethod().name(), payment.getAmount(), ledgerEntryId, after,
                invoice.map(i -> i.getId()).orElse(null), invoice.map(i -> i.getInvoiceNumber()).orElse(null), replay);
    }
}
