package com.gymplanner.catalog.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Create: {@code muscleGroupId} is required (checked by the service). Update: {@code null} keeps
 * the current group.
 */
record ExerciseRequest(@NotBlank @Size(max = 100) String name, UUID muscleGroupId) {
}
