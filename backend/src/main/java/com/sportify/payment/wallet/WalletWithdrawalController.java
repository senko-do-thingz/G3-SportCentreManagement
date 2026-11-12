package com.sportify.payment.wallet;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.wallet.WalletWithdrawalService.CompleteRequest;
import com.sportify.payment.wallet.WalletWithdrawalService.CreateRequest;
import com.sportify.payment.wallet.WalletWithdrawalService.DecisionRequest;
import com.sportify.payment.wallet.WalletWithdrawalService.PayoutAccount;
import com.sportify.payment.wallet.WalletWithdrawalService.PayoutAccountRequest;
import com.sportify.payment.wallet.WalletWithdrawalService.Withdrawal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** MW-06: Members manage their payout account and request withdrawals; Receptionists review and settle them. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WalletWithdrawalController {

    private final WalletWithdrawalService service;

    @GetMapping("/member/wallet/payout-account")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public ResponseEntity<PayoutAccount> payoutAccount(@AuthenticationPrincipal CustomUserDetails user) {
        PayoutAccount account = service.payoutAccount(user.getUserAccount().getId());
        return account == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(account);
    }

    @PutMapping("/member/wallet/payout-account")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public PayoutAccount savePayoutAccount(@RequestBody PayoutAccountRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet payout account management is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @GetMapping("/member/wallet/withdrawals")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public List<Withdrawal> mine(@AuthenticationPrincipal CustomUserDetails user) {
        return service.mine(user.getUserAccount().getId());
    }

    @PostMapping("/member/wallet/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public Withdrawal create(@RequestBody CreateRequest request,
                             @RequestHeader(value = "Idempotency-Key", required = false) String key,
                             @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet withdrawal is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @GetMapping("/reception/wallet/withdrawals")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public List<Withdrawal> queue(@RequestParam(required = false) String status) {
        return service.staffList(status);
    }

    @GetMapping("/reception/wallet/withdrawals/{id}")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public Withdrawal detail(@PathVariable long id) {
        return service.staffDetail(id);
    }

    @PostMapping("/reception/wallet/withdrawals/{id}/approve")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public Withdrawal approve(@PathVariable long id, @RequestBody(required = false) DecisionRequest request,
                              @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet withdrawal approval is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @PostMapping("/reception/wallet/withdrawals/{id}/reject")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public Withdrawal reject(@PathVariable long id, @RequestBody DecisionRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet withdrawal rejection is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @PostMapping("/reception/wallet/withdrawals/{id}/complete")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public Withdrawal complete(@PathVariable long id, @RequestBody(required = false) CompleteRequest request,
                               @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet withdrawal completion is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @PostMapping("/reception/wallet/withdrawals/{id}/fail")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public Withdrawal fail(@PathVariable long id, @RequestBody DecisionRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet withdrawal processing is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }
}
