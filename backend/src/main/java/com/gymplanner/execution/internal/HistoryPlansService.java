package com.gymplanner.execution.internal;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 4: the history is organised by plan. A plan appears when it has at least one workout,
 * also after it was closed, changed or deleted; its name is the most recent snapshot stored in
 * the workouts (never the current, possibly renamed, plan).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class HistoryPlansService {

    record HistoryPlan(UUID planId, String planName, boolean active, long totalWorkouts, long completedWorkouts,
            long interruptedWorkouts, long inProgressWorkouts, LocalDate firstWorkoutDate, LocalDate lastWorkoutDate) {
    }

    private record Row(UUID assignmentId, String name, long total, long completed, long interrupted, long inProgress,
            LocalDate first, LocalDate last) {
    }

    private final JdbcTemplate jdbc;
    private final AssignmentQueries assignments;

    /** Restricts a filter to the assignments of one plan of the user (null plan = every plan). */
    @Transactional(readOnly = true)
    public WorkoutHistoryFilter scope(UUID userId, UUID planId, WorkoutHistoryFilter filter) {
        if (planId == null) {
            return filter;
        }
        return filter.forAssignments(assignments.listAllForUser(userId).stream()
                .filter(a -> a.planId().equals(planId)).map(AssignmentView::id).toList());
    }

    /** One aggregate query grouped by assignment, merged per plan: independent of the history size. */
    @Transactional(readOnly = true)
    public List<HistoryPlan> plans(UUID userId, WorkoutHistoryFilter filter) {
        var criteria = filter.sqlOwnedBy(userId);
        String sql = """
                select plan_assignment_id, count(*) as total,
                    count(*) filter (where status = 'COMPLETED') as completed,
                    count(*) filter (where status = 'INTERRUPTED') as interrupted,
                    count(*) filter (where status = 'IN_PROGRESS') as in_progress,
                    min(scheduled_date) as first_date, max(scheduled_date) as last_date,
                    (array_agg(plan_name_snapshot order by scheduled_date desc, started_at desc))[1] as latest_name
                from workouts where""" + " " + criteria.where() + " group by plan_assignment_id";
        List<Row> rows = jdbc.query(sql, (rs, i) -> new Row(rs.getObject("plan_assignment_id", UUID.class),
                rs.getString("latest_name"), rs.getLong("total"), rs.getLong("completed"), rs.getLong("interrupted"),
                rs.getLong("in_progress"), rs.getDate("first_date").toLocalDate(), rs.getDate("last_date").toLocalDate()),
                criteria.args().toArray());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, AssignmentView> byId = assignments.listAllForUser(userId).stream()
                .collect(Collectors.toMap(AssignmentView::id, Function.identity()));
        Map<UUID, List<Row>> byPlan = new LinkedHashMap<>();
        for (Row row : rows) {
            AssignmentView assignment = byId.get(row.assignmentId());
            UUID planId = assignment == null ? row.assignmentId() : assignment.planId();
            byPlan.computeIfAbsent(planId, k -> new ArrayList<>()).add(row);
        }
        List<HistoryPlan> result = new ArrayList<>();
        byPlan.forEach((planId, list) -> {
            Row latest = list.stream().max(Comparator.comparing(Row::last)).orElseThrow();
            boolean active = byId.values().stream().anyMatch(a -> a.planId().equals(planId) && a.active());
            result.add(new HistoryPlan(planId, latest.name(), active, list.stream().mapToLong(Row::total).sum(),
                    list.stream().mapToLong(Row::completed).sum(), list.stream().mapToLong(Row::interrupted).sum(),
                    list.stream().mapToLong(Row::inProgress).sum(),
                    list.stream().map(Row::first).min(Comparator.naturalOrder()).orElseThrow(), latest.last()));
        });
        result.sort(Comparator.comparing(HistoryPlan::lastWorkoutDate).reversed().thenComparing(HistoryPlan::planName));
        return result;
    }
}
