package com.sportify.payment.controller;

import com.sportify.catalog.dto.PageResponse;
import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.dto.MemberPaymentResponse;
import com.sportify.payment.entity.PaymentStatus;
import com.sportify.payment.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * My Payments and Receipts (F3-03, S3-PaidHistory). Members only: the list always belongs to the signed-in member,
 * so there is no member id parameter to tamper with. The literal path /my wins over GET /payments/{id}.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class MemberPaymentController {

    private final ReceiptService receiptService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('MEMBER')")
    public PageResponse<MemberPaymentResponse> listMyPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + ReceiptService.DEFAULT_PAGE_SIZE) int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return receiptService.listMyPayments(status, page, size, userDetails.getUserAccount());
    }
}
