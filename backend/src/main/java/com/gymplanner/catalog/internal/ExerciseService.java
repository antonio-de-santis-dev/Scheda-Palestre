package com.gymplanner.catalog.internal;

import org.springframework.stereotype.Service;

@Service
class ExerciseService extends CatalogService<Exercise> {

    ExerciseService(ExerciseRepository repository) {
        super(repository, "Exercise", Exercise::new);
    }
}
