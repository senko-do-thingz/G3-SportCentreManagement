package com.sportify.catalog.controller;

import com.sportify.catalog.service.ReceptionSupportService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor @Validated
public class ReceptionSupportController {
    private final ReceptionSupportService support;
    @GetMapping("/reception/support-requests") @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public List<ReceptionSupportService.Ticket> list(@RequestParam(required=false) @Positive Long memberId,@RequestParam(required=false) ReceptionSupportService.Status status,@AuthenticationPrincipal CustomUserDetails actor) {
        return support.staff(actor.getUserAccount().getId(),memberId,status);
    }
    @GetMapping("/reception/support-requests/{id}") @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public ReceptionSupportService.Ticket detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){return support.detail(actor.getUserAccount().getId(),id);}
    @PostMapping("/reception/support-requests") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public ReceptionSupportService.Ticket create(@Valid @RequestBody ReceptionSupportService.Intake request,@RequestHeader("Idempotency-Key") UUID operation,@AuthenticationPrincipal CustomUserDetails actor){return support.create(actor.getUserAccount().getId(),request,operation);}
    @PutMapping("/reception/support-requests/{id}/status") @PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
    public ReceptionSupportService.Ticket update(@PathVariable long id,@Valid @RequestBody ReceptionSupportService.Update request,@AuthenticationPrincipal CustomUserDetails actor){return support.update(actor.getUserAccount().getId(),id,request);}
    @GetMapping("/member/support-requests") @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public List<ReceptionSupportService.Ticket> member(@AuthenticationPrincipal CustomUserDetails actor){return support.member(actor.getUserAccount().getId());}
}
