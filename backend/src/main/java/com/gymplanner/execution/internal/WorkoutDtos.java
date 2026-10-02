package com.gymplanner.execution.internal;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class WorkoutDtos {

    private WorkoutDtos() {
    }

    record StartWorkoutRequest(@NotNull LocalDate date) {
    }

    record CompleteSetRequest(
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal weightKgUsed,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 0) BigDecimal repsActual) { }

    enum RestAction { PAUSE, RESUME, EXTEND, SKIP }

    record RestRequest(@NotNull RestAction action, @NotNull @PositiveOrZero Long expectedVersion,
            @Min(1) @Max(300) Integer seconds) { }

    enum NextAction {
        /** The current set can be completed. */
        COMPLETE_SET,
        /** Running or paused recovery blocks the next set, unless explicitly skipped. */
        WAIT_FOR_REST,
        /** The workout is completed or interrupted. */
        FINISHED
    }

    record SetState(UUID id, int setIndex, int repsPlanned, boolean toFailure, int restSeconds, Instant completedAt, BigDecimal weightKgPlanned, BigDecimal weightKgUsed, Integer repsActual) {
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
            NextAction nextAction, long durationSeconds, boolean restPaused, long restRemainingSeconds,
            long restVersion) {
    }

    record WorkoutSummary(UUID id, LocalDate scheduledDate, WorkoutStatus status, String planName,
            String sessionTitle, Instant startedAt, Instant finishedAt, int totalExercises, int completedExercises,
            int skippedExercises, Long durationSeconds) {

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
                    completed, skipped, w.getFinishedAt() == null ? null : w.durationSeconds(w.getFinishedAt()));
        }
    }
}
