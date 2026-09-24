package com.gymplanner.assignment.internal;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PlanAssignmentRepository extends JpaRepository<PlanAssignment, UUID> {

    Optional<PlanAssignment> findByUserIdAndActiveTrue(UUID userId);

    /** Locks the active assignment row so concurrent activations serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PlanAssignment a where a.userId = :userId and a.active = true")
    Optional<PlanAssignment> lockActiveByUser(@Param("userId") UUID userId);

    List<PlanAssignment> findByWorkoutPlanIdAndActiveTrue(UUID workoutPlanId);

    List<PlanAssignment> findByWorkoutPlanIdOrderByCreatedAtDesc(UUID workoutPlanId);

    List<PlanAssignment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PlanAssignment> findByIdAndUserId(UUID id, UUID userId);
}
