package com.sportify.catalog.controller;

import com.sportify.catalog.dto.*;
import com.sportify.catalog.service.SporiAiService;
import com.sportify.catalog.service.SporiAiSessionService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Spori AI chatbot and multi-session management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/spori-ai")
@RequiredArgsConstructor
public class SporiAiController {

    private final SporiAiService sporiAiService;
    private final SporiAiSessionService sessionService;

    // -------------------------------------------------------------
    // Session Management Endpoints
    // -------------------------------------------------------------

    @PostMapping("/sessions")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<ChatSessionResponse> createSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody(required = false) @Valid ChatSessionCreateRequest request) {

        Long userId = userDetails.getUserAccount().getId();
        String title = (request != null) ? request.title() : null;
        ChatSessionResponse session = sessionService.createSession(userId, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<List<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Boolean archived) {

        Long userId = userDetails.getUserAccount().getId();
        List<ChatSessionResponse> sessions = sessionService.listSessions(userId, archived);
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<ChatSessionDetailResponse> getSessionDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        Long userId = userDetails.getUserAccount().getId();
        ChatSessionDetailResponse detail = sessionService.getSessionDetail(userId, id);
        return ResponseEntity.ok(detail);
    }

    @PutMapping("/sessions/{id}/title")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<ChatSessionResponse> renameSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ChatSessionRenameRequest request) {

        Long userId = userDetails.getUserAccount().getId();
        ChatSessionResponse session = sessionService.renameSession(userId, id, request.title());
        return ResponseEntity.ok(session);
    }

    @PutMapping("/sessions/{id}/archive")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<ChatSessionResponse> archiveSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestBody(required = false) ChatSessionArchiveRequest request) {

        Long userId = userDetails.getUserAccount().getId();
        boolean archive = (request != null) ? request.archived() : true;
        ChatSessionResponse session = sessionService.archiveSession(userId, id, archive);
        return ResponseEntity.ok(session);
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {

        Long userId = userDetails.getUserAccount().getId();
        sessionService.deleteSession(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/{id}/messages")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<ChatSessionMessageResponse> sendMessageInSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody ChatSessionMessageRequest request) {

        Long userId = userDetails.getUserAccount().getId();
        String role = resolveRole(userDetails);
        ChatSessionMessageResponse response = sessionService.sendMessage(userId, role, id, request.message());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------
    // Direct / Single-turn Chat Endpoint (Backward Compatible)
    // -------------------------------------------------------------

    @PostMapping("/chat")
    @PreAuthorize("hasAnyRole('MEMBER', 'MANAGER', 'RECEPTIONIST', 'COACH') and principal.enabled")
    public ResponseEntity<SporiAiChatResponse> chat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SporiAiChatRequest request) {

        String role = resolveRole(userDetails);
        log.debug("Processing Spori AI chat request for role: {}", role);

        if ("/status/".equalsIgnoreCase(request.message().strip())) {
            String status = sporiAiService.getStatus();
            return ResponseEntity.ok(new SporiAiChatResponse(status, role));
        }

        String reply = sporiAiService.chat(request.message(), role);
        return ResponseEntity.ok(new SporiAiChatResponse(reply, role));
    }

    private String resolveRole(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return "MEMBER";
        }
        if (userDetails.getUserAccount() != null && userDetails.getUserAccount().getRole() != null) {
            String code = userDetails.getUserAccount().getRole().getCode();
            if (code != null && !code.isBlank()) {
                String normalized = code.trim().toUpperCase();
                return normalized.startsWith("ROLE_") ? normalized.substring(5) : normalized;
            }
        }
        return userDetails.getAuthorities().stream()
                .findFirst()
                .map(authority -> {
                    String auth = authority.getAuthority();
                    return auth.startsWith("ROLE_") ? auth.substring(5) : auth;
                })
                .orElse("MEMBER");
    }
}
