package com.sportify.catalog.controller;

import com.sportify.catalog.dto.CheckInResponse;
import com.sportify.catalog.service.MemberCheckInPassService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MemberCheckInPassController {
    private final MemberCheckInPassService passes;
    public record Redeem(@NotBlank @Size(max=128) String token,@Positive Long bookingId) {
        @Override public String toString() { return "CheckInPassRedemption[redacted]"; }
    }
    @PostMapping("/member/check-in-pass")
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public ResponseEntity<MemberCheckInPassService.Pass> issue(@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma","no-cache")
                .body(passes.issue(actor.getUserAccount().getId()));
    }
    @PostMapping("/reception/check-in-pass/redeem")
    @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public ResponseEntity<CheckInResponse> redeem(@Valid @RequestBody Redeem request,@AuthenticationPrincipal CustomUserDetails actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(passes.redeem(request.token(),request.bookingId(),actor.getUserAccount()));
    }
}
