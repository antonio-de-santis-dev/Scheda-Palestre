package com.gymplanner.execution.internal;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.execution.internal.WorkoutDtos.WorkoutSummary;
import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.api.PlanSummary;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Oggi" and "Calendario" views (US-16, spec 14.4). They combine the rotation (calendar
 * module) with the workouts, so they are orchestrated here (ADR 0002).
 */
@Service
class TodayService {

    static final int MAX_RANGE_DAYS = 62;
    private static final int LOOKAHEAD_DAYS = 28;

    enum TodayStatus {
        NO_ACTIVE_ASSIGNMENT,
        NOT_STARTED_YET,
        NO_SCHEDULE,
        PLAN_NOT_READY,
        REST_DAY,
        TRAINING_DAY
    }

    record NextTraining(LocalDate date, String sessionTitle, String planName) {
    }

    /** An active plan that still has no weekdays: the USER is guided to choose them. */
    record PlanRef(UUID assignmentId, String planName) {
    }

    /**
     * {@code assignmentId}/{@code planName} describe the plan resolved for the date: the plan that
     * trains that day (or owns the workout of that day); on other days they are filled only when
     * the user has exactly one active plan.
     */
    record TodayResponse(LocalDate date, TodayStatus status, UUID assignmentId, String planName, List<Integer> weekdays,
            PlanStructure.Session session, WorkoutSummary workout, WorkoutSummary pendingWorkout,
            NextTraining nextTraining, boolean canStart, int activePlanCount, List<PlanRef> plansWithoutDays) {
    }

    enum DayType {
        TRAINING,
        REST,
        NONE
    }

    record CalendarDay(LocalDate date, DayType type, String sessionTitle, UUID assignmentId, String planName,
            WorkoutSummary workout) {
    }

    private final WorkoutRepository workouts;
    private final AssignmentQueries assignments;
    private final CalendarQueries calendarQueries;
    private final WorkoutPlanQueries plans;
    private final BusinessCalendar calendar;

    TodayService(WorkoutRepository workouts, AssignmentQueries assignments, CalendarQueries calendarQueries,
            WorkoutPlanQueries plans, BusinessCalendar calendar) {
        this.workouts = workouts;
        this.assignments = assignments;
        this.calendarQueries = calendarQueries;
        this.plans = plans;
        this.calendar = calendar;
    }

