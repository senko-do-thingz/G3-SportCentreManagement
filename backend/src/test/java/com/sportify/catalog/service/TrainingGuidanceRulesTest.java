package com.sportify.catalog.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TrainingGuidanceRulesTest {
    @Test void goalsOnlyScoreSupportedSessionCharacteristics() {
        assertThat(TrainingGuidanceService.goalScore("BUILD_SKILLS","COACH_LED","BEGINNER",30)).isEqualTo(15);
        assertThat(TrainingGuidanceService.goalScore("BUILD_SKILLS","SELF_TRAINING","BEGINNER",30)).isZero();
        assertThat(TrainingGuidanceService.goalScore("COMPETITION","COACH_LED","BEGINNER",60)).isZero();
        assertThat(TrainingGuidanceService.goalScore("COMPETITION","COACH_LED","ADVANCED",60)).isEqualTo(15);
        assertThat(TrainingGuidanceService.goalScore("IMPROVE_FITNESS","SELF_TRAINING","BEGINNER",59)).isZero();
        assertThat(TrainingGuidanceService.goalScore("IMPROVE_FITNESS","SELF_TRAINING","BEGINNER",60)).isEqualTo(15);
        assertThat(TrainingGuidanceService.goalScore(null,"COACH_LED","BEGINNER",60)).isZero();
    }
}
