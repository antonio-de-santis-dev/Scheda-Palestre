package com.gymplanner.assignment.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Assignment of a shared plan to one USER (spec 8.10). Users and plans are referenced by id
 * (ADR 0002). States: pending (not active, no end date), active, closed (not active, end date).
 */
@Entity
@Table(name = "plan_assignments")
public class PlanAssignment {

    enum Status {
        PENDING,
        ACTIVE,
        CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "workout_plan_id", nullable = false, updatable = false)
    private UUID workoutPlanId;

    @Column(nullable = false, updatable = false)
    private UUID assignedBy;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    private LocalDate rotationAnchorDate;

    @Column(nullable = false)
    private int rotationAnchorIndex;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PlanAssignment() {
    }

    PlanAssignment(UUID userId, UUID workoutPlanId, UUID assignedBy, LocalDate startDate) {
        this.userId = userId;
        this.workoutPlanId = workoutPlanId;
        this.assignedBy = assignedBy;
        this.startDate = startDate;
        this.active = false;
    }

    Status status() {
        if (active) {
            return Status.ACTIVE;
        }
        return endDate == null ? Status.PENDING : Status.CLOSED;
    }

    /** Spec 10.5: the rotation starts from the first session on the later of start date and today. */
    void activate(LocalDate today) {
        this.active = true;
        this.endDate = null;
        this.rotationAnchorDate = today.isAfter(startDate) ? today : startDate;
        this.rotationAnchorIndex = 0;
    }

    /** Closing sets active=false and an end date that never precedes the start date. */
    void close(LocalDate today) {
        this.active = false;
        this.endDate = today.isBefore(startDate) ? startDate : today;
    }

    void reanchor(LocalDate date, int index) {
        this.rotationAnchorDate = date;
        this.rotationAnchorIndex = index;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getWorkoutPlanId() {
        return workoutPlanId;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getRotationAnchorDate() {
        return rotationAnchorDate;
    }

    public int getRotationAnchorIndex() {
        return rotationAnchorIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
