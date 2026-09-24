package com.gymplanner.workoutplan.api;

import java.util.UUID;

/** A session in rotation order (position is 1-based). */
public record PlanSessionRef(UUID id, String title, int position) {
}
