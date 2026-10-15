package com.sportify.identity.controller;

import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.dto.MemberCreateResponse;
import com.sportify.identity.dto.MemberOverviewResponse;
import com.sportify.identity.service.FrontDeskMemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final FrontDeskMemberService frontDeskMemberService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public Page<MemberSearchResponse> searchMembers(
            @RequestParam(required = false) String query,
            @org.springframework.data.web.PageableDefault(size = 20, sort = "id") Pageable pageable) {
        
        return memberSearchService.searchMembers(query, pageable);
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public MemberOverviewResponse overview(@PathVariable Long id) {
        return frontDeskMemberService.overview(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public ResponseEntity<MemberCreateResponse> create(@Valid @RequestBody MemberCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
                .body(frontDeskMemberService.create(request));
    }
}
