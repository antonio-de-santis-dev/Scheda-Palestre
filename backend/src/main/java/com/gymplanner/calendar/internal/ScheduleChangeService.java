package com.gymplanner.calendar.internal;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.calendar.api.ScheduleMaintenance;
import com.gymplanner.calendar.api.WorkoutScheduleAvailability;
import com.gymplanner.shared.concurrency.UserLock;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanSessionRef;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 3: training days that were missed, skipped or moved, and their effect on the rotation.
 *
 * <p>Design decision: a missed day is detected <em>lazily</em> at the first request after the
 * end of the day (business time zone), not by a scheduled job. With one backend replica and no
 * scheduler, this is the most reliable option: every view is always consistent, the detection is
 * serialised per user by {@link UserLock} and idempotent thanks to the unique (assignment, day)
 * constraint, so refreshes or repeated calls never reschedule a day twice.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ScheduleChangeService implements ScheduleMaintenance {

    private static final Logger log = LoggerFactory.getLogger(ScheduleChangeService.class);
    /** Look-back of the detection and look-ahead of manual changes. */
    static final int WINDOW_DAYS = 62;
    private static final int PREVIEW_SLOTS = 8;

    record Preview(LocalDate date, String before, String after) {
    }

    /** What the USER can do on a date and what would change (always shown before confirming). */
    record Options(LocalDate date, String state, String sessionTitle, UUID eventId, String eventKind, LocalDate landsOn,
            boolean canSkip, boolean canMove, boolean canUndo, List<LocalDate> moveTargets, List<Preview> skipPreview,
            List<Preview> undoPreview) {
    }

    record ChangeView(UUID id, LocalDate originalDate, String kind, LocalDate targetDate, LocalDate landsOn,
            String sessionTitle, boolean active) {
    }

    private final AssignmentQueries assignments;
    private final WorkoutPlanQueries plans;
    private final CalendarService calendarService;
    private final ScheduleEvents scheduleEvents;
    private final ScheduleEventRepository events;
    private final ScheduleTrackingRepository tracking;
    private final WorkoutScheduleAvailability workouts;
    private final BusinessCalendar calendar;
    private final UserLock userLock;
    private final Clock clock;

    // ------------------------------------------------------------------ automatic detection

    @Override
    @Transactional
    public void recordMissedSessions(UUID userId) {
        LocalDate yesterday = calendar.today().minusDays(1);
        List<AssignmentView> active = assignments.listActiveForUser(userId);
        if (active.isEmpty() || upToDate(active, yesterday)) {
            return; // the common case: no lock, one query
        }
        userLock.lock(userId);
        active = assignments.listActiveForUser(userId); // re-read inside the lock
        Map<UUID, ScheduleTracking> tracked = new HashMap<>();
        tracking.findByPlanAssignmentIdIn(active.stream().map(AssignmentView::id).toList())
                .forEach(t -> tracked.put(t.getPlanAssignmentId(), t));
        for (AssignmentView assignment : active) {
            if (assignment.rotationAnchorDate() == null) continue;
            ScheduleTracking row = tracked.computeIfAbsent(assignment.id(),
                    id -> new ScheduleTracking(id, assignment.rotationAnchorDate().minusDays(1)));
            if (row.getCheckedUntil().isBefore(yesterday)) {
                detect(assignment, row.getCheckedUntil().plusDays(1), yesterday);
                row.checkedUntil(yesterday);
            }
            tracking.save(row);
        }
    }

    private boolean upToDate(List<AssignmentView> active, LocalDate yesterday) {
        Map<UUID, LocalDate> checked = new HashMap<>();
        tracking.findByPlanAssignmentIdIn(active.stream().map(AssignmentView::id).toList())
                .forEach(t -> checked.put(t.getPlanAssignmentId(), t.getCheckedUntil()));
        return active.stream().allMatch(a -> a.rotationAnchorDate() == null
                || (checked.containsKey(a.id()) && !checked.get(a.id()).isBefore(yesterday))
                || (!checked.containsKey(a.id()) && a.rotationAnchorDate().isAfter(yesterday)));
    }

    private void detect(AssignmentView assignment, LocalDate fromInclusive, LocalDate to) {
        LocalDate from = max(max(fromInclusive, assignment.rotationAnchorDate()),
                max(assignment.startDate(), to.minusDays(WINDOW_DAYS)));
        if (from.isAfter(to)) return;
        Set<Integer> days = calendarService.weekdays(assignment.id());
        List<PlanSessionRef> sessions = plans.sessionsInOrder(assignment.planId());
        if (days.isEmpty() || sessions.isEmpty()) return;
        List<ScheduleEvent> list = new ArrayList<>(scheduleEvents.byAssignment(List.of(assignment.id())).get(assignment.id()));
        Set<LocalDate> known = new HashSet<>();
        list.forEach(e -> known.add(e.getOriginalDate()));
        Set<LocalDate> trained = workouts.workoutDates(assignment.id(), from, to);
        int missed = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            SlotPlanner planner = ScheduleEvents.planner(assignment, days, sessions.size(), list);
            if (!planner.isSlot(day) || trained.contains(day) || known.contains(day)) continue;
            PlanSessionRef session = sessions.get(planner.indexAt(day));
            ScheduleEvent event = new ScheduleEvent(assignment.id(), day, ScheduleEvent.Kind.MISSED, null, session.id(),
                    session.title(), clock.instant());
            events.save(event);
            list.add(event);
            known.add(day);
            missed++;
        }
        if (missed > 0) {
            log.info("Assignment id={}: {} missed training day(s) rescheduled", assignment.id(), missed);
        }
    }

    // ------------------------------------------------------------------ manual changes

    @Transactional(readOnly = true)
    public Options options(UUID userId, UUID assignmentId, LocalDate date) {
        Context ctx = context(userId, assignmentId);
        return ctx.options(date);
    }

    @Transactional(readOnly = true)
    public List<ChangeView> changes(UUID userId, UUID assignmentId) {
        Context ctx = context(userId, assignmentId);
        LocalDate since = calendar.today().minusDays(WINDOW_DAYS);
        return ctx.list.stream().filter(e -> !e.getOriginalDate().isBefore(since))
                .sorted(Comparator.comparing(ScheduleEvent::getOriginalDate).reversed())
                .map(e -> new ChangeView(e.getId(), e.getOriginalDate(), e.getKind().name(), e.getTargetDate(),
                        e.isActive() ? ctx.planner.landsOn(e.getOriginalDate()) : null, e.getSessionTitle(), e.isActive()))
                .toList();
    }

    /** Skip ({@code target == null}) or move the session of a training day. */
    @Transactional
    public Options change(UUID userId, UUID assignmentId, LocalDate date, LocalDate target) {
        recordMissedSessions(userId);
        userLock.lock(userId);
        Context ctx = context(userId, assignmentId);
        Options options = ctx.options(date);
        if (target == null ? !options.canSkip() : !options.canMove() || !options.moveTargets().contains(target)) {
            throw new BusinessRuleException(target == null ? "SCHEDULE_DATE_NOT_ALLOWED" : "SCHEDULE_TARGET_NOT_ALLOWED",
                    "This training day cannot be changed in this way")
                    .with("date", date).with("targetDate", target);
        }
        PlanSessionRef session = ctx.sessions.get(ctx.planner.indexAt(date));
        ScheduleEvent.Kind kind = target == null ? ScheduleEvent.Kind.SKIPPED : ScheduleEvent.Kind.MOVED;
        ScheduleEvent event = events.findByPlanAssignmentIdAndOriginalDate(assignmentId, date).orElse(null);
        if (event == null) {
            events.save(new ScheduleEvent(assignmentId, date, kind, target, session.id(), session.title(), clock.instant()));
        } else {
            event.reactivate(kind, target, session.id(), session.title(), clock.instant());
        }
        events.flush();
        log.info("Assignment id={}: training day {} {}", assignmentId, date, target == null ? "skipped" : "moved to " + target);
        return context(userId, assignmentId).options(date);
    }

    /**
     * Undo. For today or a future day the event is removed and the original planning comes back.
     * For a past day the event is kept as cancelled: the day counts as used again (its session is
     * not recovered) and the automatic detection does not reschedule it a second time.
     */
    @Transactional
    public Options undo(UUID userId, UUID assignmentId, UUID eventId) {
        userLock.lock(userId);
        Context ctx = context(userId, assignmentId);
        ScheduleEvent event = events.findByIdAndPlanAssignmentId(eventId, assignmentId)
                .filter(ScheduleEvent::isActive).orElseThrow(() -> new NotFoundException("Schedule change"));
        if (!ctx.options(event.getOriginalDate()).canUndo()) {
            throw new ConflictException("SCHEDULE_CHANGE_LOCKED",
                    "The rescheduled session was already trained: the change cannot be undone");
        }
        LocalDate date = event.getOriginalDate();
        if (date.isBefore(calendar.today())) {
            event.cancel(clock.instant());
        } else {
            events.delete(event);
        }
        events.flush();
        log.info("Assignment id={}: change of {} undone", assignmentId, date);
        return context(userId, assignmentId).options(date);
    }

    // ------------------------------------------------------------------ helpers

    private Context context(UUID userId, UUID assignmentId) {
        AssignmentView assignment = assignments.findForUser(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Assignment"));
        if (!assignment.active() || assignment.rotationAnchorDate() == null) {
            throw new BusinessRuleException("ASSIGNMENT_NOT_ACTIVE", "Only an active plan can be rescheduled");
        }
        Set<Integer> days = calendarService.weekdays(assignmentId);
        if (days.isEmpty()) {
            throw new BusinessRuleException("SCHEDULE_DAYS_REQUIRED", "Save the training days first");
        }
        List<PlanSessionRef> sessions = plans.sessionsInOrder(assignment.planId());
        if (sessions.isEmpty()) {
            throw new BusinessRuleException("PLAN_NOT_EXECUTABLE", "The plan has no sessions");
        }
        List<ScheduleEvent> list = scheduleEvents.byAssignment(List.of(assignmentId)).get(assignmentId);
        Set<Integer> otherDays = new TreeSet<>();
        assignments.listActiveForUser(userId).stream().filter(a -> !a.id().equals(assignmentId))
                .forEach(a -> otherDays.addAll(calendarService.weekdays(a.id())));
        return new Context(assignment, days, sessions, list, otherDays, userId);
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    /** All the data of one assignment needed to validate and preview a change. */
    private final class Context {
        final AssignmentView assignment;
        final Set<Integer> days;
        final List<PlanSessionRef> sessions;
        final List<ScheduleEvent> list;
        final Set<Integer> otherPlanDays;
        final UUID userId;
        final SlotPlanner planner;
        final Map<LocalDate, ScheduleEvent> active;

        Context(AssignmentView assignment, Set<Integer> days, List<PlanSessionRef> sessions, List<ScheduleEvent> list,
                Set<Integer> otherPlanDays, UUID userId) {
            this.assignment = assignment;
            this.days = days;
            this.sessions = sessions;
            this.list = list;
            this.otherPlanDays = otherPlanDays;
            this.userId = userId;
            this.planner = ScheduleEvents.planner(assignment, days, sessions.size(), list);
            this.active = ScheduleEvents.activeByDate(list);
        }

        Options options(LocalDate date) {
            LocalDate today = calendar.today();
            LocalDate limit = today.plusDays(WINDOW_DAYS);
            DayPlan plan = CalendarService.plan(assignment, days, sessions, planner, active, date);
            boolean trained = workouts.hasWorkout(assignment.id(), date);
            ScheduleEvent event = active.get(date);
            boolean editable = !date.isBefore(assignment.rotationAnchorDate()) && !date.isAfter(limit) && !trained;
            boolean canSkip = editable && plan.isTraining();
            List<LocalDate> targets = canSkip && !date.isBefore(today) ? moveTargets(date, today, limit) : List.of();
            boolean canUndo = event != null && (event.getTargetDate() == null
                    || !workouts.hasWorkout(assignment.id(), event.getTargetDate()));
            List<Preview> skipPreview = canSkip ? preview(date, withChange(new SlotPlanner.Change(date, null))) : List.of();
            List<Preview> undoPreview = canUndo ? preview(date, without(date)) : List.of();
            return new Options(date, state(plan, trained), plan.sessionTitle(), event == null ? null : event.getId(),
                    event == null ? null : event.getKind().name(), plan.skip() == null ? null : plan.skip().landsOn(),
                    canSkip, !targets.isEmpty(), canUndo, targets, skipPreview, undoPreview);
        }

        private String state(DayPlan plan, boolean trained) {
            if (trained) return "TRAINED";
            if (plan.type() == DayPlan.Type.SKIPPED) return plan.skip().kind();
            if (plan.isTraining()) return plan.rescheduledFrom() != null ? "RESCHEDULED" : "PLANNED";
            return plan.type().name();
        }

        /** Free days strictly between the previous and the next training slot (the session keeps its place). */
        private List<LocalDate> moveTargets(LocalDate date, LocalDate today, LocalDate limit) {
            LocalDate previous = planner.previousSlot(date);
            LocalDate next = planner.nextSlot(date.plusDays(1));
            LocalDate from = max(today, previous == null ? date.minusDays(6) : previous.plusDays(1));
            LocalDate to = next == null ? date.plusDays(6) : next.minusDays(1);
            if (to.isAfter(limit)) to = limit;
            if (from.isAfter(to)) return List.of();
            Set<LocalDate> busy = workouts.userWorkoutDates(userId, from, to);
            List<LocalDate> result = new ArrayList<>();
            for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
                int weekday = day.getDayOfWeek().getValue();
                if (day.equals(date) || planner.wasSlot(day) || otherPlanDays.contains(weekday) || busy.contains(day)
                        || active.containsKey(day) || day.isBefore(assignment.startDate())) continue;
                result.add(day);
            }
            return result;
        }

        private SlotPlanner withChange(SlotPlanner.Change change) {
            List<SlotPlanner.Change> changes = new ArrayList<>(ScheduleEvents.activeChanges(list));
            changes.add(change);
            return new SlotPlanner(days, sessions.size(), assignment.rotationAnchorDate(), assignment.rotationAnchorIndex(), changes);
        }

        private SlotPlanner without(LocalDate original) {
            List<SlotPlanner.Change> changes = ScheduleEvents.activeChanges(list).stream()
                    .filter(c -> !c.original().equals(original)).toList();
            return new SlotPlanner(days, sessions.size(), assignment.rotationAnchorDate(), assignment.rotationAnchorIndex(), changes);
        }

        /** The next training slots from {@code date}, before and after the change. */
        private List<Preview> preview(LocalDate date, SlotPlanner after) {
            TreeSet<LocalDate> dates = new TreeSet<>();
            dates.add(date);
            collect(planner, date, dates);
            collect(after, date, dates);
            List<Preview> result = new ArrayList<>();
            for (LocalDate day : dates) {
                String before = title(planner, day), afterTitle = title(after, day);
                if (before != null || afterTitle != null) result.add(new Preview(day, before, afterTitle));
                if (result.size() >= PREVIEW_SLOTS + 1) break;
            }
            return result;
        }

        private void collect(SlotPlanner p, LocalDate from, Set<LocalDate> into) {
            LocalDate day = from;
            for (int i = 0; i < PREVIEW_SLOTS && day != null; i++) {
                day = p.nextSlot(day);
                if (day != null) {
                    into.add(day);
                    day = day.plusDays(1);
                }
            }
        }

        /** Session trained on the day, or null when the day is not a training slot. */
        private String title(SlotPlanner p, LocalDate day) {
            int index = p.sessionIndex(day);
            return index >= 0 ? sessions.get(index).title() : null;
        }
    }
}