    /**
     * ADR 0008: with several active plans the date is resolved from the weekdays. At most one plan
     * trains on a given day; when none does, the most informative state among the plans with days
     * is reported (rest day > not started yet > plan not ready), and plans without days are listed.
     */
    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId, LocalDate requested) {
        LocalDate date = requested != null ? requested : calendar.today();
        Optional<Workout> inProgress = workouts.findFirstByUserIdAndStatus(userId, WorkoutStatus.IN_PROGRESS);
        // O-04: a workout left open on another day is shown and needs an explicit choice.
        WorkoutSummary pending = inProgress.filter(w -> !w.getScheduledDate().equals(date))
                .map(WorkoutSummary::of).orElse(null);

        List<AssignmentView> active = assignments.listActiveForUser(userId);
        if (active.isEmpty()) {
            return new TodayResponse(date, TodayStatus.NO_ACTIVE_ASSIGNMENT, null, null, List.of(), null, null,
                    pending, null, false, 0, List.of());
        }
        Map<UUID, PlanSummary> names = plans.findPlans(active.stream().map(AssignmentView::planId).toList());
        Map<UUID, Set<Integer>> days = new HashMap<>();
        Map<UUID, DayPlan> dayPlans = new HashMap<>();
        for (AssignmentView a : active) {
            days.put(a.id(), calendarQueries.weekdays(a.id()));
            dayPlans.put(a.id(), calendarQueries.dayPlan(a, date));
        }
        List<PlanRef> withoutDays = active.stream().filter(a -> days.get(a.id()).isEmpty())
                .map(a -> new PlanRef(a.id(), name(names, a))).toList();

        // The plan of the day: the one that owns a workout on that date, else the one that trains.
        Map<UUID, Workout> workoutByAssignment = new HashMap<>();
        for (Workout w : workouts.findByUserIdAndScheduledDateBetweenOrderByStartedAtAsc(userId, date, date)) {
            workoutByAssignment.put(w.getPlanAssignmentId(), w);
        }
        AssignmentView chosen = active.stream().filter(a -> workoutByAssignment.containsKey(a.id())).findFirst()
                .or(() -> active.stream().filter(a -> dayPlans.get(a.id()).isTraining()).findFirst())
                .orElse(null);

        if (chosen == null) {
            TodayStatus status = aggregateStatus(active, days, dayPlans);
            AssignmentView single = active.size() == 1 ? active.getFirst() : null;
            NextTraining next = status == TodayStatus.REST_DAY || status == TodayStatus.NOT_STARTED_YET
                    ? nextTraining(active, names, date.plusDays(1)) : null;
            return new TodayResponse(date, status, single == null ? null : single.id(),
                    single == null ? null : name(names, single),
                    single == null ? List.of() : List.copyOf(days.get(single.id())), null, null, pending, next, false,
                    active.size(), withoutDays);
        }

        PlanStructure structure = plans.getStructure(chosen.planId());
        DayPlan day = dayPlans.get(chosen.id());
        Workout workoutOfDay = workoutByAssignment.get(chosen.id());
        TodayStatus status = switch (day.type()) {
            case OUT_OF_PERIOD -> TodayStatus.NOT_STARTED_YET;
            case NO_SCHEDULE -> TodayStatus.NO_SCHEDULE;
            case NO_SESSIONS -> TodayStatus.PLAN_NOT_READY;
            case REST -> TodayStatus.REST_DAY;
            case TRAINING -> structure.executable() ? TodayStatus.TRAINING_DAY : TodayStatus.PLAN_NOT_READY;
        };
        PlanStructure.Session session = status == TodayStatus.TRAINING_DAY
                ? structure.sessions().stream().filter(s -> s.id().equals(day.sessionId())).findFirst().orElse(null)
                : null;
        NextTraining next = status == TodayStatus.REST_DAY || status == TodayStatus.NOT_STARTED_YET
                ? nextTraining(active, names, date.plusDays(1)) : null;
        boolean dateAllowed = Math.abs(ChronoUnit.DAYS.between(calendar.today(), date)) <= 1;
        boolean canStart = status == TodayStatus.TRAINING_DAY && workoutOfDay == null && inProgress.isEmpty()
                && dateAllowed;
        return new TodayResponse(date, status, chosen.id(), structure.name(), List.copyOf(days.get(chosen.id())),
                session, workoutOfDay == null ? null : WorkoutSummary.of(workoutOfDay), pending, next, canStart,
                active.size(), withoutDays);
    }

    private static TodayStatus aggregateStatus(List<AssignmentView> active, Map<UUID, Set<Integer>> days,
            Map<UUID, DayPlan> dayPlans) {
        List<DayPlan.Type> types = active.stream().filter(a -> !days.get(a.id()).isEmpty())
                .map(a -> dayPlans.get(a.id()).type()).toList();
        if (types.isEmpty()) {
            return TodayStatus.NO_SCHEDULE;
        }
        if (types.contains(DayPlan.Type.REST)) {
            return TodayStatus.REST_DAY;
        }
        if (types.contains(DayPlan.Type.OUT_OF_PERIOD)) {
            return TodayStatus.NOT_STARTED_YET;
        }
        return TodayStatus.PLAN_NOT_READY;
    }

    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BadRequestException.field("to", "The end date must not precede the start date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BadRequestException("RANGE_TOO_LARGE", "The range can include at most " + MAX_RANGE_DAYS + " days");
        }
        List<AssignmentView> active = assignments.listActiveForUser(userId);
        Map<UUID, PlanSummary> names = plans.findPlans(active.stream().map(AssignmentView::planId).toList());
        Map<UUID, List<DayPlan>> ranges = new HashMap<>();
        active.forEach(a -> ranges.put(a.id(), calendarQueries.range(a, from, to)));

        List<CalendarDay> result = new ArrayList<>();
        Map<LocalDate, List<Workout>> byDate = new HashMap<>();
        for (Workout w : workouts.findByUserIdAndScheduledDateBetweenOrderByStartedAtAsc(userId, from, to)) {
            byDate.computeIfAbsent(w.getScheduledDate(), k -> new ArrayList<>()).add(w);
        }
        int i = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1), i++) {
            AssignmentView training = null;
            DayPlan trainingDay = null;
            boolean rest = false;
            for (AssignmentView a : active) {
                DayPlan plan = ranges.get(a.id()).get(i);
                if (plan.isTraining()) {
                    training = a;
                    trainingDay = plan;
                } else if (plan.type() == DayPlan.Type.REST) {
                    rest = true;
                }
            }
            DayType type = training != null ? DayType.TRAINING : rest ? DayType.REST : DayType.NONE;
            // Prefer the workout of the plan that trains that day, otherwise the latest one.
            List<Workout> ofDay = byDate.getOrDefault(d, List.of());
            UUID trainingId = training == null ? null : training.id();
            Workout w = ofDay.stream().filter(x -> x.getPlanAssignmentId().equals(trainingId)).findFirst()
                    .orElse(ofDay.isEmpty() ? null : ofDay.getLast());
            result.add(new CalendarDay(d, type, trainingDay == null ? null : trainingDay.sessionTitle(),
                    trainingId, training == null ? null : name(names, training),
                    w == null ? null : WorkoutSummary.of(w)));
        }
        return result;
    }

    private static String name(Map<UUID, PlanSummary> names, AssignmentView a) {
        PlanSummary plan = names.get(a.planId());
        return plan == null ? "?" : plan.name();
    }

    /** Earliest planned training among all active plans in the look-ahead window. */
    private NextTraining nextTraining(List<AssignmentView> active, Map<UUID, PlanSummary> names, LocalDate from) {
        NextTraining best = null;
        for (AssignmentView a : active) {
            Optional<DayPlan> first = calendarQueries.range(a, from, from.plusDays(LOOKAHEAD_DAYS - 1)).stream()
                    .filter(DayPlan::isTraining)
                    .min(Comparator.comparing(DayPlan::date));
            if (first.isPresent() && (best == null || first.get().date().isBefore(best.date()))) {
                best = new NextTraining(first.get().date(), first.get().sessionTitle(), name(names, a));
            }
        }
        return best;
    }
}
