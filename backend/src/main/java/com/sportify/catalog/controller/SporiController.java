package com.sportify.catalog.controller;

import com.sportify.catalog.dto.TrainingGuidanceRequest.ChatTurn;
import com.sportify.catalog.dto.TrainingGuidanceResponse.Consultation;
import com.sportify.catalog.service.AssistantReplyWriter;
import com.sportify.catalog.service.CenterConsultationService;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Spori, the shared read-only assistant for Members, Managers, Receptionists and Coaches. The caller's role comes from
 * the signed-in account, never from the request, and decides which Sportify data the answer may use.
 */
@RestController
@RequestMapping("/api/v1/assistant")
@PreAuthorize("hasAnyRole('MEMBER','MANAGER','RECEPTIONIST','COACH') and principal.enabled")
public class SporiController {
    private final CenterConsultationService consultation;

    public SporiController(CenterConsultationService consultation) {
        this.consultation = consultation;
    }

    /** {@code history} holds the earlier turns of the open chat, oldest first; it is never stored. */
    public record ChatRequest(@NotBlank @Size(max = 500) String question, @Valid @Size(max = 12) List<ChatTurn> history) {}

    @PostMapping("/chat")
    public Consultation chat(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody ChatRequest request) {
        // /status/ is an exact command handled by the backend, never a prompt sent to Gemini as user chat.
        if ("/status/".equalsIgnoreCase(request.question().strip())) {
            return consultation.geminiStatus();
        }
        var account = user.getUserAccount();
        var history = request.history() == null ? List.<AssistantReplyWriter.Turn>of() : request.history().stream()
                .filter(Objects::nonNull).map(turn -> new AssistantReplyWriter.Turn(turn.role(), turn.text())).toList();
        return consultation.chat(account.getId(), account.getRole().getCode(), request.question(), history);
    }
}
