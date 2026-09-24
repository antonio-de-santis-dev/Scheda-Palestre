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
        return new PlanExerciseRequest(EXERCISE, sets, reps, failure, 90, custom);
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
        var complete = List.of(new SetRequest(2, 8, false, 60), new SetRequest(1, 10, false, 90),
                new SetRequest(3, 0, true, 0));
        var config = ExerciseConfigValidator.validate(request(3, 10, false, complete));
        assertThat(config.customSets()).extracting(ExerciseConfig.SetConfig::setIndex).containsExactly(1, 2, 3);

        var missing = List.of(new SetRequest(1, 10, false, 90), new SetRequest(3, 10, false, 90));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(3, 10, false, missing)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).code()).isEqualTo("INVALID_CUSTOM_SETS"));

        var duplicated = List.of(new SetRequest(1, 10, false, 90), new SetRequest(1, 10, false, 90));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(2, 10, false, duplicated)))
                .isInstanceOf(BadRequestException.class);

        var tooMany = List.of(new SetRequest(1, 10, false, 90), new SetRequest(2, 10, false, 90));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(1, 10, false, tooMany)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void customSetFailureRuleIsCheckedPerSet() {
        var invalid = List.of(new SetRequest(1, 5, true, 90));
        assertThatThrownBy(() -> ExerciseConfigValidator.validate(request(1, 10, false, invalid)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).errors().get(0).field())
                        .isEqualTo("customSets[0].reps"));
    }

    @Test
    void effectiveSetsRepeatGeneralValuesWhenNotCustomized() {
        var exercise = new PlanExercise(null, EXERCISE, 1, new ExerciseConfig(3, 12, false, 60, List.of()));
        assertThat(exercise.isCustomized()).isFalse();
        assertThat(exercise.effectiveSets()).hasSize(3)
                .allSatisfy(s -> assertThat(s.reps()).isEqualTo(12))
                .extracting(ExerciseConfig.SetConfig::setIndex).containsExactly(1, 2, 3);
    }
}
