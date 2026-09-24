package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** State rules of spec 10.7-10.10 on the aggregate, without persistence. */
class WorkoutTransitionsTest {

    private static final Instant T0 = Instant.parse("2026-10-05T08:00:00Z");

    private static Workout workout() {
        Workout w = new Workout(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 10, 5), T0,
                "Plan", "Giorno 1");
        WorkoutExercise a = w.addExercise(null, "Panca", "Petto");
        a.addSet(1, 10, false, 60);
        a.addSet(2, 0, true, 0);
        WorkoutExercise b = w.addExercise(null, "Squat", "Gambe");
        b.addSet(1, 8, false, 90);
        return w;
    }

    @Test
    void beginPutsOnlyTheFirstExerciseInProgress() {
        Workout w = workout();
        w.begin();
        assertThat(w.getExercises()).extracting(WorkoutExercise::getStatus)
                .containsExactly(WorkoutExerciseStatus.IN_PROGRESS, WorkoutExerciseStatus.TODO);
        assertThat(w.getExercises().getFirst().getSetsPlanned()).isEqualTo(2);
        assertThat(w.getExercises().get(1).getPosition()).isEqualTo(2);
    }

    @Test
    void nextSetIsTheFirstIncompleteOne() {
        Workout w = workout();
        WorkoutExercise first = w.getExercises().getFirst();
        assertThat(first.nextSet()).contains(first.getSets().getFirst());
        first.getSets().getFirst().complete(T0);
        assertThat(first.nextSet()).contains(first.getSets().get(1));
        first.getSets().get(1).complete(T0);
        assertThat(first.nextSet()).isEmpty();
    }

    @Test
    void nextTodoSkipsFinishedExercises() {
        Workout w = workout();
        w.begin();
        w.getExercises().getFirst().markSkipped();
        assertThat(w.nextTodo()).contains(w.getExercises().get(1));
        w.getExercises().get(1).markCompleted();
        assertThat(w.nextTodo()).isEmpty();
    }

    @Test
    void timerDerivesFromTheLastCompletedSet() {
        Workout w = workout();
        w.begin();
        WorkoutSet set = w.getExercises().getFirst().getSets().getFirst();
        set.complete(T0);
        var state = WorkoutStateMapper.toState(w, T0.plusSeconds(15));
        assertThat(state.restEndsAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(state.nextAction()).isEqualTo(WorkoutDtos.NextAction.WAIT_FOR_REST);
        // Once the rest is over the timer disappears.
        var later = WorkoutStateMapper.toState(w, T0.plusSeconds(61));
        assertThat(later.restEndsAt()).isNull();
        assertThat(later.nextAction()).isEqualTo(WorkoutDtos.NextAction.COMPLETE_SET);
    }

    @Test
    void zeroRestMeansNoTimer() {
        Workout w = workout();
        w.begin();
        w.getExercises().getFirst().getSets().getFirst().complete(T0);
        w.getExercises().getFirst().getSets().get(1).complete(T0.plusSeconds(70));
        var state = WorkoutStateMapper.toState(w, T0.plusSeconds(71));
        assertThat(state.restEndsAt()).isNull();
    }

    @Test
    void finishedWorkoutsHaveNoTimerAndNoCurrentSet() {
        Workout w = workout();
        w.begin();
        w.getExercises().getFirst().getSets().getFirst().complete(T0);
        w.interrupt(T0.plusSeconds(5));
        var state = WorkoutStateMapper.toState(w, T0.plusSeconds(6));
        assertThat(state.restEndsAt()).isNull();
        assertThat(state.currentSetId()).isNull();
        assertThat(state.nextAction()).isEqualTo(WorkoutDtos.NextAction.FINISHED);
        assertThat(state.finishedAt()).isEqualTo(T0.plusSeconds(5));
    }
}
