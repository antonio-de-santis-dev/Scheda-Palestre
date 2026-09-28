package com.gymplanner.catalog.internal;

import com.gymplanner.shared.web.PageResponse;
import com.gymplanner.shared.web.Paging;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
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
@RequestMapping("/api/admin/muscle-groups")
class MuscleGroupController {

    private static final Sort SORT = Sort.by("name");

    private final MuscleGroupService service;

    MuscleGroupController(MuscleGroupService service) {
        this.service = service;
    }

    @GetMapping
    PageResponse<CatalogItemResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Page<MuscleGroup> result = service.search(q, active, Paging.of(page, size, SORT));
        Map<UUID, ExerciseRepository.GroupCount> counts = service.exerciseCounts(
                result.getContent().stream().map(MuscleGroup::getId).toList());
        return PageResponse.of(result, g -> CatalogItemResponse.of(g, counts.get(g.getId())));
    }

    @GetMapping("/{id}")
    CatalogItemResponse get(@PathVariable UUID id) {
        return respond(service.get(id));
    }

    @PostMapping
    ResponseEntity<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest body) {
        MuscleGroup group = service.create(body.name());
        return ResponseEntity.created(URI.create("/api/admin/muscle-groups/" + group.getId()))
                .body(respond(group));
    }

    @PutMapping("/{id}")
    CatalogItemResponse rename(@PathVariable UUID id, @Valid @RequestBody CatalogItemRequest body) {
        return respond(service.rename(id, body.name()));
    }

    @PostMapping("/{id}/activate")
    CatalogItemResponse activate(@PathVariable UUID id) {
        return respond(service.setActive(id, true));
    }

    /** Deactivating a group hides it from new sections; its exercises stay where they are. */
    @PostMapping("/{id}/deactivate")
    CatalogItemResponse deactivate(@PathVariable UUID id) {
        return respond(service.setActive(id, false));
    }

    private CatalogItemResponse respond(MuscleGroup group) {
        return CatalogItemResponse.of(group, service.exerciseCounts(List.of(group.getId())).get(group.getId()));
    }
}
