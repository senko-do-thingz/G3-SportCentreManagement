package com.sportify.catalog.controller;

import com.sportify.catalog.dto.TrainingGuidanceRequest.Preferences;
import com.sportify.catalog.dto.TrainingGuidanceResponse.Recommendations;
import com.sportify.catalog.service.TrainingGuidanceService;
import com.sportify.catalog.service.CenterConsultationService;
import jakarta.validation.Valid;
import com.sportify.identity.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/v1/member/training")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class TrainingGuidanceController {
    private final TrainingGuidanceService service;
    private final CenterConsultationService consultation;
    @PostMapping("/consultation")
    public com.sportify.catalog.dto.TrainingGuidanceResponse.Consultation consult(
        @Valid @RequestBody com.sportify.catalog.dto.TrainingGuidanceRequest.Consultation request) {
        return consultation.answer(request.question(),request.optIn());
    }
    @GetMapping("/recommendations")
    public Recommendations recommendations(@AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(required=false) Long sportId, @RequestParam(required=false) String level,
        @RequestParam(required=false) String goal, @RequestParam(required=false) LocalDate from,
        @RequestParam(required=false) LocalDate to, @RequestParam(required=false) LocalTime earliest,
        @RequestParam(required=false) LocalTime latest, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="10") int size, @RequestParam(required=false) String format,
        @RequestParam(required=false) String days) {
        return service.recommendations(user.getUserAccount().getId(),
            new Preferences(sportId,level,goal,from,to,earliest,latest,page,size,format,parseDays(days)));
    }

    /** Comma-separated weekdays such as MON,THU; anything else is a 400. */
    static java.util.Set<java.time.DayOfWeek> parseDays(String days) {
        if (days == null || days.isBlank()) return null;
        var parsed = java.util.EnumSet.noneOf(java.time.DayOfWeek.class);
        for (String part : days.split(",")) {
            String code = part.trim().toUpperCase(java.util.Locale.ROOT);
            var match = java.util.Arrays.stream(java.time.DayOfWeek.values()).filter(day -> day.name().startsWith(code) && code.length() >= 3).findFirst();
            if (match.isEmpty()) throw new com.sportify.core.exception.BusinessRuleException("Use weekday codes such as MON,THU for available days");
            parsed.add(match.get());
        }
        return parsed;
    }
}
