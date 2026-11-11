package com.sportify.payment.wallet;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.wallet.WalletTopUpService.ConfirmRequest;
import com.sportify.payment.wallet.WalletTopUpService.CreateRequest;
import com.sportify.payment.wallet.WalletTopUpService.MemberTopUps;
import com.sportify.payment.wallet.WalletTopUpService.RejectRequest;
import com.sportify.payment.wallet.WalletTopUpService.TopUp;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** MW-04: Members request top-ups for their own wallet; Receptionists confirm or reject them after checking the bank. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WalletTopUpController {

    private final WalletTopUpService service;

    @GetMapping("/member/wallet/topups")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public MemberTopUps mine(@AuthenticationPrincipal CustomUserDetails user) {
        return service.mine(user.getUserAccount().getId());
    }

    @PostMapping("/member/wallet/topups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public TopUp create(@RequestBody CreateRequest request,
                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                        @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet top-up is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @GetMapping("/member/wallet/topups/{id}")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public TopUp detail(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.memberDetail(user.getUserAccount().getId(), id);
    }

    @PostMapping("/member/wallet/topups/{id}/cancel")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public TopUp cancel(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet top-up cancellation is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @GetMapping("/reception/wallet/topups")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public List<TopUp> queue(@RequestParam(required = false) String status) {
        return service.staffList(status);
    }

    @GetMapping("/reception/wallet/topups/{id}")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public TopUp staffDetail(@PathVariable long id) {
        return service.staffDetail(id);
    }

    @PostMapping("/reception/wallet/topups/{id}/confirm")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public TopUp confirm(@PathVariable long id, @RequestBody ConfirmRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet top-up confirmation is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }

    @PostMapping("/reception/wallet/topups/{id}/reject")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public TopUp reject(@PathVariable long id, @RequestBody RejectRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Wallet top-up rejection is no longer supported as the Wallet feature has been retired.", "WALLET_FEATURE_RETIRED");
    }
}
