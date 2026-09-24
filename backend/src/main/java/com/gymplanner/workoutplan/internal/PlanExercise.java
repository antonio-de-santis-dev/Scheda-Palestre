package com.gymplanner.workoutplan.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Configured exercise (spec 8.8). There is no {@code customSets} column: customisation is the
 * presence of {@link PlanSet} rows (all-or-nothing).
 */
@Entity
@Table(name = "plan_exercises")
public class PlanExercise implements Positioned {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "muscle_section_id", nullable = false, updatable = false)
    private MuscleSection muscleSection;

    @Column(name = "exercise_id", nullable = false)
    private UUID exerciseId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private int setsCount;

    @Column(nullable = false)
    private int reps;

    @Column(nullable = false)
    private boolean toFailure;

    @Column(nullable = false)
    private int restSeconds;

    @OneToMany(mappedBy = "planExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setIndex")
    private List<PlanSet> sets = new ArrayList<>();

    protected PlanExercise() {
    }

    PlanExercise(MuscleSection muscleSection, UUID exerciseId, int position, ExerciseConfig config) {
        this.muscleSection = muscleSection;
        this.exerciseId = exerciseId;
        this.position = position;
        apply(exerciseId, config);
    }

    /** Replaces the whole configuration, custom sets included, in the current transaction. */
    void apply(UUID exerciseId, ExerciseConfig config) {
        this.exerciseId = exerciseId;
        this.setsCount = config.setsCount();
        this.reps = config.reps();
        this.toFailure = config.toFailure();
        this.restSeconds = config.restSeconds();
        this.sets.clear();
        for (ExerciseConfig.SetConfig set : config.customSets()) {
            sets.add(new PlanSet(this, set.setIndex(), set.reps(), set.toFailure(), set.restSeconds()));
        }
    }

    boolean isCustomized() {
        return !sets.isEmpty();
    }

    /** Effective values of every set: the custom rows, or the general values repeated. */
    List<ExerciseConfig.SetConfig> effectiveSets() {
        List<ExerciseConfig.SetConfig> result = new ArrayList<>(setsCount);
        if (isCustomized()) {
            for (PlanSet s : sets) {
                result.add(new ExerciseConfig.SetConfig(s.getSetIndex(), s.getReps(), s.isToFailure(), s.getRestSeconds()));
            }
        } else {
            for (int i = 1; i <= setsCount; i++) {
                result.add(new ExerciseConfig.SetConfig(i, reps, toFailure, restSeconds));
            }
        }
        return result;
    }

    ExerciseConfig config() {
        List<ExerciseConfig.SetConfig> custom = isCustomized() ? effectiveSets() : List.of();
        return new ExerciseConfig(setsCount, reps, toFailure, restSeconds, custom);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public MuscleSection getMuscleSection() {
        return muscleSection;
    }

    public UUID getExerciseId() {
        return exerciseId;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
    }

    public int getSetsCount() {
        return setsCount;
    }

    public int getReps() {
        return reps;
    }

    public boolean isToFailure() {
        return toFailure;
    }

    public int getRestSeconds() {
        return restSeconds;
    }

    public List<PlanSet> getSets() {
        return sets;
    }
}
