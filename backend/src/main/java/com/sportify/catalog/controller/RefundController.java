package com.sportify.catalog.controller;

import com.sportify.catalog.dto.RefundCreateRequest;
import com.sportify.catalog.dto.RefundResponse;
import com.sportify.catalog.dto.RefundReviewRequest;
import com.sportify.catalog.service.RefundService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MEMBER', 'RECEPTIONIST', 'MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse submitRefund(
            @Valid @RequestBody RefundCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return refundService.submitRefund(request, userDetails.getUserAccount());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('MANAGER')")
    public List<RefundResponse> getPendingRefunds() {
        return refundService.getPendingRefunds();
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasRole('MANAGER')")
    public RefundResponse reviewRefund(
            @PathVariable Long id,
            @Valid @RequestBody RefundReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return refundService.reviewRefund(id, request, userDetails.getUserAccount());
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public RefundResponse completeRefund(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return refundService.completeRefund(id, userDetails.getUserAccount());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('MEMBER')")
    public List<RefundResponse> getMyRefunds(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return refundService.getMyRefunds(userDetails.getUserAccount());
    }

    @GetMapping("/member/{memberId}")
    @PreAuthorize("hasAnyRole('RECEPTIONIST', 'MANAGER')")
    public List<RefundResponse> getMemberRefunds(@PathVariable Long memberId) {
        return refundService.getMemberRefunds(memberId);
    }
}
