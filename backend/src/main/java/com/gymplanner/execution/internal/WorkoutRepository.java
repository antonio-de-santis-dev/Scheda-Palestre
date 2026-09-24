package com.gymplanner.execution.internal;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WorkoutRepository extends JpaRepository<Workout, UUID> {

    Optional<Workout> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Row lock on the workout: concurrent "Fine serie"/"Salta" requests of the same workout are
     * serialised, which makes set completion idempotent under concurrency.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workout w where w.id = :id and w.userId = :userId")
    Optional<Workout> lockOwned(@Param("id") UUID id, @Param("userId") UUID userId);

    boolean existsByPlanAssignmentIdAndScheduledDate(UUID planAssignmentId, LocalDate scheduledDate);

    Optional<Workout> findByPlanAssignmentIdAndScheduledDate(UUID planAssignmentId, LocalDate scheduledDate);

    Optional<Workout> findFirstByUserIdAndStatus(UUID userId, WorkoutStatus status);

    List<Workout> findByPlanAssignmentIdAndStatus(UUID planAssignmentId, WorkoutStatus status);

    List<Workout> findByUserIdAndScheduledDateBetweenOrderByStartedAtAsc(UUID userId, LocalDate from, LocalDate to);

    Page<Workout> findByUserId(UUID userId, Pageable pageable);
}
