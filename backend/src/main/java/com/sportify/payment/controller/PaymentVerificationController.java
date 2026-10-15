package com.sportify.payment.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.dto.ConfirmPaymentRequest;
import com.sportify.payment.dto.FailPaymentRequest;
import com.sportify.payment.dto.StaffPaymentResponse;
import com.sportify.payment.entity.PaymentMethod;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.PaymentVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Front desk payment verification (F3-04, F3-05, F3-06, S3-Mismatch). Staff only.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentVerificationController {

    private final PaymentVerificationService paymentVerificationService;

    /**
     * Payment requests, PENDING first. {@code search} matches member name, member code, payment code and
     * reference code; {@code query} is accepted as another name for it (front desk screen FE-12).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public PageResponse<StaffPaymentResponse> listPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + PaymentVerificationService.DEFAULT_PAGE_SIZE) int size) {
        String text = search != null && !search.isBlank() ? search : query;
        return paymentVerificationService.listPayments(status, method, text, from, to, page, size);
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public StaffPaymentResponse confirmPayment(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return paymentVerificationService.confirmPayment(id, request, userDetails.getUserAccount());
    }

    @PutMapping("/{id}/fail")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public StaffPaymentResponse failPayment(
            @PathVariable Long id,
            @Valid @RequestBody FailPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return paymentVerificationService.failPayment(id, request, userDetails.getUserAccount());
    }
}
