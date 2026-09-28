package com.gymplanner.execution.internal;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.identity.api.UserDirectory;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanSummary;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN activity report (ADR 0010). Only data actually recorded by the app: assignments, days,
 * workouts by outcome, exercises and sets. No physical results (loads, body weight) are invented:
 * the app does not record them. Interpretations (rates) are left to the client and labelled.
 */
@Service
class ActivityReportService {

    static final int WEEKS = 12;

    record Totals(long workoutsCompleted, long workoutsInterrupted, long workoutsInProgress, long setsCompleted,
            long exercisesCompleted, long exercisesSkipped, LocalDate firstWorkoutDate, LocalDate lastWorkoutDate) {
    }

    record PlanActivity(UUID assignmentId, UUID planId, String planName, String status, LocalDate startDate,
            LocalDate endDate, List<Integer> weekdays, LocalDate planExpiresOn, boolean recommendedDurationEnded,
            long workoutsCompleted, long workoutsInterrupted, long workoutsInProgress, long setsCompleted,
            long exercisesCompleted, long exercisesSkipped, LocalDate lastWorkoutDate, int sessionsInPlan,
            long distinctSessionsCompleted) {
    }

    record WeekActivity(LocalDate weekStart, long workoutsCompleted, long workoutsInterrupted, long setsCompleted) {
    }

    record ActivityReport(UUID userId, LocalDate generatedOn, Totals totals, List<PlanActivity> plans,
            List<WeekActivity> weeks) {
    }

    private final ReportRepository reports;
    private final AssignmentQueries assignments;
    private final CalendarQueries calendarQueries;
    private final WorkoutPlanQueries plans;
    private final UserDirectory users;
    private final BusinessCalendar calendar;

    ActivityReportService(ReportRepository reports, AssignmentQueries assignments, CalendarQueries calendarQueries,
            WorkoutPlanQueries plans, UserDirectory users, BusinessCalendar calendar) {
        this.reports = reports;
        this.assignments = assignments;
        this.calendarQueries = calendarQueries;
        this.plans = plans;
        this.users = users;
        this.calendar = calendar;
    }

