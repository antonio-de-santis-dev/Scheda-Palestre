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

/** Muscle-group section inside a session (spec 8.7); the group is referenced by id. */
@Entity
@Table(name = "muscle_sections")
public class MuscleSection implements Positioned {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_session_id", nullable = false, updatable = false)
    private PlanSession planSession;

    @Column(name = "muscle_group_id", nullable = false, updatable = false)
    private UUID muscleGroupId;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "muscleSection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PlanExercise> exercises = new ArrayList<>();

    protected MuscleSection() {
    }

    MuscleSection(PlanSession planSession, UUID muscleGroupId, int position) {
        this.planSession = planSession;
        this.muscleGroupId = muscleGroupId;
        this.position = position;
    }

    PlanExercise addExercise(UUID exerciseId, ExerciseConfig config) {
        PlanExercise exercise = new PlanExercise(this, exerciseId, exercises.size() + 1, config);
        exercises.add(exercise);
        return exercise;
    }

    void removeExercise(PlanExercise exercise) {
        exercises.remove(exercise);
        WorkoutPlan.renumber(exercises);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public PlanSession getPlanSession() {
        return planSession;
    }

    public UUID getMuscleGroupId() {
        return muscleGroupId;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
    }

    public List<PlanExercise> getExercises() {
        return exercises;
    }
}
