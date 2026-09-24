package com.gymplanner.catalog.internal;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/exercises")
class ExerciseController extends CatalogController<Exercise> {

    ExerciseController(ExerciseService service) {
        super(service, "/api/admin/exercises");
    }
}
