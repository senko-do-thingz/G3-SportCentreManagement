package com.sportify.payment.controller;

import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.service.VietQrCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Profile("dev")
@ConditionalOnProperty(prefix = "app.payment.local-demo", name = "enabled", havingValue = "true")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class VietQrLocalSandboxController {
    private final VietQrCheckoutService checkout;

    @PostMapping("/{id}/vietqr-sandbox-callback")
    public VietQrCheckoutService.SandboxCallbackSubmission testCallback(@PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return checkout.submitSandboxCallback(id, actor.getUserAccount());
    }
}
