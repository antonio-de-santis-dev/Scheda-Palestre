package com.gymplanner.workoutplan.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, UUID> {

    @Query("""
            select p from WorkoutPlan p
            where lower(p.name) like :pattern escape '\\'
              and ((:deleted = true and p.deletedAt is not null) or (:deleted = false and p.deletedAt is null))
            """)
    Page<WorkoutPlan> search(@Param("pattern") String pattern, @Param("deleted") boolean deleted, Pageable pageable);

    List<WorkoutPlan> findByIdIn(Collection<UUID> ids);

    @Query("select s from PlanSession s join fetch s.workoutPlan where s.id = :id")
    Optional<PlanSession> findSession(@Param("id") UUID id);

    @Query("select s from MuscleSection s join fetch s.planSession ps join fetch ps.workoutPlan where s.id = :id")
    Optional<MuscleSection> findSection(@Param("id") UUID id);

    @Query("""
            select e from PlanExercise e
            join fetch e.muscleSection ms join fetch ms.planSession ps join fetch ps.workoutPlan
            where e.id = :id
            """)
    Optional<PlanExercise> findPlanExercise(@Param("id") UUID id);

    @Query("select s from PlanSession s where s.workoutPlan.id = :planId order by s.position")
    List<PlanSession> findSessionsInOrder(@Param("planId") UUID planId);
}
