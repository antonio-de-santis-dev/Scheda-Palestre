package com.gymplanner.catalog.internal;


import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
class CatalogDeletionController {

    private final CatalogDeletionService service;

    CatalogDeletionController(CatalogDeletionService service) {
        this.service = service;
    }

    @DeleteMapping("/exercises/{id}")
    ResponseEntity<Void> deleteExercise(@PathVariable UUID id) {
        service.deleteExercise(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/muscle-groups/{id}")
    ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        service.deleteGroup(id);
        return ResponseEntity.noContent().build();
    }
}
