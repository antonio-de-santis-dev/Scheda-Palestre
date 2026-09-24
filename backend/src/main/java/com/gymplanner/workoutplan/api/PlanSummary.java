package com.gymplanner.workoutplan.api;

import java.util.UUID;

/** Minimal plan data for other modules. */
public record PlanSummary(UUID id, String name, boolean deleted) {
}
