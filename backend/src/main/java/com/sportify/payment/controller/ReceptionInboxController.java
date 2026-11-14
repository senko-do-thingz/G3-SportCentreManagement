package com.sportify.payment.controller;

import com.sportify.payment.service.ReceptionInboxService;

import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Hộp thư công việc của lễ tân: đánh dấu đã đọc không đồng nghĩa xử lý xong yêu cầu. */
@RestController
@RequestMapping("/api/v1/reception/inbox")
@PreAuthorize("hasRole('RECEPTIONIST') and principal.enabled")
@RequiredArgsConstructor
public class ReceptionInboxController {

    private final ReceptionInboxService service;

    @GetMapping
    public ReceptionInboxService.Inbox inbox(@RequestParam(defaultValue = "false") boolean openOnly,
                                             @AuthenticationPrincipal CustomUserDetails user) {
        return service.inbox(user.getUserAccount().getId(), openOnly);
    }

    @PutMapping("/{id}/read")
    public ReceptionInboxService.Item markRead(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        return service.markRead(user.getUserAccount().getId(), id);
    }
}
