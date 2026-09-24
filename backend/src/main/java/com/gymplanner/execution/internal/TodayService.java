package com.gymplanner.execution.internal;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.execution.internal.WorkoutDtos.WorkoutSummary;
import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    record NextTraining(LocalDate date, String sessionTitle) {
    }

    record TodayResponse(LocalDate date, TodayStatus status, UUID assignmentId, String planName, List<Integer> weekdays,
            PlanStructure.Session session, WorkoutSummary workout, WorkoutSummary pendingWorkout,
            NextTraining nextTraining, boolean canStart) {
    }

    enum DayType {
        TRAINING,
        REST,
        NONE
    }

    record CalendarDay(LocalDate date, DayType type, String sessionTitle, WorkoutSummary workout) {
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

    @Transactional(readOnly = true)
    public TodayResponse today(UUID userId, LocalDate requested) {
        LocalDate date = requested != null ? requested : calendar.today();
        Optional<Workout> inProgress = workouts.findFirstByUserIdAndStatus(userId, WorkoutStatus.IN_PROGRESS);
        // O-04: a workout left open on another day is shown and needs an explicit choice.
        WorkoutSummary pending = inProgress.filter(w -> !w.getScheduledDate().equals(date))
                .map(WorkoutSummary::of).orElse(null);

        Optional<AssignmentView> active = assignments.findActiveForUser(userId);
        if (active.isEmpty()) {
            return new TodayResponse(date, TodayStatus.NO_ACTIVE_ASSIGNMENT, null, null, List.of(), null, null,
                    pending, null, false);
        }
        AssignmentView assignment = active.get();
        PlanStructure structure = plans.getStructure(assignment.planId());
        List<Integer> weekdays = List.copyOf(calendarQueries.weekdays(assignment.id()));
        DayPlan day = calendarQueries.dayPlan(assignment, date);
        WorkoutSummary workout = workouts.findByPlanAssignmentIdAndScheduledDate(assignment.id(), date)
                .map(WorkoutSummary::of).orElse(null);

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
                ? nextTraining(assignment, date.plusDays(1)) : null;
        boolean dateAllowed = Math.abs(ChronoUnit.DAYS.between(calendar.today(), date)) <= 1;
        boolean canStart = status == TodayStatus.TRAINING_DAY && workout == null && inProgress.isEmpty() && dateAllowed;
        return new TodayResponse(date, status, assignment.id(), structure.name(), weekdays, session, workout, pending,
                next, canStart);
    }

    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(UUID userId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw BadRequestException.field("to", "The end date must not precede the start date");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new BadRequestException("RANGE_TOO_LARGE", "The range can include at most " + MAX_RANGE_DAYS + " days");
        }
        Optional<AssignmentView> active = assignments.findActiveForUser(userId);
        Map<LocalDate, Workout> byDate = new HashMap<>();
        for (Workout w : workouts.findByUserIdAndScheduledDateBetweenOrderByStartedAtAsc(userId, from, to)) {
            // Prefer the workout of the active assignment, otherwise the latest one.
            Workout existing = byDate.get(w.getScheduledDate());
            boolean fromActive = active.map(a -> a.id().equals(w.getPlanAssignmentId())).orElse(false);
            if (existing == null || fromActive
                    || !active.map(a -> a.id().equals(existing.getPlanAssignmentId())).orElse(false)) {
                byDate.put(w.getScheduledDate(), w);
            }
        }
        List<DayPlan> plansByDay = active.map(a -> calendarQueries.range(a, from, to)).orElse(List.of());
        List<CalendarDay> result = new ArrayList<>();
        int i = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1), i++) {
            DayPlan day = plansByDay.isEmpty() ? null : plansByDay.get(i);
            DayType type = day == null ? DayType.NONE
                    : day.isTraining() ? DayType.TRAINING
                    : day.type() == DayPlan.Type.REST ? DayType.REST : DayType.NONE;
            Workout w = byDate.get(d);
            result.add(new CalendarDay(d, type, day != null && day.isTraining() ? day.sessionTitle() : null,
                    w == null ? null : WorkoutSummary.of(w)));
        }
        return result;
    }

    private NextTraining nextTraining(AssignmentView assignment, LocalDate from) {
        return calendarQueries.range(assignment, from, from.plusDays(LOOKAHEAD_DAYS - 1)).stream()
                .filter(DayPlan::isTraining)
                .min(Comparator.comparing(DayPlan::date))
                .map(d -> new NextTraining(d.date(), d.sessionTitle()))
                .orElse(null);
    }

}
