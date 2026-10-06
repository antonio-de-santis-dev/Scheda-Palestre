package com.gymplanner.execution.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class WorkoutDtos {

    private WorkoutDtos() {
    }

    record StartWorkoutRequest(@NotNull LocalDate date) {
    }

    record ReorderExercisesRequest(@NotEmpty List<@NotNull UUID> exerciseIds,
            @NotNull @PositiveOrZero Long expectedVersion) {
    }

    enum RestAction { PAUSE, RESUME, EXTEND, SKIP }

    record RestRequest(@NotNull RestAction action, @NotNull @PositiveOrZero Long expectedVersion,
            @NotNull @PositiveOrZero Long expectedExecutionVersion) {
    }

    /** Optional actual results; decimal validation prevents silently truncating fractional repetitions. */
    record CompleteSetRequest(
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 2) BigDecimal weightKgUsed,
            @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 0) BigDecimal repsActual) {
        Integer actualRepetitions() {
            return repsActual == null ? null : repsActual.intValueExact();
        }
    }

    record RecordSetResultsRequest(@NotNull @Valid CompleteSetRequest results,
            @NotNull @PositiveOrZero Long expectedExecutionVersion, @NotNull @PositiveOrZero Long expectedRestVersion) {
    }

    enum NextAction {
        /** The current set can be completed. */
        COMPLETE_SET,
        /** Rest is running or paused; the next set is blocked until it ends or is skipped. */
        WAIT_FOR_REST,
        /** The workout is completed or interrupted. */
        FINISHED
    }

    record SetState(UUID id, int setIndex, int repsPlanned, boolean toFailure, int restSeconds, Instant completedAt,
            BigDecimal weightKgUsed, Integer repsActual) {
    }

    /** kg × actual repetitions; null means no completed set has both recorded values. */
    record VolumeSummary(BigDecimal recordedKgReps, int completedSets, int recordedSets,
            int missingWeightSets, int missingRepsSets) {
    }

    enum IdentitySource { CATALOG, LEGACY }

    /** The source is part of the key: a legacy plan-entry UUID is never a catalog UUID. */
    record ExerciseIdentity(IdentitySource source, UUID id) {
    }

    record ExerciseState(UUID id, int position, WorkoutExerciseStatus status, String exerciseName,
            String muscleGroupName, int setsPlanned, int setsCompleted, List<SetState> sets, ExerciseIdentity identity, VolumeSummary volume) {
    }

    /**
     * Complete execution state returned by every execution endpoint (spec 13.6): the client
     * never has to guess what to do next. Running recovery uses the persisted {@code restEndsAt};
     * paused recovery uses the stored remaining milliseconds.
     */
    record WorkoutState(UUID workoutId, WorkoutStatus status, LocalDate scheduledDate, String planName,
            String sessionTitle, Instant startedAt, Instant finishedAt, List<ExerciseState> exercises,
            UUID currentExerciseId, UUID currentSetId, Instant restEndsAt, Integer restSeconds, Instant serverTime,
            NextAction nextAction, long executionVersion, Long durationSeconds, boolean restPaused, long restRemainingMillis, long restVersion, VolumeSummary volume, UUID resultEntrySetId, Instant finalResultEndsAt) {
    }

    record WorkoutSummary(UUID id, LocalDate scheduledDate, WorkoutStatus status, String planName,
            String sessionTitle, Instant startedAt, Instant finishedAt, int totalExercises, int completedExercises,
            int skippedExercises, Long durationSeconds, VolumeSummary volume) {

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
                    completed, skipped, w.durationSeconds(), WorkoutVolume.of(w));
        }
    }
}
