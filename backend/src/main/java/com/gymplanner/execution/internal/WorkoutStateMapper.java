package com.gymplanner.execution.internal;

import com.gymplanner.execution.internal.WorkoutDtos.ExerciseState;
import com.gymplanner.execution.internal.WorkoutDtos.NextAction;
import com.gymplanner.execution.internal.WorkoutDtos.SetState;
import com.gymplanner.execution.internal.WorkoutDtos.WorkoutState;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Builds the execution state; the timer is derived from server instants (spec 10.9). */
final class WorkoutStateMapper {

    private WorkoutStateMapper() {
    }

    static WorkoutState toState(Workout w, Instant serverTime) {
        List<ExerciseState> exercises = w.getExercises().stream()
                .map(e -> new ExerciseState(e.getId(), e.getPosition(), e.getStatus(), e.getExerciseNameSnapshot(),
                        e.getMuscleGroupNameSnapshot(), e.getSetsPlanned(),
                        (int) e.getSets().stream().filter(WorkoutSet::isCompleted).count(),
                        e.getSets().stream()
                                .map(s -> new SetState(s.getId(), s.getSetIndex(), s.getRepsPlanned(), s.isToFailure(),
                                        s.getRestSeconds(), s.getCompletedAt()))
                                .toList()))
                .toList();

        UUID currentExerciseId = null;
        UUID currentSetId = null;
        if (w.isInProgress()) {
            var current = w.currentExercise();
            if (current.isPresent()) {
                currentExerciseId = current.get().getId();
                currentSetId = current.get().nextSet().map(WorkoutSet::getId).orElse(null);
            }
        }

        Instant restEndsAt = null;
        Integer restSeconds = null;
        if (w.isInProgress()) {
            var last = w.lastCompletedSet();
            if (last.isPresent() && last.get().getRestSeconds() > 0) {
                Instant end = last.get().getCompletedAt().plusSeconds(last.get().getRestSeconds());
                if (end.isAfter(serverTime)) {
                    restEndsAt = end;
                    restSeconds = last.get().getRestSeconds();
                }
            }
        }

        NextAction next = !w.isInProgress() ? NextAction.FINISHED
                : restEndsAt != null ? NextAction.WAIT_FOR_REST : NextAction.COMPLETE_SET;

        return new WorkoutState(w.getId(), w.getStatus(), w.getScheduledDate(), w.getPlanNameSnapshot(),
                w.getSessionTitleSnapshot(), w.getStartedAt(), w.getFinishedAt(), exercises, currentExerciseId,
                currentSetId, restEndsAt, restSeconds, serverTime, next);
    }
}
