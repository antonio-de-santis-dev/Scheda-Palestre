package com.gymplanner.catalog.internal;

import org.springframework.stereotype.Service;

@Service
class MuscleGroupService extends CatalogService<MuscleGroup> {

    MuscleGroupService(MuscleGroupRepository repository) {
        super(repository, "Muscle group", MuscleGroup::new);
    }
}
