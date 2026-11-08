package com.sportify.identity.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MW-02 advisory outcome rules. NO_REPORTED_CONCERNS needs an explicit NO to every question. */
class MemberHealthOutcomeTest {

    private static Map<String, String> all(String value) {
        Map<String, String> answers = new HashMap<>();
        MemberHealthService.QUESTIONS.forEach(q -> answers.put(q.key(), value));
        return answers;
    }

    @Test
    void emptyOrPartialAnswersAreIncompleteNotACLearance() {
        assertThat(MemberHealthService.outcome(Map.of())).isEqualTo(MemberHealthService.INCOMPLETE);
        assertThat(MemberHealthService.outcome(Map.of("injuryOrMobility", "NO"))).isEqualTo(MemberHealthService.INCOMPLETE);
    }

    @Test
    void preferNotToAnswerNeverCountsAsNoConcern() {
        var answers = all("NO");
        answers.put("exertionSymptoms", "PREFER_NOT_TO_ANSWER");
        assertThat(MemberHealthService.outcome(answers)).isEqualTo(MemberHealthService.INCOMPLETE);
    }

    @Test
    void anyYesRecommendsACoachConversationEvenWhenOthersAreSkipped() {
        assertThat(MemberHealthService.outcome(Map.of("coachShouldKnow", "YES"))).isEqualTo(MemberHealthService.COACH_DISCUSSION_RECOMMENDED);
        assertThat(MemberHealthService.outcome(all("YES"))).isEqualTo(MemberHealthService.COACH_DISCUSSION_RECOMMENDED);
    }

    @Test
    void allNoIsNoReportedConcerns() {
        assertThat(MemberHealthService.outcome(all("NO"))).isEqualTo(MemberHealthService.NO_REPORTED_CONCERNS);
    }

    @Test
    void questionnaireHasTheFiveFixedQuestionsAndAnAdvisoryDisclaimer() {
        assertThat(MemberHealthService.QUESTIONS).hasSize(5);
        assertThat(MemberHealthService.DISCLAIMER).contains("not a medical assessment").contains("not mean you are medically cleared");
    }
}
