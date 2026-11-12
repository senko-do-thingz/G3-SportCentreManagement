package com.sportify.catalog.controller;

import com.sportify.catalog.service.OperationalNotificationService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Header inbox for Manager and Coach: the caller's own notifications only. Marking read changes presentation state only. */
@RestController @RequestMapping("/api/v1/staff/notifications")
@PreAuthorize("hasAnyRole('MANAGER','COACH') and principal.enabled") @RequiredArgsConstructor
public class StaffNotificationController {
    private final OperationalNotificationService notifications;
    @GetMapping public List<OperationalNotificationService.Notice> inbox(@AuthenticationPrincipal CustomUserDetails actor){return notifications.own(actor.getUserAccount().getId());}
    @PutMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable long id,@AuthenticationPrincipal CustomUserDetails actor){notifications.read(actor.getUserAccount().getId(),id);}
}
