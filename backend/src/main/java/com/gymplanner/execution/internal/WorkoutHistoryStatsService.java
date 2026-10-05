package com.gymplanner.execution.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One database snapshot and aggregate row, independent of pagination and history size. */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkoutHistoryStatsService {
    private final JdbcTemplate jdbc;

    record HistoryStats(long totalWorkouts, long completedWorkouts, long interruptedWorkouts,
            long inProgressWorkouts, Long recordedDurationSeconds, long workoutsWithDuration,
            long workoutsMissingDuration, WorkoutDtos.VolumeSummary volume) {
    }

    @Transactional(readOnly = true)
    public HistoryStats get(UUID userId, WorkoutHistoryFilter filter) {
        var criteria = filter.sqlOwnedBy(userId);
        String sql = "with filtered as (select * from workouts where " + criteria.where() + "), " + """
            workout_totals as (
                select count(*) as total,
                    count(*) filter (where status = 'COMPLETED') as completed,
                    count(*) filter (where status = 'INTERRUPTED') as interrupted,
                    count(*) filter (where status = 'IN_PROGRESS') as in_progress,
                    count(*) filter (where status <> 'IN_PROGRESS' and finished_at >= started_at) as with_duration,
                    sum(floor(extract(epoch from (finished_at - started_at))))
                        filter (where status <> 'IN_PROGRESS' and finished_at >= started_at) as duration_seconds
                from filtered
            ), set_totals as (
                select count(*) as completed_sets,
                    count(*) filter (where s.weight_kg_used is not null and s.reps_actual is not null) as recorded_sets,
                    count(*) filter (where s.weight_kg_used is null) as missing_weight,
                    count(*) filter (where s.reps_actual is null) as missing_reps,
                    sum(s.weight_kg_used * s.reps_actual)
                        filter (where s.weight_kg_used is not null and s.reps_actual is not null) as volume
                from filtered w join workout_exercises e on e.workout_id = w.id
                    join workout_sets s on s.workout_exercise_id = e.id
                where s.completed_at is not null
            )
            select * from workout_totals cross join set_totals
            """;
        return jdbc.queryForObject(sql, (rs, row) -> readStats(rs), criteria.args().toArray());
    }

    record MonthlyProgress(LocalDate month, HistoryStats totals) {
    }

    @Transactional(readOnly = true)
    public List<MonthlyProgress> progress(UUID userId, WorkoutHistoryFilter filter) {
        var criteria = filter.sqlOwnedBy(userId);
        String sql = "with filtered as (select *, date_trunc('month', scheduled_date)::date as month from workouts where "
                + criteria.where() + "), " + """
            workout_months as (
                select month, count(*) as total,
                    count(*) filter (where status = 'COMPLETED') as completed,
                    count(*) filter (where status = 'INTERRUPTED') as interrupted,
                    count(*) filter (where status = 'IN_PROGRESS') as in_progress,
                    count(*) filter (where status <> 'IN_PROGRESS' and finished_at >= started_at) as with_duration,
                    sum(floor(extract(epoch from (finished_at - started_at))))
                        filter (where status <> 'IN_PROGRESS' and finished_at >= started_at) as duration_seconds
                from filtered group by month
            ), set_months as (
                select w.month, count(*) as completed_sets,
                    count(*) filter (where s.weight_kg_used is not null and s.reps_actual is not null) as recorded_sets,
                    count(*) filter (where s.weight_kg_used is null) as missing_weight,
                    count(*) filter (where s.reps_actual is null) as missing_reps,
                    sum(s.weight_kg_used * s.reps_actual)
                        filter (where s.weight_kg_used is not null and s.reps_actual is not null) as volume
                from filtered w join workout_exercises e on e.workout_id = w.id
                    join workout_sets s on s.workout_exercise_id = e.id
                where s.completed_at is not null group by w.month
            )
            select m.*, s.volume, coalesce(s.completed_sets, 0) as completed_sets,
                coalesce(s.recorded_sets, 0) as recorded_sets, coalesce(s.missing_weight, 0) as missing_weight,
                coalesce(s.missing_reps, 0) as missing_reps
            from workout_months m left join set_months s on s.month = m.month order by m.month
            """;
        return jdbc.query(sql, (rs, row) -> new MonthlyProgress(rs.getDate("month").toLocalDate(), readStats(rs)),
                criteria.args().toArray());
    }

    private static HistoryStats readStats(ResultSet rs) throws SQLException {
        var seconds = rs.getBigDecimal("duration_seconds");
        long completed = rs.getLong("completed"), interrupted = rs.getLong("interrupted");
        long withDuration = rs.getLong("with_duration");
        var volume = new WorkoutDtos.VolumeSummary(rs.getBigDecimal("volume"), rs.getInt("completed_sets"),
                rs.getInt("recorded_sets"), rs.getInt("missing_weight"), rs.getInt("missing_reps"));
        return new HistoryStats(rs.getLong("total"), completed, interrupted, rs.getLong("in_progress"),
                seconds == null ? null : seconds.longValueExact(), withDuration,
                completed + interrupted - withDuration, volume);
    }
}