    @Transactional(readOnly = true)
    public ActivityReport report(UUID userId) {
        users.find(userId).orElseThrow(() -> new NotFoundException("User"));
        LocalDate today = calendar.today();

        List<AssignmentView> list = assignments.listAllForUser(userId);
        Map<UUID, PlanSummary> planMap = plans.findPlans(list.stream().map(AssignmentView::planId).toList());
        Map<UUID, Map<WorkoutStatus, ReportRepository.WorkoutCount>> workouts = new HashMap<>();
        for (ReportRepository.WorkoutCount c : reports.workoutsByAssignmentAndStatus(userId)) {
            workouts.computeIfAbsent(c.getAssignmentId(), k -> new HashMap<>()).put(c.getStatus(), c);
        }
        Map<UUID, Map<WorkoutExerciseStatus, Long>> exercises = new HashMap<>();
        for (ReportRepository.ExerciseCount c : reports.exercisesByAssignmentAndStatus(userId)) {
            exercises.computeIfAbsent(c.getAssignmentId(), k -> new HashMap<>()).put(c.getStatus(), c.getTotal());
        }
        Map<UUID, Long> sets = toMap(reports.completedSetsByAssignment(userId));
        Map<UUID, Long> sessions = toMap(reports.distinctSessionsByAssignment(userId, WorkoutStatus.COMPLETED));

        List<PlanActivity> planActivities = new ArrayList<>();
        for (AssignmentView a : list) {
            PlanSummary plan = planMap.get(a.planId());
            Map<WorkoutStatus, ReportRepository.WorkoutCount> w = workouts.getOrDefault(a.id(), Map.of());
            Map<WorkoutExerciseStatus, Long> e = exercises.getOrDefault(a.id(), Map.of());
            LocalDate last = w.values().stream().map(ReportRepository.WorkoutCount::getLastDate)
                    .max(Comparator.naturalOrder()).orElse(null);
            String status = a.active() ? "ACTIVE" : a.endDate() == null ? "PENDING" : "CLOSED";
            planActivities.add(new PlanActivity(a.id(), a.planId(), plan == null ? "?" : plan.name(), status,
                    a.startDate(), a.endDate(), a.active() ? List.copyOf(calendarQueries.weekdays(a.id())) : List.of(),
                    plan == null ? null : plan.expiresOn(),
                    a.active() && plan != null && plan.recommendedDurationEnded(today),
                    count(w, WorkoutStatus.COMPLETED), count(w, WorkoutStatus.INTERRUPTED),
                    count(w, WorkoutStatus.IN_PROGRESS), sets.getOrDefault(a.id(), 0L),
                    e.getOrDefault(WorkoutExerciseStatus.COMPLETED, 0L), e.getOrDefault(WorkoutExerciseStatus.SKIPPED, 0L),
                    last, plan == null || plan.deleted() ? 0 : plans.sessionsInOrder(a.planId()).size(),
                    sessions.getOrDefault(a.id(), 0L)));
        }

        LocalDate first = workouts.values().stream().flatMap(m -> m.values().stream())
                .map(ReportRepository.WorkoutCount::getFirstDate).min(Comparator.naturalOrder()).orElse(null);
        LocalDate lastAll = workouts.values().stream().flatMap(m -> m.values().stream())
                .map(ReportRepository.WorkoutCount::getLastDate).max(Comparator.naturalOrder()).orElse(null);
        Totals totals = new Totals(
                planActivities.stream().mapToLong(PlanActivity::workoutsCompleted).sum(),
                planActivities.stream().mapToLong(PlanActivity::workoutsInterrupted).sum(),
                planActivities.stream().mapToLong(PlanActivity::workoutsInProgress).sum(),
                planActivities.stream().mapToLong(PlanActivity::setsCompleted).sum(),
                planActivities.stream().mapToLong(PlanActivity::exercisesCompleted).sum(),
                planActivities.stream().mapToLong(PlanActivity::exercisesSkipped).sum(),
                first, lastAll);
        return new ActivityReport(userId, today, totals, planActivities, weeks(userId, today));
    }

    /** Last {@link #WEEKS} ISO weeks, oldest first, including weeks without activity (zeros). */
    private List<WeekActivity> weeks(UUID userId, LocalDate today) {
        LocalDate thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate from = thisWeek.minusWeeks(WEEKS - 1L);
        Map<LocalDate, long[]> byWeek = new HashMap<>();
        for (Object[] row : reports.workoutsPerWeek(userId, from)) {
            long[] v = byWeek.computeIfAbsent(date(row[0]), k -> new long[3]);
            v[0] = ((Number) row[1]).longValue();
            v[1] = ((Number) row[2]).longValue();
        }
        for (Object[] row : reports.setsPerWeek(userId, from)) {
            byWeek.computeIfAbsent(date(row[0]), k -> new long[3])[2] = ((Number) row[1]).longValue();
        }
        List<WeekActivity> result = new ArrayList<>(WEEKS);
        for (LocalDate week = from; !week.isAfter(thisWeek); week = week.plusWeeks(1)) {
            long[] v = byWeek.getOrDefault(week, new long[3]);
            result.add(new WeekActivity(week, v[0], v[1], v[2]));
        }
        return result;
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }

    private static long count(Map<WorkoutStatus, ReportRepository.WorkoutCount> map, WorkoutStatus status) {
        ReportRepository.WorkoutCount c = map.get(status);
        return c == null ? 0 : c.getTotal();
    }

    private static Map<UUID, Long> toMap(List<ReportRepository.AssignmentCount> list) {
        Map<UUID, Long> map = new HashMap<>();
        list.forEach(c -> map.put(c.getAssignmentId(), c.getTotal()));
        return map;
    }
}
