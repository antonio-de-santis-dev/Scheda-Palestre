package com.gymplanner.workoutplan.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete, ordered and resolved structure of a plan (catalog names included). Used by the
 * ADMIN editor, by the USER read-only view, by the "today" preview and to build snapshots.
 */
public record PlanStructure(
        UUID id,
        String name,
        String description,
        Integer durationWeeks,
        UUID createdBy,
        UUID copiedFromPlanId,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        long version,
        boolean executable,
        List<Session> sessions) {

    public boolean deleted() {
        return deletedAt != null;
    }

    public record Session(UUID id, String title, int position, List<Section> sections) {
    }

    public record Section(UUID id, UUID muscleGroupId, String muscleGroupName, boolean muscleGroupActive,
            int position, List<Exercise> exercises) {
    }

    /**
     * @param exerciseInSectionGroup false for a tolerated historic entry whose exercise now belongs
     *                               to another muscle group (ADR 0007)
     * @param customized             true when per-set values exist; {@code sets} always holds the N effective sets
     */
    public record Exercise(UUID id, UUID exerciseId, String exerciseName, boolean exerciseActive,
            boolean exerciseInSectionGroup, int position,
            int setsCount, int reps, boolean toFailure, int restSeconds, boolean customized, List<Set> sets, BigDecimal plannedWeightKg) {
    }

    public record Set(int setIndex, int reps, boolean toFailure, int restSeconds, BigDecimal plannedWeightKg) {
    }
}
