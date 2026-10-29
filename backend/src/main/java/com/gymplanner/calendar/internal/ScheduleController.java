package com.gymplanner.calendar.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ScheduleController {

    record ScheduleRequest(@NotNull @Size(max = 7) List<Integer> weekdays) {
    }

    record NextSessionRequest(@NotNull UUID sessionId) {
    }

    record SessionResponse(UUID id, String title) {
    }

    record PlanScheduleResponse(
            UUID assignmentId,
            UUID planId,
            String planName,
            LocalDate startDate,
            List<Integer> weekdays,
            List<SessionResponse> sessions) {

        static PlanScheduleResponse of(CalendarService.PlanSchedule s) {
            return new PlanScheduleResponse(
                    s.assignmentId(),
                    s.planId(),
                    s.planName(),
                    s.startDate(),
                    List.copyOf(s.weekdays()),
                    s.sessions().stream()
                            .map(session -> new SessionResponse(
                                    session.id(),
                                    session.title()))
                            .toList());
        }
    }

    private final CalendarService service;
    private final ScheduleChangeService changes;

    /** Task 3: skip ({@code targetDate} null) or move the session of a training day. */
    record ScheduleChangeRequest(@NotNull LocalDate date, LocalDate targetDate) {
    }

    /** Every active plan with its days: the page shows which plan occupies each weekday. */
    @GetMapping("/schedules")
    List<PlanScheduleResponse> all(@AuthenticationPrincipal AuthenticatedUser user) {
        changes.recordMissedSessions(user.id());
        return service.mySchedules(user.id()).stream().map(PlanScheduleResponse::of).toList();
    }

    /** Recent and future changes of one plan (missed, skipped, moved; undone ones included). */
    @GetMapping("/assignments/{assignmentId}/schedule-changes")
    List<ScheduleChangeService.ChangeView> changes(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID assignmentId) {
        changes.recordMissedSessions(user.id());
        return changes.changes(user.id(), assignmentId);
    }

    /** What can be done on a day and the consequences on the following sessions. */
    @GetMapping("/assignments/{assignmentId}/schedule-changes/options")
    ScheduleChangeService.Options options(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assignmentId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        changes.recordMissedSessions(user.id());
        return changes.options(user.id(), assignmentId, date);
    }

    @PostMapping("/assignments/{assignmentId}/schedule-changes")
    ResponseEntity<ScheduleChangeService.Options> change(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID assignmentId, @Valid @RequestBody ScheduleChangeRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(changes.change(user.id(), assignmentId, body.date(), body.targetDate()));
    }

    @DeleteMapping("/assignments/{assignmentId}/schedule-changes/{changeId}")
    ScheduleChangeService.Options undo(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assignmentId,
            @PathVariable UUID changeId) {
        return changes.undo(user.id(), assignmentId, changeId);
    }

    @PutMapping("/assignments/{assignmentId}/schedule")
    PlanScheduleResponse replace(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID assignmentId,
            @Valid @RequestBody ScheduleRequest body) {
        return PlanScheduleResponse.of(service.replaceSchedule(user.id(), assignmentId, body.weekdays()));
    }

    @PutMapping("/assignments/{assignmentId}/next-session")
    PlanScheduleResponse setNextSession(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID assignmentId,
            @Valid @RequestBody NextSessionRequest body) {

        return PlanScheduleResponse.of(
                service.setNextSession(
                        user.id(),
                        assignmentId,
                        body.sessionId()));
    }
}
