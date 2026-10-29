package com.gymplanner.execution.internal;

import java.math.BigDecimal;
import java.util.stream.Stream;

/** Recorded load volume, never inferred from a prescription or an unfinished set. */
final class WorkoutVolume {
    private WorkoutVolume() {
    }

    static WorkoutDtos.VolumeSummary of(Stream<WorkoutSet> sets) {
        BigDecimal sum = BigDecimal.ZERO;
        int completed = 0, recorded = 0, missingWeight = 0, missingReps = 0;
        for (WorkoutSet set : sets.toList()) {
            if (!set.isCompleted()) continue;
            completed++;
            if (set.getWeightKgUsed() == null) missingWeight++;
            if (set.getRepsActual() == null) missingReps++;
            if (set.getWeightKgUsed() != null && set.getRepsActual() != null) {
                recorded++;
                sum = sum.add(set.getWeightKgUsed().multiply(BigDecimal.valueOf(set.getRepsActual())));
            }
        }
        return new WorkoutDtos.VolumeSummary(recorded == 0 ? null : sum, completed, recorded,
                missingWeight, missingReps);
    }

    static WorkoutDtos.VolumeSummary of(Workout workout) {
        return of(workout.getExercises().stream().flatMap(e -> e.getSets().stream()));
    }
}
