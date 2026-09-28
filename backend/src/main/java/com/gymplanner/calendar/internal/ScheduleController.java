package com.gymplanner.calendar.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-15: the USER only chooses weekdays (never a session per day), separately for each active
 * plan; a weekday belongs to at most one active plan (ADR 0008).
 */
@RestController
@RequestMapping("/api/me")
class ScheduleController {

    record ScheduleRequest(@NotNull @Size(max = 7) List<Integer> weekdays) {
    }

    record PlanScheduleResponse(UUID assignmentId, UUID planId, String planName, LocalDate startDate,
            List<Integer> weekdays) {

        static PlanScheduleResponse of(CalendarService.PlanSchedule s) {
            return new PlanScheduleResponse(s.assignmentId(), s.planId(), s.planName(), s.startDate(),
                    List.copyOf(s.weekdays()));
        }
    }

    private final CalendarService service;

    ScheduleController(CalendarService service) {
        this.service = service;
    }

    /** Every active plan with its days: the page shows which plan occupies each weekday. */
    @GetMapping("/schedules")
    List<PlanScheduleResponse> all(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.mySchedules(user.id()).stream().map(PlanScheduleResponse::of).toList();
    }

    @PutMapping("/assignments/{assignmentId}/schedule")
    PlanScheduleResponse replace(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assignmentId,
            @Valid @RequestBody ScheduleRequest body) {
        return PlanScheduleResponse.of(service.replaceSchedule(user.id(), assignmentId, body.weekdays()));
    }
}
