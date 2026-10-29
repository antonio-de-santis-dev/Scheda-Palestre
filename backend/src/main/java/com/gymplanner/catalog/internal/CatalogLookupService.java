package com.gymplanner.catalog.internal;

import com.gymplanner.catalog.api.CatalogItemView;
import com.gymplanner.catalog.api.CatalogLookup;
import com.gymplanner.catalog.api.ExerciseView;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class CatalogLookupService implements CatalogLookup {

    private final MuscleGroupService muscleGroups;
    private final ExerciseService exercises;

    @Override
    public Map<UUID, CatalogItemView> muscleGroups(Collection<UUID> ids) {
        return muscleGroups.views(ids);
    }

    @Override
    public Map<UUID, ExerciseView> exercises(Collection<UUID> ids) {
        return exercises.exerciseViews(ids);
    }

    @Override
    public CatalogItemView requireSelectableMuscleGroup(UUID id) {
        return muscleGroups.requireSelectable(id);
    }

    @Override
    public ExerciseView requireSelectableExercise(UUID id, UUID muscleGroupId) {
        return exercises.requireSelectable(id, muscleGroupId);
    }
}
