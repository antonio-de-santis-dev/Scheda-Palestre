package com.gymplanner.workoutplan.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.workoutplan.internal.PlanDtos.PlanExerciseRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.SetRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExerciseConfigValidatorTest {

    private static final UUID EXERCISE = UUID.randomUUID();

    private static PlanExerciseRequest request(int sets, int reps, boolean failure, List<SetRequest> custom) {
        return new PlanExerciseRequest(EXERCISE, sets, reps, failure, 90, custom, null);
    }

    @Test
    void normalExerciseNeedsRepsBetweenOneAndHundred() {
        assertThat(ExerciseConfigValidator.validate(request(3, 10, false, null)).reps()).isEqualTo(10);
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(3, 0, false, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void toFailureMeansMaxWithZeroReps() {
        assertThat(ExerciseConfigValidator.validate(request(3, 0, true, List.of())).toFailure()).isTrue();
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(3, 8, true, null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).errors().get(0).field()).isEqualTo("reps"));
    }

    @Test
    void customSetsMustCoverAllSetsFromOneToN() {
        var complete = List.of(new SetRequest(2, 8, false, 60, null), new SetRequest(1, 10, false, 90, null),
                new SetRequest(3, 0, true, 0, null));
        var config = ExerciseConfigValidator.validate(request(3, 10, false, complete));
        assertThat(config.customSets()).extracting(ExerciseConfig.SetConfig::setIndex).containsExactly(1, 2, 3);

        var missing = List.of(new SetRequest(1, 10, false, 90, null), new SetRequest(3, 10, false, 90, null));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(3, 10, false, missing)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).code()).isEqualTo("INVALID_CUSTOM_SETS"));

        var duplicated = List.of(new SetRequest(1, 10, false, 90, null), new SetRequest(1, 10, false, 90, null));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(2, 10, false, duplicated)))
                .isInstanceOf(BadRequestException.class);

        var tooMany = List.of(new SetRequest(1, 10, false, 90, null), new SetRequest(2, 10, false, 90, null));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(1, 10, false, tooMany)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void customSetFailureRuleIsCheckedPerSet() {
        var invalid = List.of(new SetRequest(1, 5, true, 90, null));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(1, 10, false, invalid)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).errors().get(0).field())
                        .isEqualTo("customSets[0].reps"));
    }

    @Test
    void effectiveSetsRepeatGeneralValuesWhenNotCustomized() {
        var exercise = new PlanExercise(null, EXERCISE, 1, new ExerciseConfig(3, 12, false, 60, null, List.of()));
        assertThat(exercise.isCustomized()).isFalse();
        assertThat(exercise.effectiveSets()).hasSize(3)
                .allSatisfy(s -> assertThat(s.reps()).isEqualTo(12))
                .extracting(ExerciseConfig.SetConfig::setIndex).containsExactly(1, 2, 3);
    }
    @Test
    void plannedWeightsAreCopiedWithGeneralAndCustomConfiguration() {
        var weight = new java.math.BigDecimal("25.50");
        var general = new ExerciseConfig(2, 10, false, 60, weight, List.of());
        var exercise = new PlanExercise(null, EXERCISE, 1, general);
        assertThat(exercise.effectiveSets()).allSatisfy(set -> assertThat(set.plannedWeightKg()).isEqualByComparingTo(weight));
        var custom = new ExerciseConfig(2, 10, false, 60, weight,
                List.of(new ExerciseConfig.SetConfig(1, 10, false, 60, weight),
                        new ExerciseConfig.SetConfig(2, 8, false, 60, java.math.BigDecimal.ZERO)));
        exercise.apply(EXERCISE, custom);
        var copy = new PlanExercise(null, EXERCISE, 1, exercise.config());
        assertThat(copy.effectiveSets()).extracting(ExerciseConfig.SetConfig::plannedWeightKg)
                .containsExactly(weight, java.math.BigDecimal.ZERO);
    }

}
