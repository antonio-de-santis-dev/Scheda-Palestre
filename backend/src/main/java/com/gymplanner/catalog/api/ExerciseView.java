package com.gymplanner.catalog.api;

import java.util.UUID;

/** Read-only view of an exercise for other modules: every exercise belongs to one muscle group. */
public record ExerciseView(UUID id, String name, boolean active, UUID muscleGroupId) {
}
