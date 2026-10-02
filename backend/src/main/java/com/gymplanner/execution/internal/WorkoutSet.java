package com.gymplanner.execution.internal;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A planned set; {@code completedAt} is the single source of truth of completion (spec 8.14). */
@Entity
@Table(name = "workout_sets")
public class WorkoutSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_exercise_id", nullable = false, updatable = false)
    private WorkoutExercise workoutExercise;

    @Column(nullable = false, updatable = false)
    private int setIndex;

    @Column(nullable = false, updatable = false)
    private int repsPlanned;

    @Column(nullable = false, updatable = false)
    private boolean toFailure;

    @Column(nullable = false, updatable = false)
    private int restSeconds;

    private Instant completedAt;

    @Column(precision = 6, scale = 2, updatable = false)
    private BigDecimal weightKgPlanned;

    @Column(precision = 6, scale = 2)
    private BigDecimal weightKgUsed;

    private Integer repsActual;

    protected WorkoutSet() {
    }

    WorkoutSet(WorkoutExercise workoutExercise, int setIndex, int repsPlanned, boolean toFailure, int restSeconds, BigDecimal weightKgPlanned) {
        this.workoutExercise = workoutExercise;
        this.setIndex = setIndex;
        this.repsPlanned = repsPlanned;
        this.toFailure = toFailure;
        this.restSeconds = restSeconds;
        this.weightKgPlanned = weightKgPlanned;
    }

    boolean isCompleted() {
        return completedAt != null;
    }

    void complete(Instant now) {
        complete(now, null, null);
    }

    void complete(Instant now, BigDecimal weightKgUsed, Integer repsActual) {
        this.completedAt = now;
        this.weightKgUsed = weightKgUsed;
        this.repsActual = repsActual;
    }

    public BigDecimal getWeightKgPlanned() { return weightKgPlanned; }
    public BigDecimal getWeightKgUsed() { return weightKgUsed; }
    public Integer getRepsActual() { return repsActual; }

    public UUID getId() {
        return id;
    }

    public WorkoutExercise getWorkoutExercise() {
        return workoutExercise;
    }

    public int getSetIndex() {
        return setIndex;
    }

    public int getRepsPlanned() {
        return repsPlanned;
    }

    public boolean isToFailure() {
        return toFailure;
    }

    public int getRestSeconds() {
        return restSeconds;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
