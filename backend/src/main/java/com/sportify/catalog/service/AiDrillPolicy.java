package com.sportify.catalog.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provider-independent part of Flow 5 AI drill drafting: one prompt, one output shape and one validation policy,
 * shared by every {@link DrillGenerator} provider so Claude and Gemini drafts obey exactly the same limits.
 */
final class AiDrillPolicy {
    private AiDrillPolicy() {}

    static final int MAX_DRILLS = 4;
    static final int MIN_MINUTES = 3;
    static final int MAX_MINUTES = 30;
    static final int MAX_TITLE = 150;
    static final int MAX_GOAL = 150;
    static final int MAX_INSTRUCTIONS = 1000;
    static final int MAX_REASON = 500;

    static final String SYSTEM_PROMPT = """
            You draft practice drills for a coach at a multi-sport center. The coach reviews, edits and \
            assigns every draft; nothing you write reaches the member without that review.

            The user message holds a training context with a "scope".
            - INDIVIDUAL: one member. Use the sport, the member's level, the member's training goal, how many \
            sessions the member attended, recent skill scores and the coach's latest feedback. Choose drills that \
            address the weakest recent scores and the areas for improvement, at a difficulty that matches the \
            member's level, and that serve the stated goal.
            - CLASS: a whole class. "group" gives the number of members, how many are at each level, how many \
            have each goal, and the class average of each recorded skill metric. Choose drills the whole group can \
            do together at the class level, targeting the lowest class averages and the most common goals.
            When there are no recorded results, rely on the level and the goal.
            If "focus" is present, it is the coach's current focus for this session: prioritise it.
            If "sessionMinutes" is present, the total duration of all drills must not exceed it.

            Return 2 to 4 drills for the given sport only. Each drill lasts 3 to 30 minutes. Write the \
            instructions as short, safe, step-by-step guidance that a coach can supervise in a normal \
            session. In "reason", say which goal, level, focus, score or feedback the drill responds to.

            Do not give medical, injury, rehabilitation, diet or weight-loss advice. Do not invent scores \
            or facts that are not in the context. Write in English.""";

    /** One drill as the model must return it. */
    record ProposedDrill(
            @JsonPropertyDescription("Short drill title, at most 150 characters") String title,
            @JsonPropertyDescription("The skill this drill trains, for example passing accuracy") String goal,
            @JsonPropertyDescription("Duration in minutes, between 3 and 30") int durationMinutes,
            @JsonPropertyDescription("Step-by-step instructions a coach can supervise, at most 1000 characters") String instructions,
            @JsonPropertyDescription("Which goal, level, recent score or feedback this drill addresses, at most 500 characters") String reason) {}

    /** The full structured response. */
    record ProposedDrills(List<ProposedDrill> drills) {}

    /** The de-identified context sent as the user message. */
    static String context(ObjectMapper json, DrillGenerator.Input input) {
        try {
            return "Training context (JSON):\n" + json.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise the drill context", e);
        }
    }

    /** Level stamped on the drafts: the class level for class scope, otherwise the member's effective level. */
    static String level(DrillGenerator.Input input) {
        return DrillGenerator.SCOPE_CLASS.equals(input.scope()) ? input.classLevel()
                : DrillGenerator.effectiveLevel(input.memberLevel(), input.classLevel());
    }

    static List<DrillDraft> toDrafts(ProposedDrills proposed, String level) {
        return toDrafts(proposed, level, null);
    }

    /**
     * Validates a model proposal; anything outside the limits Coach plans accept is dropped, and when a session
     * duration is given, drills that would push the total beyond it are dropped too.
     */
    static List<DrillDraft> toDrafts(ProposedDrills proposed, String level, Integer sessionMinutes) {
        if (proposed == null || proposed.drills() == null) return List.of();
        var valid = proposed.drills().stream()
                .filter(Objects::nonNull)
                .filter(drill -> drill.durationMinutes() >= MIN_MINUTES && drill.durationMinutes() <= MAX_MINUTES)
                .map(drill -> new DrillDraft(
                        clean(drill.title(), MAX_TITLE),
                        truncate(drill.goal(), MAX_GOAL),
                        drill.durationMinutes(),
                        clean(drill.instructions(), MAX_INSTRUCTIONS),
                        truncate(drill.reason(), MAX_REASON),
                        level))
                .filter(draft -> draft.title() != null && draft.instructions() != null)
                .toList();
        var kept = new ArrayList<DrillDraft>();
        int total = 0;
        for (var draft : valid) {
            if (kept.size() == MAX_DRILLS) break;
            if (sessionMinutes != null && total + draft.durationMinutes() > sessionMinutes) continue;
            kept.add(draft);
            total += draft.durationMinutes();
        }
        return List.copyOf(kept);
    }

    /** Trims, removes control characters and rejects blank or over-long text (fields a Coach plan stores as-is). */
    static String clean(String value, int max) {
        String text = normalise(value);
        return text == null || text.length() > max ? null : text;
    }

    /** Like {@link #clean} but shortens over-long descriptive text instead of rejecting the drill. */
    static String truncate(String value, int max) {
        String text = normalise(value);
        return text == null ? null : text.length() <= max ? text : text.substring(0, max).trim();
    }

    private static String normalise(String value) {
        if (value == null) return null;
        String text = value.replaceAll("[\\p{Cntrl}&&[^\\n]]", " ").trim();
        return text.isEmpty() ? null : text;
    }
}
