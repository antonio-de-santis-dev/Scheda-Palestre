package com.gymplanner.calendar.internal;

import com.gymplanner.assignment.api.AssignmentView;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Bulk access to the schedule events and construction of the slot planners (Task 3). */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ScheduleEvents {

    private final ScheduleEventRepository events;

    /** Every event (active or cancelled) of the given assignments, in one query. */
    Map<UUID, List<ScheduleEvent>> byAssignment(Collection<UUID> assignmentIds) {
        Map<UUID, List<ScheduleEvent>> result = new HashMap<>();
        assignmentIds.forEach(id -> result.put(id, new java.util.ArrayList<>()));
        if (!assignmentIds.isEmpty()) {
            events.findByPlanAssignmentIdIn(assignmentIds).forEach(e -> result.get(e.getPlanAssignmentId()).add(e));
        }
        return result;
    }

    static List<SlotPlanner.Change> activeChanges(List<ScheduleEvent> list) {
        return list.stream().filter(ScheduleEvent::isActive)
                .map(e -> new SlotPlanner.Change(e.getOriginalDate(), e.getTargetDate())).toList();
    }

    /** Planner of an activated assignment ({@code rotationAnchorDate} must be set). */
    static SlotPlanner planner(AssignmentView assignment, Set<Integer> days, int sessionCount, List<ScheduleEvent> list) {
        return new SlotPlanner(days, sessionCount, assignment.rotationAnchorDate(), assignment.rotationAnchorIndex(),
                activeChanges(list));
    }

    static Map<LocalDate, ScheduleEvent> activeByDate(List<ScheduleEvent> list) {
        Map<LocalDate, ScheduleEvent> result = new HashMap<>();
        list.stream().filter(ScheduleEvent::isActive).forEach(e -> result.put(e.getOriginalDate(), e));
        return result;
    }
}
