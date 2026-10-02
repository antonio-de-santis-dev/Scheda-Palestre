package com.gymplanner.workoutplan.internal;

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
import java.util.UUID;

/** Custom values of a single set (spec 8.9). */
@Entity
@Table(name = "plan_sets")
public class PlanSet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_exercise_id", nullable = false, updatable = false)
    private PlanExercise planExercise;

    @Column(nullable = false)
    private int setIndex;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private boolean toFailure;

    @Column(nullable = false)
    private int restSeconds;

    @Column(precision = 6, scale = 2)
    private BigDecimal plannedWeightKg;

    protected PlanSet() {
    }

    PlanSet(PlanExercise planExercise, int setIndex, int reps, boolean toFailure, int restSeconds, BigDecimal plannedWeightKg) {
        this.planExercise = planExercise;
        this.setIndex = setIndex;
        this.reps = reps;
        this.toFailure = toFailure;
        this.restSeconds = restSeconds;
        this.plannedWeightKg = plannedWeightKg;
    }

    public UUID getId() {
        return id;
    }

    public int getSetIndex() {
        return setIndex;
    }

    public int getReps() {
        return reps;
    }

    public boolean isToFailure() {
        return toFailure;
    }

    public BigDecimal getPlannedWeightKg() { return plannedWeightKg; }

    public int getRestSeconds() {
        return restSeconds;
    }
}
