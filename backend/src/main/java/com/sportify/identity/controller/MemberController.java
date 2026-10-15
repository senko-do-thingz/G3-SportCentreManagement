package com.sportify.identity.controller;

import com.sportify.identity.dto.MemberCreateRequest;
import com.sportify.identity.dto.MemberCreateResponse;
import com.sportify.identity.dto.MemberSearchResponse;
import com.sportify.identity.service.FrontDeskMemberService;
import com.sportify.identity.service.MemberSearchService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

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
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {

        return memberSearchService.searchMembers(query, pageable);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'RECEPTIONIST')")
    public ResponseEntity<MemberCreateResponse> create(@Valid @RequestBody MemberCreateRequest request) {
        MemberCreateResponse response = frontDeskMemberService.create(request);
        URI location = URI.create("/api/v1/members/" + response.userId() + "/overview");
        return ResponseEntity.created(location).header("Cache-Control", "no-store").body(response);
    }
}
