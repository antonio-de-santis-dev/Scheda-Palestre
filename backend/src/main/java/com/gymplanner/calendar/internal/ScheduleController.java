package com.gymplanner.calendar.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** US-15: the USER only chooses weekdays (never a session per day). */
@RestController
@RequestMapping("/api/me/schedule")
class ScheduleController {

    record ScheduleRequest(@NotNull @Size(max = 7) List<Integer> weekdays) {
    }

    record ScheduleResponse(UUID assignmentId, List<Integer> weekdays) {

        static ScheduleResponse of(CalendarService.Schedule schedule) {
            return new ScheduleResponse(schedule.assignmentId(), List.copyOf(schedule.weekdays()));
        }
    }

    private final CalendarService service;

    ScheduleController(CalendarService service) {
        this.service = service;
    }

    @GetMapping
    ScheduleResponse get(@AuthenticationPrincipal AuthenticatedUser user) {
        return ScheduleResponse.of(service.mySchedule(user.id()));
    }

    @PutMapping
    ScheduleResponse replace(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody ScheduleRequest body) {
        return ScheduleResponse.of(service.replaceMySchedule(user.id(), body.weekdays()));
    }
}
