package com.gymplanner.execution.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Three grouped queries, independent of the number of workouts; no entity graph/N+1. */
interface ProgressRepository extends JpaRepository<Workout, UUID> {
    @Query(value = """
        select w.scheduled_date,
            count(*) filter (where w.status = 'COMPLETED'),
            count(*) filter (where w.status = 'INTERRUPTED'),
            count(*) filter (where w.status = 'IN_PROGRESS'),
            coalesce(sum(greatest(0, floor(extract(epoch from (w.finished_at - w.started_at)))))
                filter (where w.finished_at is not null), 0),
            count(*) filter (where w.finished_at is not null)
        from workouts w where w.user_id = :userId and w.scheduled_date between :from and :to
        group by w.scheduled_date order by w.scheduled_date
        """, nativeQuery = true)
    List<Object[]> days(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    // Legacy entries are grouped by the original plan entry, never by a possibly reused name.
    String KEY = "case when e.catalog_exercise_id is not null then 'catalog:' || cast(e.catalog_exercise_id as text) "
        + "else 'legacy:' || cast(coalesce(e.legacy_exercise_id, e.id) as text) end";

    @Query(value = "select " + KEY + """
        , (array_agg(e.exercise_name_snapshot order by w.started_at desc, e.id))[1],
        (array_agg(e.muscle_group_name_snapshot order by w.started_at desc, e.id))[1],
        w.scheduled_date, count(s.id),
        count(s.id) filter (where s.weight_kg_used is not null and s.reps_actual is not null),
        sum(s.weight_kg_used * s.reps_actual), max(s.weight_kg_used), max(s.reps_actual)
        from workouts w join workout_exercises e on e.workout_id = w.id
        join workout_sets s on s.workout_exercise_id = e.id
        where w.user_id = :userId and w.scheduled_date between :from and :to and s.completed_at is not null
        group by 1, w.scheduled_date order by w.scheduled_date, 1
        """, nativeQuery = true)
    List<Object[]> exerciseDays(@Param("userId") UUID userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(value = "select " + KEY + """
        , (array_agg(e.exercise_name_snapshot order by w.started_at desc, e.id))[1],
        (array_agg(e.muscle_group_name_snapshot order by w.started_at desc, e.id))[1],
        max(s.weight_kg_used), max(s.reps_actual)
        from workouts w join workout_exercises e on e.workout_id = w.id
        join workout_sets s on s.workout_exercise_id = e.id
        where w.user_id = :userId and s.completed_at is not null
        and (s.weight_kg_used is not null or s.reps_actual is not null)
        group by 1 order by 2, 1
        """, nativeQuery = true)
    List<Object[]> records(@Param("userId") UUID userId);
}
