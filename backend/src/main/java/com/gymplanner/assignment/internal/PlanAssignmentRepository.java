package com.gymplanner.assignment.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PlanAssignmentRepository extends JpaRepository<PlanAssignment, UUID> {

    List<PlanAssignment> findByUserIdAndActiveTrueOrderByCreatedAtAsc(UUID userId);

    boolean existsByUserIdAndWorkoutPlanIdAndActiveTrue(UUID userId, UUID workoutPlanId);

    /** The most recently closed assignment of the user: source of the optional days copy. */
    Optional<PlanAssignment> findFirstByUserIdAndActiveFalseAndEndDateIsNotNullAndIdNotOrderByCreatedAtDesc(
            UUID userId, UUID excludedId);

    List<PlanAssignment> findByWorkoutPlanIdAndActiveTrue(UUID workoutPlanId);

    List<PlanAssignment> findByWorkoutPlanIdOrderByCreatedAtDesc(UUID workoutPlanId);

    List<PlanAssignment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<PlanAssignment> findByIdAndUserId(UUID id, UUID userId);
}
