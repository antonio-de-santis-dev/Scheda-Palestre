package com.gymplanner.calendar.internal;

import com.gymplanner.assignment.api.AssignmentEvents;
import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.calendar.api.RotationCalculator;
import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanSessionRef;
import com.gymplanner.workoutplan.api.WorkoutPlanEvents;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly days and session rotation (US-15, spec 10.6). The rotation anchor is owned by the
 * assignment module and moved through its public API.
 */
@Service
class CalendarService implements CalendarQueries {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private final WeeklyScheduleRepository schedules;
    private final AssignmentQueries assignments;
    private final WorkoutPlanQueries plans;
    private final BusinessCalendar calendar;

    CalendarService(WeeklyScheduleRepository schedules, AssignmentQueries assignments, WorkoutPlanQueries plans,
            BusinessCalendar calendar) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.plans = plans;
        this.calendar = calendar;
    }

    record Schedule(UUID assignmentId, Set<Integer> weekdays) {
    }

    // ------------------------------------------------------------------ USER

    @Transactional(readOnly = true)
    public Schedule mySchedule(UUID userId) {
        return assignments.findActiveForUser(userId)
                .map(a -> new Schedule(a.id(), weekdays(a.id())))
                .orElse(new Schedule(null, Set.of()));
    }

    /** Replaces the days and re-anchors the rotation without touching existing workouts. */
    @Transactional
    public Schedule replaceMySchedule(UUID userId, Collection<Integer> requested) {
        AssignmentView assignment = assignments.findActiveForUser(userId)
                .orElseThrow(() -> new BusinessRuleException("NO_ACTIVE_ASSIGNMENT", "No active plan assignment"));
        Set<Integer> newDays = new TreeSet<>();
        for (Integer day : requested) {
            if (day == null || !RotationCalculator.isValidWeekday(day)) {
                throw BadRequestException.field("weekdays", "Weekdays must be between 1 (Monday) and 7 (Sunday)");
            }
            newDays.add(day);
        }
        Set<Integer> oldDays = weekdays(assignment.id());
        if (oldDays.equals(newDays)) {
            return new Schedule(assignment.id(), oldDays);
        }
        int sessionCount = plans.sessionsInOrder(assignment.planId()).size();
        reanchor(assignment, oldDays, sessionCount, index -> index);
        schedules.deleteByAssignment(assignment.id());
        newDays.forEach(day -> schedules.save(new WeeklySchedule(assignment.id(), day)));
        schedules.flush();
        log.info("Assignment id={} weekly schedule replaced", assignment.id());
        return new Schedule(assignment.id(), newDays);
    }

    // ------------------------------------------------------------------ events

    /** O-07: copy the days of the replaced assignment (the user can change them later). */
    @EventListener
    @Transactional
    public void onAssignmentActivated(AssignmentEvents.AssignmentActivated event) {
        if (!event.copySchedule() || event.previousAssignmentId() == null) {
            return;
        }
        if (!schedules.findByPlanAssignmentIdOrderByWeekday(event.assignmentId()).isEmpty()) {
            return;
        }
        for (Integer day : weekdays(event.previousAssignmentId())) {
            schedules.save(new WeeklySchedule(event.assignmentId(), day));
        }
    }

    /**
     * Sessions of a plan were added/removed/reordered: every active rotation is re-anchored so
     * that the session due next stays the same session when it still exists.
     */
    @EventListener
    @Transactional
    public void onPlanSessionsChanged(WorkoutPlanEvents.PlanSessionsChanged event) {
        List<UUID> before = event.previousSessionIds();
        List<UUID> after = plans.sessionsInOrder(event.planId()).stream().map(PlanSessionRef::id).toList();
        for (AssignmentView assignment : assignments.findActiveByPlan(event.planId())) {
            reanchor(assignment, weekdays(assignment.id()), before.size(), oldIndex -> {
                if (after.isEmpty()) {
                    return 0;
                }
                if (oldIndex >= 0 && oldIndex < before.size()) {
                    int moved = after.indexOf(before.get(oldIndex));
                    if (moved >= 0) {
                        return moved;
                    }
                }
                return oldIndex >= 0 && oldIndex < after.size() ? oldIndex : 0;
            });
        }
    }

    /**
     * Re-anchoring (spec 10.6). The anchor moves to today, or to tomorrow when today is already a
     * training day under the old rules (today's session keeps its meaning). The new anchor index
     * is the old rotation position at the new anchor date, mapped by {@code mapper}.
     */
    private void reanchor(AssignmentView assignment, Set<Integer> oldDays, int oldSessionCount,
            java.util.function.IntUnaryOperator mapper) {
        LocalDate today = calendar.today();
        LocalDate anchor = assignment.rotationAnchorDate();
        if (anchor == null) {
            return;
        }
        if (anchor.isAfter(today)) {
            // Rotation not started yet: only the index may need mapping.
            int mapped = mapper.applyAsInt(assignment.rotationAnchorIndex());
            assignments.updateRotationAnchor(assignment.id(), anchor, Math.max(mapped, 0));
            return;
        }
        LocalDate newAnchor = RotationCalculator.isScheduled(oldDays, today) ? today.plusDays(1) : today;
        long k = RotationCalculator.countScheduledDays(oldDays, anchor, newAnchor);
        int oldIndex = oldSessionCount > 0
                ? (int) Math.floorMod(assignment.rotationAnchorIndex() + k, (long) oldSessionCount)
                : 0;
        int newIndex = Math.max(mapper.applyAsInt(oldIndex), 0);
        assignments.updateRotationAnchor(assignment.id(), newAnchor, newIndex);
    }

    // ------------------------------------------------------------------ queries

    @Override
    @Transactional(readOnly = true)
    public Set<Integer> weekdays(UUID assignmentId) {
        return schedules.findByPlanAssignmentIdOrderByWeekday(assignmentId).stream()
                .map(WeeklySchedule::getWeekday)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Override
    @Transactional(readOnly = true)
    public DayPlan dayPlan(AssignmentView assignment, LocalDate date) {
        return range(assignment, date, date).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DayPlan> range(AssignmentView assignment, LocalDate from, LocalDate to) {
        Set<Integer> days = weekdays(assignment.id());
        List<PlanSessionRef> sessions = plans.sessionsInOrder(assignment.planId());
        List<DayPlan> result = new ArrayList<>((int) ChronoUnit.DAYS.between(from, to) + 1);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.add(plan(assignment, days, sessions, d));
        }
        return result;
    }

    private static DayPlan plan(AssignmentView a, Set<Integer> days, List<PlanSessionRef> sessions, LocalDate d) {
        boolean inPeriod = a.active() && !d.isBefore(a.startDate()) && (a.endDate() == null || !d.isAfter(a.endDate()))
                && a.rotationAnchorDate() != null;
        if (!inPeriod) {
            return DayPlan.of(d, DayPlan.Type.OUT_OF_PERIOD);
        }
        if (days.isEmpty()) {
            return DayPlan.of(d, DayPlan.Type.NO_SCHEDULE);
        }
        if (sessions.isEmpty()) {
            return DayPlan.of(d, DayPlan.Type.NO_SESSIONS);
        }
        int index = RotationCalculator.sessionIndex(days, sessions.size(), a.rotationAnchorDate(),
                a.rotationAnchorIndex(), d);
        if (index < 0) {
            return DayPlan.of(d, DayPlan.Type.REST);
        }
        PlanSessionRef session = sessions.get(index);
        return new DayPlan(d, DayPlan.Type.TRAINING, session.id(), session.title(), index);
    }
}
