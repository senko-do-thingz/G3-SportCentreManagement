package com.sportify.catalog.service;

import com.sportify.catalog.dto.TrainingWriteResponse.DrillDraft;
import com.sportify.core.exception.BusinessRuleException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional AI drafting step for Coach drill suggestions (Flow 5).
 * When no bean is present, or a call fails or returns nothing usable, the rule-based catalog is used.
 * Implementations receive only training data: never names, e-mail addresses, phone numbers, member codes or ids.
 */
public interface DrillGenerator {

    String SCOPE_INDIVIDUAL = "INDIVIDUAL";
    String SCOPE_CLASS = "CLASS";

    /** Identifies the generator in responses, for example the model id. */
    String version();

    /** Returns validated drafts for Coach review, or an empty list when nothing usable was produced. */
    List<DrillDraft> generate(Input input);

    /** One recorded skill score from an earlier session of this class. */
    record RecentScore(String metric, BigDecimal score, Integer maxScore, LocalDate sessionDate) {}

    /** Class average of one skill metric over recorded results. */
    record MetricAverage(String metric, BigDecimal averageScore, Integer maxScore, int samples) {}

    /** De-identified summary of the members of a class: counts only, never individual rows. */
    record GroupSummary(int members, Map<String, Integer> levels, Map<String, Integer> goals, List<MetricAverage> averages) {}

    /**
     * De-identified training context. For INDIVIDUAL scope the member fields are filled and {@code group} is null;
     * for CLASS scope the member fields are null and {@code group} summarises the class.
     */
    record Input(String scope, String sport, String classLevel, String memberLevel, String goal, int attendedSessions,
                 List<RecentScore> recentScores, String latestStrengths, String latestAreasForImprovement,
                 String focus, Integer sessionMinutes, GroupSummary group) {}

    /** Optional Coach inputs for one preview. */
    record Options(String focus, Integer sessionMinutes) {
        public static final Options NONE = new Options(null, null);
        public static final int MAX_FOCUS = 200;
        public static final int MIN_SESSION_MINUTES = 15;
        public static final int MAX_SESSION_MINUTES = 180;

        /** Trims the focus and validates both inputs; blank values mean "not given". */
        public static Options of(String focus, Integer sessionMinutes) {
            String text = focus == null || focus.isBlank() ? null : focus.trim();
            if (text != null && text.length() > MAX_FOCUS)
                throw new BusinessRuleException("Current focus must be at most " + MAX_FOCUS + " characters");
            if (sessionMinutes != null && (sessionMinutes < MIN_SESSION_MINUTES || sessionMinutes > MAX_SESSION_MINUTES))
                throw new BusinessRuleException("Session duration must be between " + MIN_SESSION_MINUTES + " and " + MAX_SESSION_MINUTES + " minutes");
            return new Options(text, sessionMinutes);
        }
    }

    /** The member's own level when it is a supported value, otherwise the class level. */
    static String effectiveLevel(String memberLevel, String classLevel) {
        return memberLevel != null && Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED").contains(memberLevel) ? memberLevel : classLevel;
    }
}
