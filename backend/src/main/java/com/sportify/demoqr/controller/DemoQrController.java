package com.sportify.demoqr.controller;

import com.sportify.demoqr.service.DemoQrException;
import com.sportify.demoqr.service.DemoQrService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/simulated-qr/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MEMBER')")
@Profile("qr-simulator & !prod & !production")
@ConditionalOnProperty(name = "app.payment.simulator.enabled", havingValue = "true")
public class DemoQrController {
    private final DemoQrService service;

    public record CreateRequest(@Positive long paymentId, @NotNull DemoQrService.Bank bankCode, @NotNull java.util.UUID requestId) { }
    public record TokenRequest(String token) {
        @Override public String toString() { return "TokenRequest[REDACTED]"; }
    }

    @PostMapping
    public DemoQrService.Created create(@Valid @RequestBody CreateRequest request,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        return service.create(request.paymentId(), request.bankCode(), request.requestId(), user.getUserAccount());
    }

    @GetMapping("/{id}")
    public DemoQrService.View get(@PathVariable String id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.get(id, user.getUserAccount());
    }

    @PostMapping("/preview")
    public DemoQrService.View preview(@RequestBody TokenRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.preview(request.token(), user.getUserAccount());
    }

    @PostMapping("/confirm-intent")
    public DemoQrService.View confirmIntent(@RequestBody TokenRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        throw new DemoQrException(HttpStatus.CONFLICT, "SIMULATOR_CONFIRM_ENDPOINT_REQUIRED",
                "Legacy intent-only confirmation is retired because QR simulation now finalizes payment as Paid - Simulated. Refresh and use the explicit /confirm endpoint.");
    }

    @PostMapping("/confirm")
    public DemoQrService.View confirm(@RequestBody TokenRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        return service.confirm(request.token(), user.getUserAccount());
    }

    @PostMapping("/{id}/cancel")
    public DemoQrService.View cancel(@PathVariable String id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.cancel(id, user.getUserAccount());
    }
}
