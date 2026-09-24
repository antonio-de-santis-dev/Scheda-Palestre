package com.gymplanner.execution.internal;

import com.gymplanner.assignment.api.AssignmentEvents;
import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
import com.gymplanner.execution.internal.WorkoutDtos.WorkoutState;
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
import java.util.Optional;
import java.util.UUID;
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
class WorkoutService {

    private static final Logger log = LoggerFactory.getLogger(WorkoutService.class);
    /** O-05: only the current day may be started; one day of tolerance absorbs time zones. */
    private static final long START_TOLERANCE_DAYS = 1;

    private final WorkoutRepository workouts;
    private final AssignmentQueries assignments;
    private final CalendarQueries calendarQueries;
    private final WorkoutPlanQueries plans;
    private final BusinessCalendar calendar;

    WorkoutService(WorkoutRepository workouts, AssignmentQueries assignments, CalendarQueries calendarQueries,
            WorkoutPlanQueries plans, BusinessCalendar calendar) {
        this.workouts = workouts;
        this.assignments = assignments;
        this.calendarQueries = calendarQueries;
        this.plans = plans;
        this.calendar = calendar;
    }

    /** Spec 10.7: validation + immutable snapshot in a single transaction. */
    @Transactional
    public WorkoutState start(UUID userId, LocalDate date) {
        AssignmentView assignment = assignments.findActiveForUser(userId)
                .orElseThrow(() -> new BusinessRuleException("NO_ACTIVE_ASSIGNMENT", "No active plan assignment"));
        LocalDate today = calendar.today();
        if (Math.abs(ChronoUnit.DAYS.between(today, date)) > START_TOLERANCE_DAYS) {
            throw new BusinessRuleException("DATE_NOT_ALLOWED", "Only today's workout can be started");
        }
        DayPlan day = calendarQueries.dayPlan(assignment, date);
        if (!day.isTraining()) {
            throw new BusinessRuleException("NOT_A_TRAINING_DAY", "The date is not a planned training day");
        }
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
            org.springframework.data.domain.Pageable pageable) {
        return workouts.findByUserId(userId, pageable).map(WorkoutDtos.WorkoutSummary::of);
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
        set.complete(now);
        if (exercise.nextSet().isEmpty()) {
            exercise.markCompleted();
            advance(workout, now);
        }
        workouts.flush();
        return WorkoutStateMapper.toState(workout, now);
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
