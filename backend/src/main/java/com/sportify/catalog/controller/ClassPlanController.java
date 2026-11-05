package com.sportify.catalog.controller;

import com.sportify.catalog.service.ClassPlanService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/coach/training/class-plans")
@PreAuthorize("hasRole('COACH') and principal.enabled")
@RequiredArgsConstructor
public class ClassPlanController {
    private final ClassPlanService plans;
    @GetMapping public List<ClassPlanService.Summary> list(@AuthenticationPrincipal CustomUserDetails actor) { return plans.list(actor.getUserAccount().getId()); }
    @GetMapping("/{id}") public ClassPlanService.Detail detail(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor) { return plans.detail(actor.getUserAccount().getId(),id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ClassPlanService.Detail create(@Valid @RequestBody ClassPlanService.Request request,
            @RequestHeader(value="Idempotency-Key",required=false) UUID operation,@AuthenticationPrincipal CustomUserDetails actor) {
        return plans.create(actor.getUserAccount().getId(),request,operation);
    }
}
