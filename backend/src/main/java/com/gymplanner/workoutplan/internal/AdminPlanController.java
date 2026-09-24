package com.gymplanner.workoutplan.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.shared.web.PageResponse;
import com.gymplanner.shared.web.Paging;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.internal.PlanDtos.CreatePlanRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.OrderRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.PlanExerciseRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.PlanListItem;
import com.gymplanner.workoutplan.internal.PlanDtos.SectionRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.SessionRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.UpdatePlanRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN plan endpoints (spec 13.4). Structure mutations return the complete updated plan. */
@RestController
@RequestMapping("/api/admin")
class AdminPlanController {

    private static final Sort SORT = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("name"));

    private final PlanService plans;
    private final PlanStructureService structure;

    AdminPlanController(PlanService plans, PlanStructureService structure) {
        this.plans = plans;
        this.structure = structure;
    }

    @GetMapping("/plans")
    PageResponse<PlanListItem> list(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean deleted, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.of(plans.search(q, deleted, Paging.of(page, size, SORT)), item -> item);
    }

    @PostMapping("/plans")
    ResponseEntity<PlanStructure> create(@Valid @RequestBody CreatePlanRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        PlanStructure plan = plans.create(body, admin.id());
        return ResponseEntity.created(URI.create("/api/admin/plans/" + plan.id())).body(plan);
    }

    @GetMapping("/plans/{id}")
    PlanStructure get(@PathVariable UUID id) {
        return plans.get(id);
    }

    @PutMapping("/plans/{id}")
    PlanStructure update(@PathVariable UUID id, @Valid @RequestBody UpdatePlanRequest body) {
        return plans.updateMetadata(id, body);
    }

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id) {
        plans.delete(id);
    }

    @PostMapping("/plans/{id}/restore")
    PlanStructure restore(@PathVariable UUID id) {
        return plans.restore(id);
    }

    @PostMapping("/plans/{id}/duplicate")
    ResponseEntity<PlanStructure> duplicate(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser admin) {
        PlanStructure copy = plans.duplicate(id, admin.id());
        return ResponseEntity.created(URI.create("/api/admin/plans/" + copy.id())).body(copy);
    }

    // -------------------------------------------------------------- sessions

    @PostMapping("/plans/{id}/sessions")
    ResponseEntity<PlanStructure> addSession(@PathVariable UUID id, @Valid @RequestBody SessionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(structure.addSession(id, body.title()));
    }

    @PutMapping("/sessions/{id}")
    PlanStructure renameSession(@PathVariable UUID id, @Valid @RequestBody SessionRequest body) {
        return structure.renameSession(id, body.title());
    }

    @DeleteMapping("/sessions/{id}")
    PlanStructure deleteSession(@PathVariable UUID id) {
        return structure.deleteSession(id);
    }

    @PutMapping("/plans/{id}/sessions/order")
    PlanStructure reorderSessions(@PathVariable UUID id, @Valid @RequestBody OrderRequest body) {
        return structure.reorderSessions(id, body.ids());
    }

    // -------------------------------------------------------------- sections

    @PostMapping("/sessions/{id}/sections")
    ResponseEntity<PlanStructure> addSection(@PathVariable UUID id, @Valid @RequestBody SectionRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(structure.addSection(id, body.muscleGroupId()));
    }

    @DeleteMapping("/sections/{id}")
    PlanStructure deleteSection(@PathVariable UUID id) {
        return structure.deleteSection(id);
    }

    @PutMapping("/sessions/{id}/sections/order")
    PlanStructure reorderSections(@PathVariable UUID id, @Valid @RequestBody OrderRequest body) {
        return structure.reorderSections(id, body.ids());
    }

    // -------------------------------------------------------------- exercises

    @PostMapping("/sections/{id}/exercises")
    ResponseEntity<PlanStructure> addExercise(@PathVariable UUID id, @Valid @RequestBody PlanExerciseRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(structure.addExercise(id, body));
    }

    @PutMapping("/plan-exercises/{id}")
    PlanStructure updateExercise(@PathVariable UUID id, @Valid @RequestBody PlanExerciseRequest body) {
        return structure.updateExercise(id, body);
    }

    @DeleteMapping("/plan-exercises/{id}")
    PlanStructure deleteExercise(@PathVariable UUID id) {
        return structure.deleteExercise(id);
    }

    @PutMapping("/sections/{id}/exercises/order")
    PlanStructure reorderExercises(@PathVariable UUID id, @Valid @RequestBody OrderRequest body) {
        return structure.reorderExercises(id, body.ids());
    }
}
