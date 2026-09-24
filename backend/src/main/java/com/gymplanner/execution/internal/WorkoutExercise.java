package com.gymplanner.execution.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.Optional;
import java.util.UUID;

/** Snapshot of a configured exercise inside a workout (spec 8.13). */
@Entity
@Table(name = "workout_exercises")
public class WorkoutExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_id", nullable = false, updatable = false)
    private Workout workout;

    @Column(name = "plan_exercise_id", updatable = false)
    private UUID planExerciseId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkoutExerciseStatus status;

    @Column(nullable = false, updatable = false, length = 100)
    private String exerciseNameSnapshot;

    @Column(nullable = false, updatable = false, length = 100)
    private String muscleGroupNameSnapshot;

    @Column(nullable = false, updatable = false)
    private int setsPlanned;

    @OneToMany(mappedBy = "workoutExercise", cascade = CascadeType.ALL)
    @OrderBy("setIndex")
    private List<WorkoutSet> sets = new ArrayList<>();

    protected WorkoutExercise() {
    }

    WorkoutExercise(Workout workout, UUID planExerciseId, int position, String exerciseNameSnapshot,
            String muscleGroupNameSnapshot) {
        this.workout = workout;
        this.planExerciseId = planExerciseId;
        this.position = position;
        this.exerciseNameSnapshot = exerciseNameSnapshot;
        this.muscleGroupNameSnapshot = muscleGroupNameSnapshot;
        this.status = WorkoutExerciseStatus.TODO;
    }

    void addSet(int setIndex, int repsPlanned, boolean toFailure, int restSeconds) {
        sets.add(new WorkoutSet(this, setIndex, repsPlanned, toFailure, restSeconds));
        this.setsPlanned = sets.size();
    }

    /** First set not completed yet: the only one that can be completed next. */
    Optional<WorkoutSet> nextSet() {
        return sets.stream().filter(s -> !s.isCompleted()).findFirst();
    }

    void markInProgress() {
        this.status = WorkoutExerciseStatus.IN_PROGRESS;
    }

    void markCompleted() {
        this.status = WorkoutExerciseStatus.COMPLETED;
    }

    void markSkipped() {
        this.status = WorkoutExerciseStatus.SKIPPED;
    }

    public UUID getId() {
        return id;
    }

    public Workout getWorkout() {
        return workout;
    }

    public UUID getPlanExerciseId() {
        return planExerciseId;
    }

    public int getPosition() {
        return position;
    }

    public WorkoutExerciseStatus getStatus() {
        return status;
    }

    public String getExerciseNameSnapshot() {
        return exerciseNameSnapshot;
    }

    public String getMuscleGroupNameSnapshot() {
        return muscleGroupNameSnapshot;
    }

    public int getSetsPlanned() {
        return setsPlanned;
    }

    public List<WorkoutSet> getSets() {
        return sets;
    }
}
