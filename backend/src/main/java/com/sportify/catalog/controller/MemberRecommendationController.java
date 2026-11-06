package com.sportify.catalog.controller;

import com.sportify.catalog.service.MemberRecommendationService;
import com.sportify.catalog.service.MemberRecommendationService.LevelUpdate;
import com.sportify.catalog.service.MemberRecommendationService.SaveRequest;
import com.sportify.catalog.service.MemberRecommendationService.Saved;
import com.sportify.catalog.service.MemberRecommendationService.SportLevel;
import com.sportify.identity.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Flow 5 Member recommendation preferences (F5-01) and saved recommendations (F5-05). Own data only. */
@RestController
@RequestMapping("/api/v1/member/training")
@PreAuthorize("hasRole('MEMBER') and principal.enabled")
@RequiredArgsConstructor
public class MemberRecommendationController {
    private final MemberRecommendationService service;

    @GetMapping("/sport-levels")
    public List<SportLevel> sportLevels(@AuthenticationPrincipal CustomUserDetails user) {
        return service.sportLevels(user.getUserAccount().getId());
    }

    @PutMapping("/sport-levels/{sportId}")
    public SportLevel saveSportLevel(@PathVariable long sportId, @Valid @RequestBody LevelUpdate request,
                                     @AuthenticationPrincipal CustomUserDetails user) {
        return service.saveSportLevel(user.getUserAccount().getId(), sportId, request.level());
    }

    @DeleteMapping("/sport-levels/{sportId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearSportLevel(@PathVariable long sportId, @AuthenticationPrincipal CustomUserDetails user) {
        service.clearSportLevel(user.getUserAccount().getId(), sportId);
    }

    @GetMapping("/saved-recommendations")
    public List<Saved> saved(@AuthenticationPrincipal CustomUserDetails user) {
        return service.saved(user.getUserAccount().getId());
    }

    @PostMapping("/saved-recommendations")
    public ResponseEntity<Saved> save(@Valid @RequestBody SaveRequest request, @AuthenticationPrincipal CustomUserDetails user) {
        long memberId = user.getUserAccount().getId();
        boolean existed = service.saved(memberId).stream().anyMatch(item -> item.classId() == request.classId());
        Saved saved = service.save(memberId, request);
        return ResponseEntity.status(existed ? HttpStatus.OK : HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/saved-recommendations/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long id, @AuthenticationPrincipal CustomUserDetails user) {
        service.remove(user.getUserAccount().getId(), id);
    }
}
