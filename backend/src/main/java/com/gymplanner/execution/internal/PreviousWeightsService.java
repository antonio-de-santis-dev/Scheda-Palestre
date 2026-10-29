package com.gymplanner.execution.internal;

import com.gymplanner.shared.error.NotFoundException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 5: weight used the last time for the same exercise and the same set number.
 *
 * <p>Only a previous workout that was really completed counts; skipped exercises, sets not
 * completed and sets without a recorded weight are ignored, and so is the current workout. The
 * exercise is matched by its stable identity (catalog id, or the legacy identity of old
 * snapshots), never by its displayed name. Each set number is resolved independently, so a plan
 * whose number of sets changed still gets a value for every set that existed before. One query
 * for the whole workout: opening the workout never issues a request per set.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class PreviousWeightsService {

    /** {@code exerciseId} is the exercise of the current workout the value refers to. */
    record PreviousWeight(UUID exerciseId, int setIndex, BigDecimal weightKgUsed, UUID workoutId, LocalDate scheduledDate) {
    }

    private final WorkoutRepository workouts;
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<PreviousWeight> previousWeights(UUID userId, UUID workoutId) {
        Workout workout = workouts.findByIdAndUserId(workoutId, userId).orElseThrow(() -> new NotFoundException("Workout"));
        Set<UUID> catalog = new HashSet<>();
        Set<UUID> legacy = new HashSet<>();
        for (WorkoutExercise exercise : workout.getExercises()) {
            WorkoutDtos.ExerciseIdentity identity = exercise.historicalIdentity();
            (identity.source() == WorkoutDtos.IdentitySource.CATALOG ? catalog : legacy).add(identity.id());
        }
        if (catalog.isEmpty() && legacy.isEmpty()) {
            return List.of();
        }
        String sql = """
                select distinct on (source, identity_id, set_index) source, identity_id, set_index, weight, workout_id, scheduled_date
                from (
                    select case when e.catalog_exercise_id is null then 'LEGACY' else 'CATALOG' end as source,
                        coalesce(e.catalog_exercise_id, e.legacy_exercise_id, e.id) as identity_id,
                        s.set_index, s.weight_kg_used as weight, w.id as workout_id, w.scheduled_date,
                        w.finished_at, s.completed_at
                    from workouts w
                    join workout_exercises e on e.workout_id = w.id
                    join workout_sets s on s.workout_exercise_id = e.id
                    where w.user_id = :userId and w.status = 'COMPLETED' and w.id <> :workoutId
                      and w.started_at < :startedAt
                      and e.status <> 'SKIPPED' and s.completed_at is not null and s.weight_kg_used is not null
                      and (e.catalog_exercise_id in (:catalog)
                           or (e.catalog_exercise_id is null and coalesce(e.legacy_exercise_id, e.id) in (:legacy)))
                ) candidates
                order by source, identity_id, set_index, scheduled_date desc, finished_at desc, completed_at desc
                """;
        Map<String, Object> params = Map.of("userId", userId, "workoutId", workoutId,
                "startedAt", Timestamp.from(workout.getStartedAt()),
                // IN () is invalid SQL: an impossible UUID keeps the clause valid for an empty side.
                "catalog", catalog.isEmpty() ? Set.of(new UUID(0, 0)) : catalog,
                "legacy", legacy.isEmpty() ? Set.of(new UUID(0, 0)) : legacy);
        List<Row> rows = jdbc.query(sql, params, (rs, i) -> new Row(rs.getString("source"),
                rs.getObject("identity_id", UUID.class), rs.getInt("set_index"), rs.getBigDecimal("weight"),
                rs.getObject("workout_id", UUID.class), rs.getDate("scheduled_date").toLocalDate()));
        List<PreviousWeight> result = new ArrayList<>();
        for (WorkoutExercise exercise : workout.getExercises()) {
            WorkoutDtos.ExerciseIdentity identity = exercise.historicalIdentity();
            for (Row row : rows) {
                if (row.source().equals(identity.source().name()) && row.identityId().equals(identity.id())) {
                    result.add(new PreviousWeight(exercise.getId(), row.setIndex(), row.weight(), row.workoutId(), row.date()));
                }
            }
        }
        return result;
    }

    private record Row(String source, UUID identityId, int setIndex, BigDecimal weight, UUID workoutId, LocalDate date) {
    }
}
