package com.sportify.payment.controller;

import com.sportify.identity.security.CustomUserDetails;
import com.sportify.payment.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MEMBER','RECEPTIONIST','MANAGER') and principal.enabled")
public class VietQrTransferController {
    private final VietQrTransferService transfers;
    private final VietQrBankDirectory directory;
    private final VietQrCheckoutService checkout;
    @GetMapping("/{id}/vietqr-instructions")
    public VietQrTransferService.Instructions instructions(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor) {
        return transfers.instructions(id,actor.getUserAccount());
    }
    @GetMapping("/vietqr-banks")
    public Map<String,Object> banks() {
        return Map.of("source","https://api.vietqr.io/v2/banks","capturedAt",directory.capturedAt(),
                "fresh",directory.fresh(),"banks",directory.banks());
    }

    @PostMapping("/{id}/vietqr-checkout")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public VietQrCheckoutService.Checkout checkout(@PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails actor) {
        return checkout.checkout(id, actor.getUserAccount());
    }

}
