package com.gymplanner.catalog.internal;

import com.gymplanner.catalog.api.CatalogItemView;
import com.gymplanner.catalog.api.CatalogLookup;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class CatalogLookupService implements CatalogLookup {

    private final MuscleGroupService muscleGroups;
    private final ExerciseService exercises;

    CatalogLookupService(MuscleGroupService muscleGroups, ExerciseService exercises) {
        this.muscleGroups = muscleGroups;
        this.exercises = exercises;
    }

    @Override
    public Map<UUID, CatalogItemView> muscleGroups(Collection<UUID> ids) {
        return muscleGroups.views(ids);
    }

    @Override
    public Map<UUID, CatalogItemView> exercises(Collection<UUID> ids) {
        return exercises.views(ids);
    }

    @Override
    public CatalogItemView requireSelectableMuscleGroup(UUID id) {
        return muscleGroups.requireSelectable(id);
    }

    @Override
    public CatalogItemView requireSelectableExercise(UUID id) {
        return exercises.requireSelectable(id);
    }
}
