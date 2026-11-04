package com.sportify.catalog.controller;

import com.sportify.catalog.service.ClassNoticeService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ClassNoticeController {
    private final ClassNoticeService notices;
    @GetMapping("/member/training/notices") @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public List<ClassNoticeService.Notice> member(@AuthenticationPrincipal CustomUserDetails actor) {return notices.member(actor.getUserAccount().getId());}
    @GetMapping("/coach/training/notices") @PreAuthorize("hasRole('COACH') and principal.enabled")
    public List<ClassNoticeService.Notice> coach(@AuthenticationPrincipal CustomUserDetails actor) {return notices.coach(actor.getUserAccount().getId());}
    @PostMapping("/coach/training/notices") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('COACH') and principal.enabled")
    public ClassNoticeService.Notice create(@Valid @RequestBody ClassNoticeService.Request request,@RequestHeader("Idempotency-Key") UUID operation,@AuthenticationPrincipal CustomUserDetails actor) {
        return notices.create(actor.getUserAccount().getId(),request,operation);
    }
    @PutMapping("/coach/training/notices/{id}/cancel") @PreAuthorize("hasRole('COACH') and principal.enabled")
    public ClassNoticeService.Notice cancel(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor) {return notices.cancel(actor.getUserAccount().getId(),id);}
}
