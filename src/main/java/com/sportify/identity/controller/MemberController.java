package com.sportify.identity.controller;

import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.service.MemberSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberSearchService memberSearchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public Page<MemberSearchResponse> searchMembers(
            @RequestParam(required = false) String query,
            @org.springframework.data.web.PageableDefault(sort = "id") Pageable pageable) {
        
        return memberSearchService.searchMembers(query, pageable);
    }
}
