package com.gymplanner.catalog.internal;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/muscle-groups")
class MuscleGroupController extends CatalogController<MuscleGroup> {

    MuscleGroupController(MuscleGroupService service) {
        super(service, "/api/admin/muscle-groups");
    }
}
