package com.sportify.payment.controller;

import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.service.PayOsCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PayOsCheckoutController {
    private final PayOsCheckoutService checkout;

    @PostMapping("/{id}/payos-checkout")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public PayOsCheckoutService.Checkout checkout(@PathVariable long id,
                                                   @AuthenticationPrincipal CustomUserDetails actor) {
        return checkout.checkout(id, actor.getUserAccount());
    }

    @PostMapping("/{id}/payos-sync")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public PayOsCheckoutService.StatusSync syncStatus(@PathVariable long id,
                                                       @AuthenticationPrincipal CustomUserDetails actor) {
        return checkout.syncStatus(id, actor.getUserAccount());
    }
}
