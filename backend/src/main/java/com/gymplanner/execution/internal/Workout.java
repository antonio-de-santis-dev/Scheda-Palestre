package com.gymplanner.execution.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A workout and its immutable snapshot (spec 8.12). All state transitions of spec 10.8/10.10
 * live here so they can be unit tested without a database.
 */
@Entity
@Table(name = "workouts")
public class Workout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "plan_assignment_id", nullable = false, updatable = false)
    private UUID planAssignmentId;

    /** Origin session; the database sets it to null if the session is deleted. */
    @Column(name = "plan_session_id", updatable = false)
    private UUID planSessionId;

    @Column(nullable = false, updatable = false)
    private LocalDate scheduledDate;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkoutStatus status;

    @Column(nullable = false, updatable = false, length = 100)
    private String planNameSnapshot;

    @Column(nullable = false, updatable = false, length = 60)
    private String sessionTitleSnapshot;

    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL)
    @OrderBy("position")
    private List<WorkoutExercise> exercises = new ArrayList<>();

    protected Workout() {
    }

    Workout(UUID userId, UUID planAssignmentId, UUID planSessionId, LocalDate scheduledDate, Instant startedAt,
            String planNameSnapshot, String sessionTitleSnapshot) {
        this.userId = userId;
        this.planAssignmentId = planAssignmentId;
        this.planSessionId = planSessionId;
        this.scheduledDate = scheduledDate;
        this.startedAt = startedAt;
        this.planNameSnapshot = planNameSnapshot;
        this.sessionTitleSnapshot = sessionTitleSnapshot;
        this.status = WorkoutStatus.IN_PROGRESS;
    }

    WorkoutExercise addExercise(UUID planExerciseId, String exerciseName, String muscleGroupName) {
        WorkoutExercise exercise = new WorkoutExercise(this, planExerciseId, exercises.size() + 1, exerciseName,
                muscleGroupName);
        exercises.add(exercise);
        return exercise;
    }

    /** First exercise IN_PROGRESS, the others TODO (spec 10.7 step 10). */
    void begin() {
        exercises.stream().min(Comparator.comparingInt(WorkoutExercise::getPosition))
                .ifPresent(WorkoutExercise::markInProgress);
    }

    boolean isInProgress() {
        return status == WorkoutStatus.IN_PROGRESS;
    }

    Optional<WorkoutExercise> currentExercise() {
        return exercises.stream().filter(e -> e.getStatus() == WorkoutExerciseStatus.IN_PROGRESS).findFirst();
    }

    Optional<WorkoutSet> findSet(UUID setId) {
        return exercises.stream().flatMap(e -> e.getSets().stream()).filter(s -> s.getId().equals(setId)).findFirst();
    }

    Optional<WorkoutExercise> findExercise(UUID exerciseId) {
        return exercises.stream().filter(e -> e.getId().equals(exerciseId)).findFirst();
    }

    /** Next TODO exercise in position order, if any. */
    Optional<WorkoutExercise> nextTodo() {
        return exercises.stream()
                .filter(e -> e.getStatus() == WorkoutExerciseStatus.TODO)
                .min(Comparator.comparingInt(WorkoutExercise::getPosition));
    }

    void complete(Instant now) {
        this.status = WorkoutStatus.COMPLETED;
        this.finishedAt = now;
    }

    void interrupt(Instant now) {
        this.status = WorkoutStatus.INTERRUPTED;
        this.finishedAt = now;
    }

    /** Most recently completed set, used to derive the rest timer. */
    Optional<WorkoutSet> lastCompletedSet() {
        return exercises.stream().flatMap(e -> e.getSets().stream())
                .filter(WorkoutSet::isCompleted)
                .max(Comparator.comparing(WorkoutSet::getCompletedAt));
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPlanAssignmentId() {
        return planAssignmentId;
    }

    public UUID getPlanSessionId() {
        return planSessionId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public WorkoutStatus getStatus() {
        return status;
    }

    public String getPlanNameSnapshot() {
        return planNameSnapshot;
    }

    public String getSessionTitleSnapshot() {
        return sessionTitleSnapshot;
    }

    public List<WorkoutExercise> getExercises() {
        return exercises;
    }
}
