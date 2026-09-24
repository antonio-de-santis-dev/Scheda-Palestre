package com.gymplanner.assignment.internal;

import com.gymplanner.assignment.internal.AssignmentDtos.ActivateRequest;
import com.gymplanner.assignment.internal.AssignmentDtos.AssignRequest;
import com.gymplanner.assignment.internal.AssignmentDtos.AssignmentResponse;
import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.workoutplan.api.PlanStructure;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN assignment endpoints (spec 13.2 / 13.5). */
@RestController
class AdminAssignmentController {

    private final AssignmentService service;

    AdminAssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/plans/{planId}/assignments")
    List<AssignmentResponse> forPlan(@PathVariable UUID planId) {
        return service.listForPlan(planId);
    }

    @GetMapping("/api/admin/users/{userId}/assignments")
    List<AssignmentResponse> forUser(@PathVariable UUID userId) {
        return service.listForUser(userId);
    }

    @PostMapping("/api/admin/assignments")
    ResponseEntity<List<AssignmentResponse>> assign(@Valid @RequestBody AssignRequest body,
            @AuthenticationPrincipal AuthenticatedUser admin) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(body, admin.id()));
    }

    @PostMapping("/api/admin/assignments/{id}/activate")
    AssignmentResponse activate(@PathVariable UUID id, @RequestBody(required = false) ActivateRequest body) {
        return service.activate(id, body == null || body.copy());
    }

    @PostMapping("/api/admin/assignments/{id}/close")
    AssignmentResponse close(@PathVariable UUID id) {
        return service.close(id);
    }
}

/** USER endpoints: the user is always taken from the session, never from the request. */
@RestController
class MyAssignmentController {

    private final AssignmentService service;

    MyAssignmentController(AssignmentService service) {
        this.service = service;
    }

    @GetMapping("/api/me/assignments")
    List<AssignmentResponse> mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.myAssignments(user.id());
    }

    @GetMapping("/api/me/assignments/{id}/plan")
    PlanStructure myPlan(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user) {
        return service.myPlan(id, user.id());
    }
}
