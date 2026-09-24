package com.gymplanner.workoutplan.api;

import java.util.UUID;

/**
 * Domain events published synchronously inside the transaction that caused them (ADR 0002).
 */
public final class WorkoutPlanEvents {

    private WorkoutPlanEvents() {
    }

    /** A plan was logically deleted: active assignments must be closed (US-08). */
    public record PlanDeleted(UUID planId) {
    }

    /**
     * Sessions were added, removed or reordered: rotations must be re-anchored (spec 10.6).
     *
     * @param previousSessionIds session ids in rotation order <em>before</em> the change
     */
    public record PlanSessionsChanged(UUID planId, java.util.List<UUID> previousSessionIds) {
    }
}
