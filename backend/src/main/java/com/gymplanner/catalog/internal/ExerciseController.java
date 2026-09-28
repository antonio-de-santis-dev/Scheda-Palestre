package com.gymplanner.catalog.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.web.PageResponse;
import com.gymplanner.shared.web.Paging;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/exercises")
class ExerciseController {

    private static final Sort SORT = Sort.by("name");

    private final ExerciseService service;

    ExerciseController(ExerciseService service) {
        this.service = service;
    }

    /** {@code muscleGroupId} restricts the page to one group (master-detail and plan editor). */
    @GetMapping
    PageResponse<ExerciseResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) UUID muscleGroupId,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return PageResponse.of(service.search(q, active, muscleGroupId, Paging.of(page, size, SORT)),
                ExerciseResponse::of);
    }

    @PostMapping
    ResponseEntity<ExerciseResponse> create(@Valid @RequestBody ExerciseRequest body) {
        if (body.muscleGroupId() == null) {
            throw BadRequestException.field("muscleGroupId", "The muscle group is required");
        }
        Exercise exercise = service.create(body.name(), body.muscleGroupId());
        return ResponseEntity.created(URI.create("/api/admin/exercises/" + exercise.getId()))
                .body(ExerciseResponse.of(exercise));
    }

    @PutMapping("/{id}")
    ExerciseResponse update(@PathVariable UUID id, @Valid @RequestBody ExerciseRequest body) {
        return ExerciseResponse.of(service.update(id, body.name(), body.muscleGroupId()));
    }

    @PostMapping("/{id}/activate")
    ExerciseResponse activate(@PathVariable UUID id) {
        return ExerciseResponse.of(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    ExerciseResponse deactivate(@PathVariable UUID id) {
        return ExerciseResponse.of(service.setActive(id, false));
    }
}
