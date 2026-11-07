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
    @Test void ageGroupsUseTheAgeOnTheSessionDateWithInclusiveBounds() {
        var born = java.time.LocalDate.of(2011, 3, 11);
        assertThat(TrainingGuidanceService.fitsAge(born, java.time.LocalDate.of(2027, 3, 10), 16, null)).isFalse();
        assertThat(TrainingGuidanceService.fitsAge(born, java.time.LocalDate.of(2027, 3, 11), 16, null)).isTrue();
        assertThat(TrainingGuidanceService.fitsAge(born, java.time.LocalDate.of(2027, 3, 11), 6, 16)).isTrue();
        assertThat(TrainingGuidanceService.fitsAge(born, java.time.LocalDate.of(2028, 3, 11), 6, 16)).isFalse();
        assertThat(TrainingGuidanceService.fitsAge(born, java.time.LocalDate.of(2027, 3, 11), null, null)).isTrue();
    }
}
