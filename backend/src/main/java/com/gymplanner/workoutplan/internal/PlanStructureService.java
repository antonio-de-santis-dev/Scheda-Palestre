package com.gymplanner.workoutplan.internal;

import com.gymplanner.catalog.api.CatalogLookup;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.api.WorkoutPlanEvents;
import com.gymplanner.workoutplan.internal.PlanDtos.PlanExerciseRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Editing of the plan structure (US-09 sessions, US-10 sections, US-11/12/13 exercises, US-14
 * custom sets). Every operation runs in one transaction, marks the plan as changed (optimistic
 * version) and returns the full updated structure.
 */
@Service
class PlanStructureService {

    private final WorkoutPlanRepository plans;
    private final PlanService planService;
    private final PlanStructureAssembler assembler;
    private final CatalogLookup catalog;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PlanStructureService(WorkoutPlanRepository plans, PlanService planService, PlanStructureAssembler assembler,
            CatalogLookup catalog, ApplicationEventPublisher events, Clock clock) {
        this.plans = plans;
        this.planService = planService;
        this.assembler = assembler;
        this.catalog = catalog;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- sessions

    @Transactional
    public PlanStructure addSession(UUID planId, String title) {
        WorkoutPlan plan = editablePlan(planId);
        List<UUID> before = sessionIds(plan);
        plan.addSession(title.trim());
        return saved(plan, before);
    }

    @Transactional
    public PlanStructure renameSession(UUID sessionId, String title) {
        PlanSession session = plans.findSession(sessionId).orElseThrow(() -> new NotFoundException("Session"));
        WorkoutPlan plan = session.getWorkoutPlan();
        PlanService.requireEditable(plan);
        session.rename(title.trim());
        return saved(plan, null);
    }

    @Transactional
    public PlanStructure deleteSession(UUID sessionId) {
        PlanSession session = plans.findSession(sessionId).orElseThrow(() -> new NotFoundException("Session"));
        WorkoutPlan plan = session.getWorkoutPlan();
        PlanService.requireEditable(plan);
        List<UUID> before = sessionIds(plan);
        plan.removeSession(session);
        return saved(plan, before);
    }

    @Transactional
    public PlanStructure reorderSessions(UUID planId, List<UUID> ids) {
        WorkoutPlan plan = editablePlan(planId);
        List<UUID> before = sessionIds(plan);
        reorder(plan.getSessions(), ids);
        return saved(plan, before);
    }

    // ---------------------------------------------------------------- sections

    @Transactional
    public PlanStructure addSection(UUID sessionId, UUID muscleGroupId) {
        PlanSession session = plans.findSession(sessionId).orElseThrow(() -> new NotFoundException("Session"));
        WorkoutPlan plan = session.getWorkoutPlan();
        PlanService.requireEditable(plan);
        catalog.requireSelectableMuscleGroup(muscleGroupId);
        if (session.hasMuscleGroup(muscleGroupId)) {
            throw new ConflictException("MUSCLE_GROUP_ALREADY_IN_SESSION",
                    "The muscle group is already present in this session");
        }
        session.addSection(muscleGroupId);
        return saved(plan, null);
    }

    @Transactional
    public PlanStructure deleteSection(UUID sectionId) {
        MuscleSection section = plans.findSection(sectionId).orElseThrow(() -> new NotFoundException("Section"));
        PlanSession session = section.getPlanSession();
        WorkoutPlan plan = session.getWorkoutPlan();
        PlanService.requireEditable(plan);
        session.removeSection(section);
        return saved(plan, null);
    }

    @Transactional
    public PlanStructure reorderSections(UUID sessionId, List<UUID> ids) {
        PlanSession session = plans.findSession(sessionId).orElseThrow(() -> new NotFoundException("Session"));
        WorkoutPlan plan = session.getWorkoutPlan();
        PlanService.requireEditable(plan);
        reorder(session.getSections(), ids);
        return saved(plan, null);
    }

    // ---------------------------------------------------------------- exercises

    @Transactional
    public PlanStructure addExercise(UUID sectionId, PlanExerciseRequest request) {
        MuscleSection section = plans.findSection(sectionId).orElseThrow(() -> new NotFoundException("Section"));
        WorkoutPlan plan = section.getPlanSession().getWorkoutPlan();
        PlanService.requireEditable(plan);
        ExerciseConfig config = ExerciseConfigValidator.validate(request);
        catalog.requireSelectableExercise(request.exerciseId());
        section.addExercise(request.exerciseId(), config);
        return saved(plan, null);
    }

    /** Full replacement of the configuration, custom sets included (atomic, US-14). */
    @Transactional
    public PlanStructure updateExercise(UUID planExerciseId, PlanExerciseRequest request) {
        PlanExercise exercise = plans.findPlanExercise(planExerciseId)
                .orElseThrow(() -> new NotFoundException("Plan exercise"));
        WorkoutPlan plan = exercise.getMuscleSection().getPlanSession().getWorkoutPlan();
        PlanService.requireEditable(plan);
        ExerciseConfig config = ExerciseConfigValidator.validate(request);
        if (!exercise.getExerciseId().equals(request.exerciseId())) {
            // Keeping an already used (even deactivated) exercise is allowed; a new one must be active.
            catalog.requireSelectableExercise(request.exerciseId());
        }
        exercise.apply(request.exerciseId(), config);
        return saved(plan, null);
    }

    @Transactional
    public PlanStructure deleteExercise(UUID planExerciseId) {
        PlanExercise exercise = plans.findPlanExercise(planExerciseId)
                .orElseThrow(() -> new NotFoundException("Plan exercise"));
        MuscleSection section = exercise.getMuscleSection();
        WorkoutPlan plan = section.getPlanSession().getWorkoutPlan();
        PlanService.requireEditable(plan);
        section.removeExercise(exercise);
        return saved(plan, null);
    }

    @Transactional
    public PlanStructure reorderExercises(UUID sectionId, List<UUID> ids) {
        MuscleSection section = plans.findSection(sectionId).orElseThrow(() -> new NotFoundException("Section"));
        WorkoutPlan plan = section.getPlanSession().getWorkoutPlan();
        PlanService.requireEditable(plan);
        reorder(section.getExercises(), ids);
        return saved(plan, null);
    }

    // ---------------------------------------------------------------- helpers

    private WorkoutPlan editablePlan(UUID planId) {
        WorkoutPlan plan = planService.load(planId);
        PlanService.requireEditable(plan);
        return plan;
    }

    /**
     * Applies a complete new order: the request must contain exactly the ids of the container.
     * Positions are rewritten in one transaction (deferred unique constraints).
     */
    private static <T extends Positioned> void reorder(List<T> items, List<UUID> ids) {
        List<UUID> current = items.stream().map(Positioned::getId).toList();
        if (!PlanService.sameIds(ids, current)) {
            throw new BusinessRuleException("INVALID_ORDER",
                    "The order must contain exactly the ids of the current elements");
        }
        Map<UUID, T> byId = items.stream().collect(Collectors.toMap(Positioned::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).setPosition(i + 1);
        }
    }

    /** Session ids in rotation order (by position: the in-memory list may be stale after a reorder). */
    private static List<UUID> sessionIds(WorkoutPlan plan) {
        return plan.getSessions().stream()
                .sorted(java.util.Comparator.comparingInt(PlanSession::getPosition))
                .map(PlanSession::getId)
                .toList();
    }

    /**
     * Marks the plan as changed, flushes (so constraint violations surface here) and, when the
     * session list changed, notifies the calendar to re-anchor rotations.
     */
    private PlanStructure saved(WorkoutPlan plan, List<UUID> sessionsBefore) {
        plan.touch(clock.instant());
        plans.saveAndFlush(plan);
        if (sessionsBefore != null && !sessionsBefore.equals(sessionIds(plan))) {
            events.publishEvent(new WorkoutPlanEvents.PlanSessionsChanged(plan.getId(), sessionsBefore));
        }
        return assembler.assemble(plan);
    }
}
