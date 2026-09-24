package com.gymplanner.workoutplan.internal;

import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.web.Paging;
import com.gymplanner.workoutplan.api.PlanSessionRef;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.api.PlanSummary;
import com.gymplanner.workoutplan.api.WorkoutPlanEvents;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import com.gymplanner.workoutplan.internal.PlanDtos.CreatePlanRequest;
import com.gymplanner.workoutplan.internal.PlanDtos.UpdatePlanRequest;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Plan lifecycle (US-06 creation, US-08, US-26) and the module's public query API. */
@Service
class PlanService implements WorkoutPlanQueries {

    static final String COPY_SUFFIX = " (copia)";

    private final WorkoutPlanRepository plans;
    private final PlanStructureAssembler assembler;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PlanService(WorkoutPlanRepository plans, PlanStructureAssembler assembler, ApplicationEventPublisher events,
            Clock clock) {
        this.plans = plans;
        this.assembler = assembler;
        this.events = events;
        this.clock = clock;
    }

    /** Mapped inside the transaction: flags need the (batch fetched) structure. */
    @Transactional(readOnly = true)
    public Page<PlanDtos.PlanListItem> search(String q, boolean deleted, Pageable pageable) {
        return plans.search(Paging.likePattern(q), deleted, pageable)
                .map(p -> new PlanDtos.PlanListItem(p.getId(), p.getName(), p.getDescription(), p.getExpiresOn(),
                        p.getSessions().size(), PlanStructureAssembler.isExecutable(p), p.getCopiedFromPlanId(),
                        p.getCreatedAt(), p.getUpdatedAt(), p.getDeletedAt()));
    }

    @Transactional
    public PlanStructure create(CreatePlanRequest request, UUID adminId) {
        WorkoutPlan plan = new WorkoutPlan(request.name().trim(), blankToNull(request.description()),
                request.expiresOn(), adminId, null);
        plans.saveAndFlush(plan);
        return assembler.assemble(plan);
    }

    @Transactional
    public PlanStructure updateMetadata(UUID id, UpdatePlanRequest request) {
        WorkoutPlan plan = load(id);
        requireEditable(plan);
        if (plan.getVersion() != request.version()) {
            throw new ObjectOptimisticLockingFailureException(WorkoutPlan.class, id);
        }
        plan.updateMetadata(request.name().trim(), blankToNull(request.description()), request.expiresOn());
        plans.saveAndFlush(plan);
        return assembler.assemble(plan);
    }

    /** Logical deletion; active assignments are closed by the assignment module (event). */
    @Transactional
    public void delete(UUID id) {
        WorkoutPlan plan = load(id);
        if (plan.isDeleted()) {
            return;
        }
        plan.markDeleted(clock.instant());
        events.publishEvent(new WorkoutPlanEvents.PlanDeleted(id));
    }

    @Transactional
    public PlanStructure restore(UUID id) {
        WorkoutPlan plan = load(id);
        plan.restore();
        plans.saveAndFlush(plan);
        return assembler.assemble(plan);
    }

    /** Deep copy of sessions, sections, exercises and custom sets; assignments are not copied. */
    @Transactional
    public PlanStructure duplicate(UUID id, UUID adminId) {
        WorkoutPlan source = load(id);
        String name = source.getName();
        int max = 100 - COPY_SUFFIX.length();
        String copyName = (name.length() > max ? name.substring(0, max).trim() : name) + COPY_SUFFIX;
        WorkoutPlan copy = new WorkoutPlan(copyName, source.getDescription(), source.getExpiresOn(), adminId,
                source.getId());
        for (PlanSession session : source.getSessions()) {
            PlanSession sessionCopy = copy.addSession(session.getTitle());
            for (MuscleSection section : session.getSections()) {
                MuscleSection sectionCopy = sessionCopy.addSection(section.getMuscleGroupId());
                for (PlanExercise exercise : section.getExercises()) {
                    sectionCopy.addExercise(exercise.getExerciseId(), exercise.config());
                }
            }
        }
        plans.saveAndFlush(copy);
        return assembler.assemble(copy);
    }

    @Transactional(readOnly = true)
    public PlanStructure get(UUID id) {
        return assembler.assemble(load(id));
    }

    // ---------------------------------------------------------------- public API

    @Override
    @Transactional(readOnly = true)
    public Optional<PlanSummary> findPlan(UUID planId) {
        return plans.findById(planId).map(PlanService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PlanSummary> findPlans(Collection<UUID> planIds) {
        if (planIds.isEmpty()) {
            return Map.of();
        }
        return plans.findByIdIn(planIds).stream().map(PlanService::summary)
                .collect(Collectors.toMap(PlanSummary::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public PlanStructure getStructure(UUID planId) {
        return get(planId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanSessionRef> sessionsInOrder(UUID planId) {
        return plans.findSessionsInOrder(planId).stream()
                .map(s -> new PlanSessionRef(s.getId(), s.getTitle(), s.getPosition()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void requireExecutable(UUID planId) {
        WorkoutPlan plan = load(planId);
        if (plan.isDeleted()) {
            throw new BusinessRuleException("PLAN_DELETED", "The plan is deleted");
        }
        if (!PlanStructureAssembler.isExecutable(plan)) {
            throw new BusinessRuleException("PLAN_NOT_EXECUTABLE",
                    "The plan needs at least one session and every session needs at least one exercise");
        }
    }

    // ---------------------------------------------------------------- helpers

    WorkoutPlan load(UUID id) {
        return plans.findById(id).orElseThrow(() -> new NotFoundException("Workout plan"));
    }

    static void requireEditable(WorkoutPlan plan) {
        if (plan.isDeleted()) {
            throw new BusinessRuleException("PLAN_DELETED", "Restore the plan before editing it");
        }
    }

    private static PlanSummary summary(WorkoutPlan plan) {
        return new PlanSummary(plan.getId(), plan.getName(), plan.isDeleted());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static boolean sameIds(List<UUID> requested, List<UUID> current) {
        return requested.size() == current.size() && new java.util.HashSet<>(requested).size() == requested.size()
                && new java.util.HashSet<>(requested).equals(new java.util.HashSet<>(current))
                && requested.stream().allMatch(Objects::nonNull);
    }
}
