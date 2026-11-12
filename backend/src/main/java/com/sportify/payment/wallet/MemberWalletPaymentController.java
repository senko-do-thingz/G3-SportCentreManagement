package com.sportify.payment.wallet;

import com.sportify.core.exception.BusinessRuleException;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** MW-05: pay one of the Member's own pending purchases from the ACTUAL wallet balance. */
@RestController
@RequestMapping("/api/v1/member/wallet/payments")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class MemberWalletPaymentController {

    private final WalletPaymentService service;

    @PostMapping("/{paymentId}")
    public WalletPaymentService.Result pay(@PathVariable long paymentId, @AuthenticationPrincipal CustomUserDetails user) {
        throw new BusinessRuleException("Sportify Wallet has been retired. Please pay using Bank Transfer, Cash, or QR Code.", "WALLET_FEATURE_RETIRED");
    }
}
