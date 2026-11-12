package com.sportify.payment.service;

import com.sportify.catalog.service.OperationalMemberPolicy;
import com.sportify.core.exception.BusinessRuleException;
import com.sportify.core.exception.ResourceNotFoundException;
import com.sportify.identity.entity.UserAccount;
import com.sportify.payment.config.BankTransferProperties;
import com.sportify.payment.config.VietQrGatewayProperties;
import com.sportify.payment.entity.Payment;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import com.sportify.demoqr.config.LocalDemoSafetyGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VietQrCheckoutService {

    private final PaymentService payments;
    private final PaymentRepository paymentRepository;
    private final VietQrApiClient api;
    private final VietQrGatewayProperties gateway;
    private final BankTransferProperties beneficiary;
    private final OperationalMemberPolicy operational;
    private final VietQrTransactionSyncService transactionSync;
    private final EntityManager entityManager;
    private final Environment environment;

    public record Checkout(long paymentId, String paymentCode, boolean enabled, boolean sandbox,
                           boolean localSandboxCompletion,
                           String bankCode, String bankName, String accountNumber, String accountName,
                           BigDecimal amount, String currency, String orderId, String transferContent,
                           String qrCode, String qrLink, String notice) {}

    public record SandboxCallbackSubmission(String status, String message) {}

    @Transactional
    public Checkout checkout(long paymentId, UserAccount actor) {
        payments.getPayment(paymentId, actor); // Enforces payment ownership before loading a writable entity.
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
        requireEligiblePayment(payment);
        operational.requireRealMember(payment.getMember().getId());

        if (!gateway.isEnabled()) {
            return new Checkout(paymentId, payment.getPaymentCode(), false, gateway.isSandbox(), false, null,
                    null, null, null, payment.getAmount(), "VND", null, null, null, null,
                    "VietQR checkout is not configured. Ask reception for payment options.");
        }

        String bankAccount = digitsOnly(beneficiary.getAccountNumber());
        String accountName = asciiName(beneficiary.getAccountName());
        String bankCode = gateway.getBankCode().toUpperCase(Locale.ROOT);
        if (!bankAccount.matches("[0-9]{6,19}") || accountName.isBlank() || accountName.length() > 50) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Configure a valid VietQR beneficiary account name and number in backend/.env.");
        }

        if (payment.getVietQrQrCode() == null || payment.getVietQrQrCode().isBlank()) {
            String orderId = payment.getId().toString();
            if (orderId.length() > 13) {
                throw new BusinessRuleException("This payment cannot be represented as a VietQR order ID.", "VIETQR_ORDER_ID_TOO_LONG");
            }
            String content = "SP" + orderId;
            long amount = wholeVnd(payment.getAmount());
            var remote = api.generateQr(new VietQrApiClient.GenerateQrRequest(
                    amount, content, bankAccount, bankCode, accountName, "C", orderId, 0));
            validateGeneratedQr(remote, orderId, content, amount, bankAccount);
            if (remote.qrCode().length() > 1500 || safeQrLink(remote.qrLink()).length() > 2048) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned an oversized payment code.");
            }
            payment.setVietQrOrderId(orderId);
            payment.setVietQrEnvironment(gateway.isSandbox() ? "SANDBOX" : "PRODUCTION");
            payment.setVietQrContent(content);
            payment.setVietQrQrCode(remote.qrCode());
            payment.setVietQrQrLink(safeQrLink(remote.qrLink()));
            paymentRepository.save(payment);
        }

        return new Checkout(paymentId, payment.getPaymentCode(), true, gateway.isSandbox(),
                gateway.isSandbox() && gateway.isLocalCallbackFallback(), bankCode,
                beneficiary.getBank(), bankAccount, accountName, payment.getAmount(), "VND",
                payment.getVietQrOrderId(), payment.getVietQrContent(), payment.getVietQrQrCode(),
                payment.getVietQrQrLink(), gateway.isSandbox()
                ? "VietQR Sandbox. No real money moves in this demo."
                : "Scan this VietQR code. The payment will confirm after VietQR verifies and sends the transaction callback.");
    }

    @Transactional
    public SandboxCallbackSubmission submitSandboxCallback(long paymentId, UserAccount actor) {
        requireLocalSandbox();
        payments.getPayment(paymentId, actor);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS
                && ("GATEWAY".equals(payment.getSettlementProvider())
                    || "LOCAL_DEMO".equals(payment.getSettlementProvider()))
                && "SANDBOX".equals(payment.getVietQrEnvironment())) {
            return new SandboxCallbackSubmission("ALREADY_COMPLETED",
                    "This VietQR Sandbox demo payment is already complete. No real money moved.");
        }
        requireEligiblePayment(payment);
        operational.requireRealMember(payment.getMember().getId());
        if (!gateway.isEnabled() || !gateway.isSandbox()) {
            throw new BusinessRuleException("VietQR sandbox callbacks are not enabled for this environment.", "VIETQR_SANDBOX_CALLBACK_DISABLED");
        }
        if (payment.getVietQrOrderId() == null || payment.getVietQrContent() == null || payment.getVietQrQrCode() == null) {
            throw new BusinessRuleException("Create the VietQR checkout code before simulating a sandbox payment.", "VIETQR_CHECKOUT_NOT_CREATED");
        }
        long amount = wholeVnd(payment.getAmount());
        api.submitSandboxCallback(new VietQrApiClient.TestCallbackRequest(
                digitsOnly(beneficiary.getAccountNumber()), payment.getVietQrContent(), amount,
                "C", gateway.getBankCode().toUpperCase(Locale.ROOT)));
        if (gateway.isLocalCallbackFallback()) {
            Payment current = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found for id: " + paymentId));
            entityManager.refresh(current, LockModeType.PESSIMISTIC_WRITE);
            if (current.getPaymentStatus() == PaymentStatus.PENDING) {
                long now = System.currentTimeMillis();
                String localTransactionId = "LOCAL-SANDBOX-" + paymentId + "-" + UUID.randomUUID();
                String localReference = "LOCAL-SANDBOX-" + UUID.randomUUID();
                transactionSync.accept(new VietQrTransactionCallback(
                        digitsOnly(beneficiary.getAccountNumber()), BigDecimal.valueOf(amount), "C",
                        current.getVietQrContent(), localTransactionId, now, localReference,
                        current.getVietQrOrderId(), null, null, null, null, null));
                return new SandboxCallbackSubmission("LOCAL_COMPLETED",
                        "VietQR accepted the sandbox test request. This local demo is marked paid without moving real money.");
            }
            if (current.getPaymentStatus() == PaymentStatus.SUCCESS
                    && ("GATEWAY".equals(current.getSettlementProvider())
                        || "LOCAL_DEMO".equals(current.getSettlementProvider()))
                    && "SANDBOX".equals(current.getVietQrEnvironment())) {
                return new SandboxCallbackSubmission("ALREADY_COMPLETED",
                        "VietQR accepted the sandbox test request and its callback already completed this demo payment.");
            }
            throw new BusinessRuleException("Payment changed while VietQR processed the sandbox test request. Refresh the payment status.",
                    "VIETQR_PAYMENT_NOT_AVAILABLE");
        }
        return new SandboxCallbackSubmission("SUBMITTED",
                "VietQR accepted the sandbox callback request. The payment updates after VietQR reaches the configured callback URL.");
    }

    private void requireLocalSandbox() {
        boolean enabled = environment.matchesProfiles("dev")
                && "true".equalsIgnoreCase(environment.getProperty("app.payment.local-demo.enabled",
                        environment.getProperty("PAYMENT_LOCAL_DEMO_ENABLED", "false")));
        if (!gateway.isSandbox() || !enabled) {
            throw new BusinessRuleException("Local sandbox settlement is disabled.", "VIETQR_SANDBOX_CALLBACK_DISABLED");
        }
        LocalDemoSafetyGuard.validateLocalDemoSafety(environment);
    }

    private static void requireEligiblePayment(Payment payment) {
        if (payment.getPaymentStatus() != PaymentStatus.PENDING || payment.getPaymentMethod() != PaymentMethod.BANK_TRANSFER
                || "SIMULATED".equals(payment.getSettlementType()) || payment.getSimulatedTransactionId() != null
                || "SPORTIFY_SIMULATOR".equals(payment.getSettlementProvider())) {
            throw new BusinessRuleException("Only pending real bank-transfer payments can use VietQR checkout.", "VIETQR_PAYMENT_NOT_AVAILABLE");
        }
    }

    private static long wholeVnd(BigDecimal amount) {
        try {
            if (amount == null || amount.signum() <= 0) throw new ArithmeticException("non-positive amount");
            return amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException invalid) {
            throw new BusinessRuleException("VietQR requires a positive whole-number VND amount.", "VIETQR_AMOUNT_INVALID");
        }
    }

    private static void validateGeneratedQr(VietQrApiClient.GenerateQrResponse response, String orderId,
                                            String content, long amount, String accountNumber) {
        if (response == null || response.qrCode() == null || response.qrCode().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR did not return a payment QR code.");
        }
        if (response.orderId() != null && !response.orderId().isBlank() && !orderId.equals(response.orderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned a payment code for a different order.");
        }
        if (response.content() != null && !content.equals(response.content())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned different payment instructions.");
        }
        if (response.bankAccount() != null && !response.bankAccount().isBlank()
                && !digitsOnly(accountNumber).equals(digitsOnly(response.bankAccount()))) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned a different beneficiary account.");
        }
        try {
            if (response.amount() != null && new BigDecimal(response.amount()).compareTo(BigDecimal.valueOf(amount)) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned a different payment amount.");
            }
        } catch (NumberFormatException invalidAmount) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "VietQR returned an invalid payment amount.");
        }
    }

    static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String asciiName(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized;
    }

    private static String safeQrLink(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && "pro.vietqr.vn".equalsIgnoreCase(uri.getHost())
                    ? value : "";
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }
}
