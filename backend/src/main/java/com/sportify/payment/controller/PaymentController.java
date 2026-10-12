package com.sportify.payment.controller;

import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.dto.PaymentResponse;
import com.sportify.payment.dto.UpdatePaymentMethodRequest;
import com.sportify.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER', 'ADMIN')")
    public PaymentResponse getPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return paymentService.getPayment(id, userDetails.getUserAccount());
    }

    @PutMapping("/{id}/method")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER', 'ADMIN')")
    public PaymentResponse updatePaymentMethod(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePaymentMethodRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return paymentService.updatePaymentMethod(id, request, userDetails.getUserAccount());
    }
}
