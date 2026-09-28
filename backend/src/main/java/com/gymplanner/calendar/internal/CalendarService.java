package com.gymplanner.calendar.internal;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.calendar.api.RotationCalculator;
import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.concurrency.UserLock;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanSessionRef;
import com.gymplanner.workoutplan.api.PlanSummary;
import com.gymplanner.workoutplan.api.WorkoutPlanEvents;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly days and session rotation (US-15, spec 10.6). Days belong to one assignment; a weekday
 * can be used by only one active plan of the same user (ADR 0008): the check runs under the
 * per-user advisory lock so concurrent requests from several tabs or devices cannot both win.
 * The rotation anchor is owned by the assignment module and moved through its public API.
 */
@Service
class CalendarService implements CalendarQueries {

    private static final Logger log = LoggerFactory.getLogger(CalendarService.class);

    private final WeeklyScheduleRepository schedules;
    private final AssignmentQueries assignments;
    private final WorkoutPlanQueries plans;
    private final BusinessCalendar calendar;
    private final UserLock userLock;

    CalendarService(WeeklyScheduleRepository schedules, AssignmentQueries assignments, WorkoutPlanQueries plans,
            BusinessCalendar calendar, UserLock userLock) {
        this.schedules = schedules;
        this.assignments = assignments;
        this.plans = plans;
        this.calendar = calendar;
        this.userLock = userLock;
    }

    /** The days of one active plan of the user. */
    record PlanSchedule(UUID assignmentId, UUID planId, String planName, LocalDate startDate, Set<Integer> weekdays) {
    }

    /** A requested day already used by another active plan of the same user. */
    record DayConflict(int weekday, UUID assignmentId, String planName) {
    }

    // ------------------------------------------------------------------ USER

    /** Every active plan of the user with its days (bulk queries, no N+1). */
    @Transactional(readOnly = true)
    public List<PlanSchedule> mySchedules(UUID userId) {
        return schedulesOf(assignments.listActiveForUser(userId));
    }

    /**
     * Replaces the days of one active plan of the user and re-anchors its rotation without touching
     * existing workouts (a workout in progress keeps running: it belongs to its assignment and the
     * new days apply to future dates). Days used by another active plan are refused with 409
     * {@code SCHEDULE_DAY_CONFLICT}.
     */
    @Transactional
    public PlanSchedule replaceSchedule(UUID userId, UUID assignmentId, Collection<Integer> requested) {
        Set<Integer> newDays = new TreeSet<>();
        for (Integer day : requested) {
            if (day == null || !RotationCalculator.isValidWeekday(day)) {
                throw BadRequestException.field("weekdays", "Weekdays must be between 1 (Monday) and 7 (Sunday)");
            }
            newDays.add(day);
        }
        // Other users' assignments are indistinguishable from missing ones.
        assignments.findForUser(assignmentId, userId).orElseThrow(() -> new NotFoundException("Assignment"));

        userLock.lock(userId);
        // Re-read inside the lock: the set of active plans and their days is now stable.
        List<AssignmentView> active = assignments.listActiveForUser(userId);
        AssignmentView assignment = active.stream().filter(a -> a.id().equals(assignmentId)).findFirst()
                .orElseThrow(() -> new BusinessRuleException("ASSIGNMENT_NOT_ACTIVE",
                        "Days can be chosen only for an active plan"));
        List<DayConflict> conflicts = conflicts(assignment, active, newDays);
        if (!conflicts.isEmpty()) {
            throw new ConflictException("SCHEDULE_DAY_CONFLICT",
                    "Some days are already used by another active plan of the user")
                    .with("conflicts", conflicts);
        }
        Set<Integer> oldDays = weekdays(assignment.id());
        if (!oldDays.equals(newDays)) {
            int sessionCount = plans.sessionsInOrder(assignment.planId()).size();
            reanchor(assignment, oldDays, sessionCount, index -> index);
            schedules.deleteByAssignment(assignment.id());
            newDays.forEach(day -> schedules.save(new WeeklySchedule(assignment.id(), day)));
            schedules.flush();
            log.info("Assignment id={} weekly schedule replaced", assignment.id());
        }
        return schedulesOf(List.of(assignment)).getFirst();
    }

    private List<DayConflict> conflicts(AssignmentView target, List<AssignmentView> active, Set<Integer> newDays) {
        List<AssignmentView> others = active.stream().filter(a -> !a.id().equals(target.id())).toList();
        if (others.isEmpty() || newDays.isEmpty()) {
            return List.of();
        }
        Map<UUID, PlanSummary> names = plans.findPlans(others.stream().map(AssignmentView::planId).toList());
        Map<UUID, UUID> planOf = others.stream().collect(Collectors.toMap(AssignmentView::id, AssignmentView::planId));
        return schedules.findByPlanAssignmentIdIn(planOf.keySet()).stream()
                .filter(s -> newDays.contains(s.getWeekday()))
                .sorted(java.util.Comparator.comparingInt(WeeklySchedule::getWeekday))
                .map(s -> {
                    PlanSummary plan = names.get(planOf.get(s.getPlanAssignmentId()));
                    return new DayConflict(s.getWeekday(), s.getPlanAssignmentId(), plan == null ? "?" : plan.name());
                })
                .toList();
    }

    private List<PlanSchedule> schedulesOf(List<AssignmentView> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, Set<Integer>> days = new TreeMap<>();
        list.forEach(a -> days.put(a.id(), new TreeSet<>()));
        schedules.findByPlanAssignmentIdIn(days.keySet())
                .forEach(s -> days.get(s.getPlanAssignmentId()).add(s.getWeekday()));
        Map<UUID, PlanSummary> names = plans.findPlans(list.stream().map(AssignmentView::planId).toList());
        return list.stream().map(a -> {
            PlanSummary plan = names.get(a.planId());
            return new PlanSchedule(a.id(), a.planId(), plan == null ? "?" : plan.name(), a.startDate(), days.get(a.id()));
        }).toList();
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
