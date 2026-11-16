package com.sportify.catalog.controller;

import com.sportify.catalog.service.MemberCheckInQrService;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hoi vien lay noi dung QR check-in co dinh cua minh.
 * Viec quet QR di qua endpoint check-in hien co: POST /api/v1/check-ins (le tan / quan ly).
 */
@RestController
@RequestMapping("/api/v1/member/check-in-qr")
@RequiredArgsConstructor
public class MemberCheckInQrController {

    private final MemberCheckInQrService checkInQrService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER') and principal.enabled")
    public MemberCheckInQrService.MemberCheckInQr myCheckInQr(@AuthenticationPrincipal CustomUserDetails actor) {
        return checkInQrService.forMember(actor.getUserAccount().getId());
    }
}
