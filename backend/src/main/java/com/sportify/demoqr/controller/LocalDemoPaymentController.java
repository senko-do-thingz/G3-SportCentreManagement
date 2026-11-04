package com.sportify.demoqr.controller;

import com.sportify.demoqr.dto.LocalDemoCapabilityResponse;
import com.sportify.demoqr.dto.LocalDemoSettlementResponse;
import com.sportify.demoqr.service.DemoQrException;
import com.sportify.demoqr.service.LocalDemoPaymentService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class LocalDemoPaymentController {

    private final ObjectProvider<LocalDemoPaymentService> serviceProvider;

    @GetMapping("/local-demo/capability")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    public LocalDemoCapabilityResponse capability() {
        LocalDemoPaymentService service = serviceProvider.getIfAvailable();
        boolean active = service != null && service.isEnabled();
        return active ? LocalDemoCapabilityResponse.createEnabled() : LocalDemoCapabilityResponse.createDisabled();
    }

    @PostMapping("/{paymentId}/demo-confirm")
    @PreAuthorize("hasRole('MEMBER')")
    public LocalDemoSettlementResponse confirm(
            @PathVariable long paymentId,
            @AuthenticationPrincipal CustomUserDetails user) {
        LocalDemoPaymentService service = serviceProvider.getIfAvailable();
        if (service == null || !service.isEnabled()) {
            throw DemoQrException.unavailable();
        }
        if (user == null || user.getUserAccount() == null) {
            throw DemoQrException.unavailable();
        }
        return service.confirmDemoPayment(paymentId, user.getUserAccount());
    }
}
