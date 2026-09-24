package com.gymplanner.execution.internal;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class WorkoutDtos {

    private WorkoutDtos() {
    }

    record StartWorkoutRequest(@NotNull LocalDate date) {
    }

    enum NextAction {
        /** The current set can be completed. */
        COMPLETE_SET,
        /** Rest is running; completing the next set is still allowed (O-06: informative timer). */
        WAIT_FOR_REST,
        /** The workout is completed or interrupted. */
        FINISHED
    }

    record SetState(UUID id, int setIndex, int repsPlanned, boolean toFailure, int restSeconds, Instant completedAt) {
    }

    record ExerciseState(UUID id, int position, WorkoutExerciseStatus status, String exerciseName,
            String muscleGroupName, int setsPlanned, int setsCompleted, List<SetState> sets) {
    }

    /**
     * Complete execution state returned by every execution endpoint (spec 13.6): the client
     * never has to guess what to do next. {@code restEndsAt} = completion instant of the last
     * set + its rest; the client computes the remaining time against {@code serverTime}.
     */
    record WorkoutState(UUID workoutId, WorkoutStatus status, LocalDate scheduledDate, String planName,
            String sessionTitle, Instant startedAt, Instant finishedAt, List<ExerciseState> exercises,
            UUID currentExerciseId, UUID currentSetId, Instant restEndsAt, Integer restSeconds, Instant serverTime,
            NextAction nextAction) {
    }

    record WorkoutSummary(UUID id, LocalDate scheduledDate, WorkoutStatus status, String planName,
            String sessionTitle, Instant startedAt, Instant finishedAt, int totalExercises, int completedExercises,
            int skippedExercises) {

        static WorkoutSummary of(Workout w) {
            int completed = 0;
            int skipped = 0;
            for (WorkoutExercise e : w.getExercises()) {
                if (e.getStatus() == WorkoutExerciseStatus.COMPLETED) {
                    completed++;
                } else if (e.getStatus() == WorkoutExerciseStatus.SKIPPED) {
                    skipped++;
                }
            }
            return new WorkoutSummary(w.getId(), w.getScheduledDate(), w.getStatus(), w.getPlanNameSnapshot(),
                    w.getSessionTitleSnapshot(), w.getStartedAt(), w.getFinishedAt(), w.getExercises().size(),
                    completed, skipped);
        }
    }
}
