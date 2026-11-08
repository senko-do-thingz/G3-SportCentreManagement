package com.sportify.catalog.controller;

import com.sportify.catalog.service.ReceptionSupportService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1") @RequiredArgsConstructor @Validated
public class ReceptionSupportController {
    private static final String RECEPTIONIST="hasRole('RECEPTIONIST') and principal.enabled";
    private static final String MEMBER="hasRole('MEMBER') and principal.enabled";
    private final ReceptionSupportService support;

    @GetMapping("/reception/support-requests") @PreAuthorize(RECEPTIONIST)
    public List<ReceptionSupportService.Ticket> list(@RequestParam(required=false) @Positive Long memberId,@RequestParam(required=false) ReceptionSupportService.Status status,
                                                     @RequestParam(required=false) @Size(max=100) String q,@AuthenticationPrincipal CustomUserDetails actor) {
        return support.staff(actor.getUserAccount().getId(),memberId,status,q);
    }
    @GetMapping("/reception/support-requests/summary") @PreAuthorize(RECEPTIONIST)
    public ReceptionSupportService.Summary summary(@AuthenticationPrincipal CustomUserDetails actor){return support.summary(actor.getUserAccount().getId());}
    @GetMapping("/reception/support-requests/{id}") @PreAuthorize(RECEPTIONIST)
    public ReceptionSupportService.Ticket detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){return support.detail(actor.getUserAccount().getId(),id);}
    @GetMapping("/reception/support-requests/{id}/verification") @PreAuthorize(RECEPTIONIST)
    public ReceptionSupportService.Verification verification(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){return support.verification(actor.getUserAccount().getId(),id);}
    @PostMapping("/reception/support-requests") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(RECEPTIONIST)
    public ReceptionSupportService.Ticket create(@Valid @RequestBody ReceptionSupportService.Intake request,@RequestHeader("Idempotency-Key") UUID operation,@AuthenticationPrincipal CustomUserDetails actor){return support.create(actor.getUserAccount().getId(),request,operation);}
    @PostMapping("/reception/support-requests/{id}/replies") @PreAuthorize(RECEPTIONIST)
    public ReceptionSupportService.Ticket reply(@PathVariable long id,@Valid @RequestBody ReceptionSupportService.ReplyRequest request,@AuthenticationPrincipal CustomUserDetails actor){return support.reply(actor.getUserAccount().getId(),id,request);}
    @PutMapping("/reception/support-requests/{id}/status") @PreAuthorize(RECEPTIONIST)
    public ReceptionSupportService.Ticket update(@PathVariable long id,@Valid @RequestBody ReceptionSupportService.Update request,@AuthenticationPrincipal CustomUserDetails actor){return support.update(actor.getUserAccount().getId(),id,request);}

    @GetMapping("/member/support-requests") @PreAuthorize(MEMBER)
    public List<ReceptionSupportService.MemberTicket> member(@AuthenticationPrincipal CustomUserDetails actor){return support.member(actor.getUserAccount().getId());}
    @GetMapping("/member/support-requests/{id}") @PreAuthorize(MEMBER)
    public ReceptionSupportService.MemberTicket memberDetail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){return support.memberDetail(actor.getUserAccount().getId(),id);}
    @PostMapping("/member/support-requests") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize(MEMBER)
    public ReceptionSupportService.MemberTicket memberCreate(@Valid @RequestBody ReceptionSupportService.MemberIntake request,@RequestHeader("Idempotency-Key") UUID operation,@AuthenticationPrincipal CustomUserDetails actor){return support.memberCreate(actor.getUserAccount().getId(),request,operation);}
    @PostMapping("/member/support-requests/{id}/replies") @PreAuthorize(MEMBER)
    public ReceptionSupportService.MemberTicket memberReply(@PathVariable long id,@Valid @RequestBody ReceptionSupportService.ReplyRequest request,@AuthenticationPrincipal CustomUserDetails actor){return support.memberReply(actor.getUserAccount().getId(),id,request);}
    @PutMapping("/member/support-requests/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize(MEMBER)
    public void memberRead(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){support.memberRead(actor.getUserAccount().getId(),id);}
}
