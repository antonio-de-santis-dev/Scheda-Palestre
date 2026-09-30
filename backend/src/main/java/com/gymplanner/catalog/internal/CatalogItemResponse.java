package com.gymplanner.catalog.internal;

import java.time.Instant;
import java.util.UUID;

/**
 * Muscle group of the catalog, with its exercise counters for the unified catalog screen.
 */
record CatalogItemResponse(UUID id, String name, boolean active, Instant createdAt, Instant updatedAt,
        long exerciseCount, long activeExerciseCount) {

    static CatalogItemResponse of(MuscleGroup group, ExerciseRepository.GroupCount count) {
        return new CatalogItemResponse(group.getId(), group.getName(), group.isActive(), group.getCreatedAt(),
                group.getUpdatedAt(), count == null ? 0 : count.getTotal(), count == null ? 0 : count.getActive());
    }
}
