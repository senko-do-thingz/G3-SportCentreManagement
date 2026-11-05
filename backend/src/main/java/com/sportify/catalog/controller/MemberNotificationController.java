package com.sportify.catalog.controller;

import com.sportify.catalog.service.OperationalNotificationService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/member/notifications")
@PreAuthorize("hasRole('MEMBER') and principal.enabled") @RequiredArgsConstructor
public class MemberNotificationController {
    private final OperationalNotificationService notifications;
    @GetMapping public OperationalNotificationService.Inbox inbox(@AuthenticationPrincipal CustomUserDetails actor){return notifications.inbox(actor.getUserAccount().getId());}
    @PutMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){notifications.read(actor.getUserAccount().getId(),id);}
}
