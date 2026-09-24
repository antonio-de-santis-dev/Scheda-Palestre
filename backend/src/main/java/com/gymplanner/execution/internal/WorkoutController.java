package com.gymplanner.execution.internal;

import com.gymplanner.execution.internal.WorkoutDtos.StartWorkoutRequest;
import com.gymplanner.execution.internal.WorkoutDtos.WorkoutState;
import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** USER execution endpoints (spec 13.6). The user always comes from the session. */
@RestController
@RequestMapping("/api/me")
class WorkoutController {

    private final WorkoutService workouts;
    private final TodayService today;

    WorkoutController(WorkoutService workouts, TodayService today) {
        this.workouts = workouts;
        this.today = today;
    }

    @GetMapping("/today")
    TodayService.TodayResponse today(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return today.today(user.id(), date);
    }

    @GetMapping("/calendar")
    List<TodayService.CalendarDay> calendar(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return today.calendar(user.id(), from, to);
    }

    @PostMapping("/workouts")
    ResponseEntity<WorkoutState> start(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartWorkoutRequest body) {
        WorkoutState state = workouts.start(user.id(), body.date());
        return ResponseEntity.created(URI.create("/api/me/workouts/" + state.workoutId())).body(state);
    }

    /** 204 when nothing is in progress. */
    @GetMapping("/workouts/current")
    ResponseEntity<WorkoutState> current(@AuthenticationPrincipal AuthenticatedUser user) {
        return workouts.current(user.id()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Full state of one of the user's workouts (resume after reload, history detail). */
    @GetMapping("/workouts/{id}")
    WorkoutState get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return workouts.get(user.id(), id);
    }

    @PostMapping("/workouts/{id}/sets/{setId}/complete")
    WorkoutState completeSet(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @PathVariable UUID setId) {
        return workouts.completeSet(user.id(), id, setId);
    }

    @PostMapping("/workouts/{id}/exercises/{exerciseId}/skip")
    WorkoutState skip(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @PathVariable UUID exerciseId) {
        return workouts.skipExercise(user.id(), id, exerciseId);
    }

    @PostMapping("/workouts/{id}/interrupt")
    WorkoutState interrupt(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return workouts.interrupt(user.id(), id);
    }
}
