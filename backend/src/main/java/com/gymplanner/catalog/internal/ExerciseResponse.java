package com.gymplanner.catalog.internal;

import java.time.Instant;
import java.util.UUID;

record ExerciseResponse(UUID id, String name, boolean active, UUID muscleGroupId, Instant createdAt,
        Instant updatedAt) {

    static ExerciseResponse of(Exercise e) {
        return new ExerciseResponse(e.getId(), e.getName(), e.isActive(), e.getMuscleGroupId(), e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
