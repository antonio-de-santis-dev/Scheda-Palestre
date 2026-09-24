package com.gymplanner.workoutplan.internal;

import java.util.List;

/**
 * Validated configuration of a plan exercise (spec 8.8/8.9).
 *
 * @param customSets empty (use the general values) or exactly {@code setsCount} entries 1..N
 */
record ExerciseConfig(int setsCount, int reps, boolean toFailure, int restSeconds, List<SetConfig> customSets) {

    record SetConfig(int setIndex, int reps, boolean toFailure, int restSeconds) {
    }
}
