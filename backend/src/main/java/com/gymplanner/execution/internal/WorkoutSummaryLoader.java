package com.gymplanner.execution.internal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Summaries for an already owned selection, without hydrating exercise/set entity graphs. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkoutSummaryLoader {
    private final NamedParameterJdbcTemplate jdbc;

    Map<UUID, WorkoutDtos.WorkoutSummary> load(List<Workout> selected) {
        if (selected.isEmpty()) return Map.of();
        Map<UUID, Workout> byId = new HashMap<>();
        selected.forEach(workout -> byId.put(workout.getId(), workout));
        Map<UUID, WorkoutDtos.WorkoutSummary> result = new HashMap<>();
        jdbc.query("""
                with exercise_totals as (
                    select workout_id, count(*) as total,
                        count(*) filter (where status = 'COMPLETED') as completed,
                        count(*) filter (where status = 'SKIPPED') as skipped
                    from workout_exercises where workout_id in (:ids) group by workout_id
                ), set_totals as (
                    select e.workout_id, count(*) as completed_sets,
                        count(*) filter (where s.weight_kg_used is not null and s.reps_actual is not null) as recorded_sets,
                        count(*) filter (where s.weight_kg_used is null) as missing_weight,
                        count(*) filter (where s.reps_actual is null) as missing_reps,
                        sum(s.weight_kg_used * s.reps_actual) as volume
                    from workout_exercises e join workout_sets s on s.workout_exercise_id = e.id
                    where e.workout_id in (:ids) and s.completed_at is not null group by e.workout_id
                )
                select w.id, coalesce(e.total, 0) as total, coalesce(e.completed, 0) as completed,
                    coalesce(e.skipped, 0) as skipped, coalesce(s.completed_sets, 0) as completed_sets,
                    coalesce(s.recorded_sets, 0) as recorded_sets, coalesce(s.missing_weight, 0) as missing_weight,
                    coalesce(s.missing_reps, 0) as missing_reps, s.volume
                from workouts w left join exercise_totals e on e.workout_id = w.id
                    left join set_totals s on s.workout_id = w.id where w.id in (:ids)
                """, Map.of("ids", byId.keySet()), (org.springframework.jdbc.core.RowCallbackHandler) row -> {
            UUID id = row.getObject("id", UUID.class);
            Workout w = byId.get(id);
            var volume = new WorkoutDtos.VolumeSummary(row.getBigDecimal("volume"), row.getInt("completed_sets"),
                    row.getInt("recorded_sets"), row.getInt("missing_weight"), row.getInt("missing_reps"));
            result.put(id, new WorkoutDtos.WorkoutSummary(id, w.getScheduledDate(), w.getStatus(),
                    w.getPlanNameSnapshot(), w.getSessionTitleSnapshot(), w.getStartedAt(), w.getFinishedAt(),
                    row.getInt("total"), row.getInt("completed"), row.getInt("skipped"), w.durationSeconds(), volume));
        });
        return result;
    }
}
