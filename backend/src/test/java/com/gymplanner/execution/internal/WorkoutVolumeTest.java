package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkoutVolumeTest {
    private static final Instant NOW = Instant.parse("2026-10-05T08:00:00Z");

    private static Workout workout() {
        return new Workout(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 5),
                NOW, "Scheda", "Sessione");
    }

    private static WorkoutSet add(WorkoutExercise exercise, String weight, Integer reps, boolean completed) {
        exercise.addSet(exercise.getSets().size() + 1, 10, false, 0);
        WorkoutSet set = exercise.getSets().getLast();
        if (completed) set.complete(NOW, weight == null ? null : new BigDecimal(weight), reps);
        return set;
    }

    @Test
    void noCompletedSetsHasNoRecordedVolume() {
        Workout w = workout();
        WorkoutExercise e = w.addExercise(null, "Panca", "Petto");
        add(e, null, null, false);
        var result = WorkoutVolume.of(w);
        assertThat(result.recordedKgReps()).isNull();
        assertThat(result.completedSets()).isZero();
        assertThat(result.missingWeightSets()).isZero();
        assertThat(result.missingRepsSets()).isZero();
    }

    @Test
    void calculatesExactDecimalsFromActualResultsAcrossExercises() {
        Workout w = workout();
        WorkoutExercise a = w.addExercise(null, "Panca", "Petto"), b = w.addExercise(null, "Squat", "Gambe");
        add(a, "32.75", 8, true);
        add(a, "0.10", 3, true);
        add(b, "1000", 1000, true);
        var result = WorkoutVolume.of(w);
        assertThat(result.recordedKgReps()).isEqualByComparingTo("1000262.30");
        assertThat(result.recordedSets()).isEqualTo(3);
        assertThat(WorkoutVolume.of(a.getSets().stream()).recordedKgReps()).isEqualByComparingTo("262.30");
    }

    @Test
    void missingFieldsOverlapButCompletedSetsAreCountedOnlyOnce() {
        Workout w = workout();
        WorkoutExercise e = w.addExercise(null, "Panca", "Petto");
        add(e, null, null, true);
        add(e, "30", null, true);
        add(e, null, 8, true);
        add(e, "30", 8, true);
        add(e, null, null, false);
        var result = WorkoutVolume.of(w);
        assertThat(result.recordedKgReps()).isEqualByComparingTo("240");
        assertThat(result.completedSets()).isEqualTo(4);
        assertThat(result.recordedSets()).isEqualTo(1);
        assertThat(result.missingWeightSets()).isEqualTo(2);
        assertThat(result.missingRepsSets()).isEqualTo(2);
    }

    @Test
    void unrecordedCompletedSetsDoNotBecomeZeroOrUsePlannedRepetitions() {
        Workout w = workout();
        WorkoutExercise e = w.addExercise(null, "Panca", "Petto");
        add(e, "30", null, true);
        var result = WorkoutVolume.of(w);
        assertThat(result.recordedKgReps()).isNull();
        assertThat(result.completedSets()).isEqualTo(1);
        assertThat(result.recordedSets()).isZero();
        assertThat(result.missingRepsSets()).isEqualTo(1);
    }

    @Test
    void explicitZeroIsRecordedAndMaxSetsUseActualRepetitions() {
        Workout w = workout();
        WorkoutExercise e = w.addExercise(null, "Trazioni", "Dorso");
        add(e, "0", 7, true);
        add(e, "30", 0, true);
        assertThat(WorkoutVolume.of(w).recordedKgReps()).isEqualByComparingTo("0");
        assertThat(WorkoutVolume.of(w).recordedSets()).isEqualTo(2);
        e.addSet(3, 0, true, 0);
        e.getSets().getLast().complete(NOW, new BigDecimal("20"), 6);
        assertThat(WorkoutVolume.of(w).recordedKgReps()).isEqualByComparingTo("120");
    }

    @Test
    void skippedAndInterruptedWorkoutsRetainResultsOfAlreadyCompletedSets() {
        Workout w = workout();
        WorkoutExercise e = w.addExercise(null, "Panca", "Petto");
        add(e, "25", 8, true);
        add(e, null, null, false);
        e.markSkipped();
        w.interrupt(NOW);
        assertThat(WorkoutVolume.of(w).recordedKgReps()).isEqualByComparingTo("200");
        assertThat(WorkoutVolume.of(w).completedSets()).isEqualTo(1);
    }
}
