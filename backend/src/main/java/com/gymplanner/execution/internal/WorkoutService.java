package com.gymplanner.execution.internal;

import com.gymplanner.assignment.api.AssignmentEvents;
import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.execution.internal.WorkoutDtos.WorkoutState;
import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workout execution (US-17, US-18, US-20). Every method runs in one transaction and returns the
 * complete state. Ownership is always checked against the authenticated user (404 otherwise).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkoutService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutService.class);
    /** O-05: only the current day may be started; one day of tolerance absorbs time zones. */
    private static final long START_TOLERANCE_DAYS = 1;

    private final WorkoutRepository workouts;
    private final AssignmentQueries assignments;
    private final CalendarQueries calendarQueries;
    private final WorkoutPlanQueries plans;
    private final BusinessCalendar calendar;

    /** Spec 10.7: validation + immutable snapshot in a single transaction. */
    @Transactional
    public WorkoutState start(UUID userId, LocalDate date) {
        List<AssignmentView> active = assignments.listActiveForUser(userId);
        if (active.isEmpty()) {
            throw new BusinessRuleException("NO_ACTIVE_ASSIGNMENT", "No active plan assignment");
        }
        LocalDate today = calendar.today();
        if (Math.abs(ChronoUnit.DAYS.between(today, date)) > START_TOLERANCE_DAYS) {
            throw new BusinessRuleException("DATE_NOT_ALLOWED", "Only today's workout can be started");
        }
        // ADR 0008: a weekday belongs to at most one active plan, so at most one plan trains today.
        AssignmentView assignment = active.stream()
                .filter(a -> calendarQueries.dayPlan(a, date).isTraining())
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("NOT_A_TRAINING_DAY", "The date is not a planned training day"));
        DayPlan day = calendarQueries.dayPlan(assignment, date);
        plans.requireExecutable(assignment.planId());
        if (workouts.existsByPlanAssignmentIdAndScheduledDate(assignment.id(), date)) {
            throw new ConflictException("WORKOUT_ALREADY_EXISTS", "The workout of this day was already started");
        }
        if (workouts.findFirstByUserIdAndStatus(userId, WorkoutStatus.IN_PROGRESS).isPresent()) {
            throw new ConflictException("WORKOUT_ALREADY_IN_PROGRESS", "Another workout is in progress");
        }

        PlanStructure structure = plans.getStructure(assignment.planId());
        PlanStructure.Session session = structure.sessions().stream()
                .filter(s -> s.id().equals(day.sessionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("PLAN_NOT_EXECUTABLE", "The planned session no longer exists"));

        Instant now = calendar.now();
        Workout workout = new Workout(userId, assignment.id(), session.id(), date, now, structure.name(),
                session.title());
        // Global order: session position > section position > exercise position (spec 10.7 step 7).
        session.sections().stream().sorted(Comparator.comparingInt(PlanStructure.Section::position))
                .forEach(section -> section.exercises().stream()
                        .sorted(Comparator.comparingInt(PlanStructure.Exercise::position))
                        .forEach(exercise -> {
                            WorkoutExercise we = workout.addExercise(exercise.id(), exercise.exerciseName(),
                                    section.muscleGroupName());
                            exercise.sets().forEach(s -> we.addSet(s.setIndex(), s.reps(), s.toFailure(), s.restSeconds()));
                        }));
        workout.begin();
        try {
            workouts.saveAndFlush(workout);
        } catch (DataIntegrityViolationException e) {
            // Concurrent start: unique (assignment, date) or single in-progress workout per user.
            throw new ConflictException("WORKOUT_ALREADY_IN_PROGRESS", "The workout was started concurrently");
        }
        log.info("Workout id={} started for assignment id={} on {}", workout.getId(), assignment.id(), date);
        return WorkoutStateMapper.toState(workout, now);
    }

    @Transactional(readOnly = true)
    public Optional<WorkoutState> current(UUID userId) {
        return workouts.findFirstByUserIdAndStatus(userId, WorkoutStatus.IN_PROGRESS)
                .map(w -> WorkoutStateMapper.toState(w, calendar.now()));
    }

    /** Essential history (US-24): newest first, values taken from the snapshot. */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<WorkoutDtos.WorkoutSummary> history(UUID userId,
            org.springframework.data.domain.Pageable pageable, WorkoutHistoryFilter filter) {
        return workouts.findAll(filter.ownedBy(userId), pageable).map(WorkoutDtos.WorkoutSummary::of);
    }

    @Transactional(readOnly = true)
    public WorkoutState get(UUID userId, UUID workoutId) {
        Workout workout = workouts.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new NotFoundException("Workout"));
        return WorkoutStateMapper.toState(workout, calendar.now());
    }

    /**
     * "Fine serie" (spec 10.8), idempotent: an already completed set returns the current state
     * without advancing twice. The completion instant comes from the server clock.
     */
    @Transactional
    public WorkoutState completeSet(UUID userId, UUID workoutId, UUID setId) {
        Workout workout = lockOwned(userId, workoutId);
        WorkoutSet set = workout.findSet(setId).orElseThrow(() -> new NotFoundException("Set"));
        Instant now = calendar.now();
        if (set.isCompleted()) {
            return WorkoutStateMapper.toState(workout, now);
        }
        requireInProgress(workout);
        WorkoutExercise exercise = set.getWorkoutExercise();
        boolean isCurrent = exercise.getStatus() == WorkoutExerciseStatus.IN_PROGRESS
                && exercise.nextSet().map(s -> s.getId().equals(setId)).orElse(false);
        if (!isCurrent) {
            throw new BusinessRuleException("SET_NOT_CURRENT", "Only the current set can be completed");
        }
        // Fase F (supersedes O-06): the next set cannot start while the rest is running. A repeated
        // request for an already completed set was answered above (idempotency is preserved).
        if (workout.remainingRestMillis(now) > 0) {
            throw new BusinessRuleException("REST_NOT_FINISHED", "The rest period is not over yet")
                    .with("restEndsAt", workout.getRestEndsAt())
                    .with("restPaused", workout.isRestPaused())
                    .with("serverTime", now);
        }
        set.complete(now);
        workout.executionChanged();
        if (exercise.nextSet().isEmpty()) {
            exercise.markCompleted();
            advance(workout, now);
        }
        if (workout.isInProgress()) workout.startRest(now, set.getRestSeconds());
        workouts.flush();
        return WorkoutStateMapper.toState(workout, now);
    }

    /** Recovery changes share the set-completion lock and reject duplicate/stale revisions. */
    @Transactional
    public WorkoutState changeRest(UUID userId, UUID workoutId, WorkoutDtos.RestRequest request) {
        Workout workout = lockOwned(userId, workoutId);
        requireInProgress(workout);
        if (request.expectedVersion() != workout.getRestVersion()
                || request.expectedExecutionVersion() != workout.getExecutionVersion()) {
            throw new ConflictException("REST_STATE_CHANGED", "Workout or rest changed; reload before retrying");
        }
        Instant now = calendar.now();
        workout.changeRest(request.action(), now);
        workout.executionChanged();
        workouts.flush();
        return WorkoutStateMapper.toState(workout, now);
    }

    /** Changes only this workout's execution order, never the shared plan or saved sets. */
    @Transactional
    public WorkoutState reorderExercises(UUID userId, UUID workoutId, WorkoutDtos.ReorderExercisesRequest request) {
        Workout workout = lockOwned(userId, workoutId);
        requireInProgress(workout);
        List<WorkoutExercise> pending = workout.getExercises().stream()
                .filter(e -> e.getStatus() == WorkoutExerciseStatus.TODO
                        || e.getStatus() == WorkoutExerciseStatus.IN_PROGRESS)
                .sorted(Comparator.comparingInt(WorkoutExercise::getPosition)).toList();
        List<UUID> currentOrder = pending.stream().map(WorkoutExercise::getId).toList();
        List<UUID> requestedOrder = request.exerciseIds();
        if (requestedOrder.size() != currentOrder.size()
                || new HashSet<>(requestedOrder).size() != requestedOrder.size()
                || !new HashSet<>(requestedOrder).equals(new HashSet<>(currentOrder))) {
            throw new BadRequestException("INVALID_EXERCISE_ORDER", "Include every unfinished exercise exactly once");
        }
        long version = workout.getExecutionVersion();
        // A network retry of the last applied reorder is safe; later actions must not be overwritten.
        if (version == request.expectedVersion() + 1 && currentOrder.equals(requestedOrder)) {
            return WorkoutStateMapper.toState(workout, calendar.now());
        }
        if (version != request.expectedVersion()) {
            throw new ConflictException("WORKOUT_STATE_CHANGED", "Workout changed; reload before reordering");
        }
        if (currentOrder.equals(requestedOrder)) {
            return WorkoutStateMapper.toState(workout, calendar.now());
        }
        Map<UUID, WorkoutExercise> byId = pending.stream()
                .collect(Collectors.toMap(WorkoutExercise::getId, Function.identity()));
        List<Integer> positions = pending.stream().map(WorkoutExercise::getPosition).toList();
        // Release the partial unique index before activating a different exercise.
        workout.currentExercise().ifPresent(WorkoutExercise::markTodo);
        workouts.flush();
        for (int i = 0; i < requestedOrder.size(); i++) {
            byId.get(requestedOrder.get(i)).moveTo(positions.get(i));
        }
        byId.get(requestedOrder.getFirst()).markInProgress();
        workout.executionChanged();
        workouts.flush();
        return WorkoutStateMapper.toState(workout, calendar.now());
    }

    /** US-20 / O-02: only the exercise in progress can be skipped; skipped ones do not come back. */
    @Transactional
    public WorkoutState skipExercise(UUID userId, UUID workoutId, UUID exerciseId) {
        Workout workout = lockOwned(userId, workoutId);
        WorkoutExercise exercise = workout.findExercise(exerciseId)
                .orElseThrow(() -> new NotFoundException("Exercise"));
        Instant now = calendar.now();
        if (exercise.getStatus() == WorkoutExerciseStatus.SKIPPED) {
            return WorkoutStateMapper.toState(workout, now);
        }
        requireInProgress(workout);
        if (exercise.getStatus() != WorkoutExerciseStatus.IN_PROGRESS) {
            throw new BusinessRuleException("EXERCISE_NOT_IN_PROGRESS", "Only the exercise in progress can be skipped");
        }
        exercise.markSkipped();
        workout.executionChanged();
        advance(workout, now);
        workouts.flush();
        return WorkoutStateMapper.toState(workout, now);
    }

    @Transactional
    public WorkoutState interrupt(UUID userId, UUID workoutId) {
        Workout workout = lockOwned(userId, workoutId);
        Instant now = calendar.now();
        if (workout.getStatus() == WorkoutStatus.INTERRUPTED) {
            return WorkoutStateMapper.toState(workout, now);
        }
        requireInProgress(workout);
        workout.interrupt(now);
        workout.executionChanged();
        workouts.flush();
        return WorkoutStateMapper.toState(workout, now);
    }

    /** Spec 10.5 step 3: closing an assignment interrupts its workout in progress. */
    @EventListener
    @Transactional
    public void onAssignmentClosed(AssignmentEvents.AssignmentClosed event) {
        Instant now = calendar.now();
        for (Workout workout : workouts.findByPlanAssignmentIdAndStatus(event.assignmentId(), WorkoutStatus.IN_PROGRESS)) {
            workout.interrupt(now);
            workout.executionChanged();
            log.info("Workout id={} interrupted because its assignment was closed", workout.getId());
        }
    }

    /**
     * Activates the next TODO exercise or completes the workout. The flush first releases the
     * "one exercise in progress" partial unique index held by the previous exercise.
     */
    private void advance(Workout workout, Instant now) {
        workouts.flush();
        Optional<WorkoutExercise> next = workout.nextTodo();
        if (next.isPresent()) {
            next.get().markInProgress();
        } else {
            workout.complete(now);
        }
    }

    private Workout lockOwned(UUID userId, UUID workoutId) {
        return workouts.lockOwned(workoutId, userId).orElseThrow(() -> new NotFoundException("Workout"));
    }

    private static void requireInProgress(Workout workout) {
        if (!workout.isInProgress()) {
            throw new BusinessRuleException("WORKOUT_NOT_IN_PROGRESS", "The workout is no longer in progress");
        }
    }
}
