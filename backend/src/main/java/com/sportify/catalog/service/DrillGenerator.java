package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Optional AI drafting step for Coach drill suggestions (Flow 5).
 * When no bean is present, or a call fails or returns nothing usable, the rule-based catalog is used.
 * Implementations receive only training data: never names, e-mail addresses, phone numbers or member codes.
 */
public interface DrillGenerator {

    /** Identifies the generator in responses, for example the model id. */
    String version();

    /** Returns validated drafts for Coach review, or an empty list when nothing usable was produced. */
    List<DrillDraft> generate(Input input);

    /** One recorded skill score from an earlier session of this class. */
    record RecentScore(String metric, BigDecimal score, Integer maxScore, LocalDate sessionDate) {}

    /** De-identified training context of one member booking. */
    record Input(String sport, String classLevel, String memberLevel, String goal, int attendedSessions,
                 List<RecentScore> recentScores, String latestStrengths, String latestAreasForImprovement) {}

    /** The member's own level when it is a supported value, otherwise the class level. */
    static String effectiveLevel(String memberLevel, String classLevel) {
        return memberLevel != null && Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(memberLevel) ? memberLevel : classLevel;
    }
}
