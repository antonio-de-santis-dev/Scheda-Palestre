package com.gymplanner.execution.internal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Aggregations of the ADMIN activity report: a fixed number of grouped queries, never one per workout. */
interface ReportRepository extends JpaRepository<Workout, UUID> {

    interface WorkoutCount {
        UUID getAssignmentId();

        WorkoutStatus getStatus();

        long getTotal();

        LocalDate getFirstDate();

        LocalDate getLastDate();
    }

    interface ExerciseCount {
        UUID getAssignmentId();

        WorkoutExerciseStatus getStatus();

        long getTotal();
    }

    interface AssignmentCount {
        UUID getAssignmentId();

        long getTotal();
    }

    @Query("""
            select w.planAssignmentId as assignmentId, w.status as status, count(w) as total,
                   min(w.scheduledDate) as firstDate, max(w.scheduledDate) as lastDate
            from Workout w where w.userId = :userId
            group by w.planAssignmentId, w.status""")
    List<WorkoutCount> workoutsByAssignmentAndStatus(@Param("userId") UUID userId);

    @Query("""
            select e.workout.planAssignmentId as assignmentId, e.status as status, count(e) as total
            from WorkoutExercise e where e.workout.userId = :userId
            group by e.workout.planAssignmentId, e.status""")
    List<ExerciseCount> exercisesByAssignmentAndStatus(@Param("userId") UUID userId);

    @Query("""
            select s.workoutExercise.workout.planAssignmentId as assignmentId, count(s) as total
            from WorkoutSet s where s.workoutExercise.workout.userId = :userId and s.completedAt is not null
            group by s.workoutExercise.workout.planAssignmentId""")
    List<AssignmentCount> completedSetsByAssignment(@Param("userId") UUID userId);

    /** Distinct sessions of the plan completed at least once (snapshot link may be NULL). */
    @Query("""
            select w.planAssignmentId as assignmentId, count(distinct w.planSessionId) as total
            from Workout w where w.userId = :userId and w.status = :status and w.planSessionId is not null
            group by w.planAssignmentId""")
    List<AssignmentCount> distinctSessionsByAssignment(@Param("userId") UUID userId,
            @Param("status") WorkoutStatus status);

    /** [weekStart, completed, interrupted] per ISO week since {@code from}. */
    @Query(value = """
            select cast(date_trunc('week', w.scheduled_date) as date) as week_start,
                   count(*) filter (where w.status = 'COMPLETED') as completed,
                   count(*) filter (where w.status = 'INTERRUPTED') as interrupted
            from workouts w where w.user_id = :userId and w.scheduled_date >= :from
            group by 1""", nativeQuery = true)
    List<Object[]> workoutsPerWeek(@Param("userId") UUID userId, @Param("from") LocalDate from);

    /** [weekStart, completed sets] per ISO week since {@code from}. */
    @Query(value = """
            select cast(date_trunc('week', w.scheduled_date) as date) as week_start, count(s.id) as sets
            from workouts w
            join workout_exercises e on e.workout_id = w.id
            join workout_sets s on s.workout_exercise_id = e.id
            where w.user_id = :userId and w.scheduled_date >= :from and s.completed_at is not null
            group by 1""", nativeQuery = true)
    List<Object[]> setsPerWeek(@Param("userId") UUID userId, @Param("from") LocalDate from);
}
