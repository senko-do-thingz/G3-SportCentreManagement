package com.sportify.payment.controller;

import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.dto.InvoiceResponse;
import com.sportify.payment.service.PaymentVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receipts (F3-06). Staff read any invoice; a member reads only the invoices of their own payments.
 */
@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final PaymentVerificationService paymentVerificationService;

    @GetMapping("/by-payment/{paymentId}")
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    public InvoiceResponse getInvoiceByPayment(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return paymentVerificationService.getInvoiceByPayment(paymentId, userDetails.getUserAccount());
    }
}
