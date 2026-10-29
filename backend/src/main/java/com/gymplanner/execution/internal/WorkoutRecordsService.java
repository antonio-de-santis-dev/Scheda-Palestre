package com.gymplanner.execution.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Independent maxima of actual results, grouped only by immutable source/identity. */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkoutRecordsService {
    private final JdbcTemplate jdbc;

    record SetRecord(UUID workoutId, UUID exerciseSnapshotId, UUID setId, int setIndex, LocalDate scheduledDate,
            Instant completedAt, String planName, String sessionTitle, String exerciseName,
            BigDecimal weightKgUsed, Integer repsActual) {
    }

    record ExerciseRecords(WorkoutDtos.ExerciseIdentity identity, String exerciseName, String muscleGroupName,
            long completedSets, long recordedWeightSets, long recordedRepsSets,
            SetRecord weightRecord, SetRecord repsRecord) {
    }

    @Transactional(readOnly = true)
    public List<ExerciseRecords> get(UUID userId, WorkoutHistoryFilter filter) {
        var criteria = filter.sqlOwnedBy(userId);
        String sql = "with filtered as (select * from workouts where " + criteria.where() + "), " + """
            results as (
                select case when e.catalog_exercise_id is null then 'LEGACY' else 'CATALOG' end as source,
                    coalesce(e.catalog_exercise_id, e.legacy_exercise_id, e.id) as identity_id,
                    w.id as workout_id, e.id as exercise_snapshot_id, s.id as set_id, e.position,
                    w.scheduled_date, w.started_at, w.plan_name_snapshot, w.session_title_snapshot,
                    e.exercise_name_snapshot, e.muscle_group_name_snapshot,
                    s.set_index, s.completed_at, s.weight_kg_used, s.reps_actual
                from filtered w join workout_exercises e on e.workout_id = w.id
                    join workout_sets s on s.workout_exercise_id = e.id where s.completed_at is not null
            ), ranked as (
                select *,
                    row_number() over (partition by source, identity_id order by weight_kg_used desc nulls last, completed_at, set_id) as weight_rank,
                    row_number() over (partition by source, identity_id order by reps_actual desc nulls last, completed_at, set_id) as reps_rank,
                    row_number() over (partition by source, identity_id order by scheduled_date desc, started_at desc, workout_id desc, position, exercise_snapshot_id, set_id) as label_rank
                from results
            ), totals as (
                select source, identity_id, count(*) as completed_sets,
                    count(weight_kg_used) as weight_sets, count(reps_actual) as reps_sets
                from results group by source, identity_id
            )
            select t.*, l.exercise_name_snapshot, l.muscle_group_name_snapshot,
                a.workout_id as weight_workout_id, a.exercise_snapshot_id as weight_exercise_snapshot_id,
                a.set_id as weight_set_id, a.set_index as weight_set_index, a.scheduled_date as weight_scheduled_date,
                a.completed_at as weight_completed_at, a.plan_name_snapshot as weight_plan_name,
                a.session_title_snapshot as weight_session_title, a.exercise_name_snapshot as weight_exercise_name,
                a.weight_kg_used as weight_weight_kg_used, a.reps_actual as weight_reps_actual,
                b.workout_id as reps_workout_id, b.exercise_snapshot_id as reps_exercise_snapshot_id,
                b.set_id as reps_set_id, b.set_index as reps_set_index, b.scheduled_date as reps_scheduled_date,
                b.completed_at as reps_completed_at, b.plan_name_snapshot as reps_plan_name,
                b.session_title_snapshot as reps_session_title, b.exercise_name_snapshot as reps_exercise_name,
                b.weight_kg_used as reps_weight_kg_used, b.reps_actual as reps_reps_actual
            from totals t join ranked l on l.source = t.source and l.identity_id = t.identity_id and l.label_rank = 1
                left join ranked a on a.source = t.source and a.identity_id = t.identity_id and a.weight_rank = 1 and a.weight_kg_used is not null
                left join ranked b on b.source = t.source and b.identity_id = t.identity_id and b.reps_rank = 1 and b.reps_actual is not null
            order by lower(l.exercise_name_snapshot), lower(l.muscle_group_name_snapshot), t.source, t.identity_id
            """;
        return jdbc.query(sql, (rs, row) -> new ExerciseRecords(
                new WorkoutDtos.ExerciseIdentity(WorkoutDtos.IdentitySource.valueOf(rs.getString("source")),
                        rs.getObject("identity_id", UUID.class)), rs.getString("exercise_name_snapshot"),
                rs.getString("muscle_group_name_snapshot"), rs.getLong("completed_sets"), rs.getLong("weight_sets"),
                rs.getLong("reps_sets"), readRecord(rs, "weight_"), readRecord(rs, "reps_")), criteria.args().toArray());
    }

    private static SetRecord readRecord(ResultSet rs, String prefix) throws SQLException {
        UUID setId = rs.getObject(prefix + "set_id", UUID.class);
        if (setId == null) return null;
        return new SetRecord(rs.getObject(prefix + "workout_id", UUID.class),
                rs.getObject(prefix + "exercise_snapshot_id", UUID.class), setId, rs.getInt(prefix + "set_index"),
                rs.getDate(prefix + "scheduled_date").toLocalDate(), rs.getTimestamp(prefix + "completed_at").toInstant(),
                rs.getString(prefix + "plan_name"), rs.getString(prefix + "session_title"), rs.getString(prefix + "exercise_name"),
                rs.getBigDecimal(prefix + "weight_kg_used"), (Integer) rs.getObject(prefix + "reps_actual"));
    }
}
