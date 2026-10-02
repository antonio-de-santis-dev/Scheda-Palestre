package com.gymplanner.execution.internal;

import com.gymplanner.shared.error.BusinessRuleException;
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
import java.time.Duration;
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

    private Instant restEndsAt;
    private Long restRemainingMillis;
    private Integer restDurationSeconds;
    @Column(nullable = false)
    private long restVersion;

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
        restEndsAt = null;
        restRemainingMillis = null;
        restDurationSeconds = null;
        restVersion++;
    }

    void interrupt(Instant now) {
        this.status = WorkoutStatus.INTERRUPTED;
        this.finishedAt = now;
        restEndsAt = null;
        restRemainingMillis = null;
        restDurationSeconds = null;
        restVersion++;
    }

    /** Most recently completed set, used to derive the rest timer. */
    Optional<WorkoutSet> lastCompletedSet() {
        return exercises.stream().flatMap(e -> e.getSets().stream())
                .filter(WorkoutSet::isCompleted)
                .max(Comparator.comparing(WorkoutSet::getCompletedAt));
    }

    /** Wall-clock duration, including recovery, paused recovery and time away from the page. */
    long durationSeconds(Instant now) {
        return Math.max(0, Duration.between(startedAt, finishedAt == null ? now : finishedAt).getSeconds());
    }

    void startRest(Instant now, int seconds) {
        restVersion++;
        restRemainingMillis = null;
        restDurationSeconds = seconds > 0 && isInProgress() ? seconds : null;
        restEndsAt = seconds > 0 && isInProgress() ? now.plusSeconds(seconds) : null;
    }

    long remainingRestMillis(Instant now) {
        if (!isInProgress()) return 0;
        return restRemainingMillis != null ? restRemainingMillis
                : restEndsAt == null ? 0 : Math.max(0, Duration.between(now, restEndsAt).toMillis());
    }

    void changeRest(WorkoutDtos.RestAction action, Integer seconds, Instant now) {
        long remaining = remainingRestMillis(now);
        if (remaining <= 0) {
            throw new BusinessRuleException("REST_NOT_ACTIVE", "There is no active rest period");
        }
        switch (action) {
            case PAUSE -> {
                if (restRemainingMillis != null) throw new BusinessRuleException("REST_ALREADY_PAUSED", "Rest is paused");
                restRemainingMillis = remaining;
                restEndsAt = null;
            }
            case RESUME -> {
                if (restRemainingMillis == null) throw new BusinessRuleException("REST_NOT_PAUSED", "Rest is running");
                restEndsAt = now.plusMillis(remaining);
                restRemainingMillis = null;
            }
            case EXTEND -> {
                long extended = remaining + seconds * 1000L;
                if (extended > 3_600_000) throw new BusinessRuleException("REST_LIMIT_EXCEEDED", "Rest cannot exceed one hour");
                restDurationSeconds = Math.max(restDurationSeconds == null ? 0 : restDurationSeconds,
                        (int) ((extended + 999) / 1000));
                if (restRemainingMillis != null) restRemainingMillis = extended;
                else restEndsAt = now.plusMillis(extended);
            }
            case SKIP -> {
                restEndsAt = null;
                restRemainingMillis = null;
                restDurationSeconds = null;
            }
        }
        restVersion++;
    }

    public Instant getRestEndsAt() { return restEndsAt; }
    public Integer getRestDurationSeconds() { return restDurationSeconds; }
    public boolean isRestPaused() { return isInProgress() && restRemainingMillis != null; }
    public long getRestVersion() { return restVersion; }

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
