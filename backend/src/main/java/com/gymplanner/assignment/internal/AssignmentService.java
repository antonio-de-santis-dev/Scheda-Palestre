package com.gymplanner.assignment.internal;

import com.gymplanner.assignment.api.AssignmentEvents;
import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.assignment.internal.AssignmentDtos.AssignRequest;
import com.gymplanner.assignment.internal.AssignmentDtos.AssignmentResponse;
import com.gymplanner.identity.api.UserDirectory;
import com.gymplanner.identity.api.UserRole;
import com.gymplanner.identity.api.UserSummary;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.time.BusinessCalendar;
import com.gymplanner.workoutplan.api.PlanStructure;
import com.gymplanner.workoutplan.api.PlanSummary;
import com.gymplanner.workoutplan.api.WorkoutPlanEvents;
import com.gymplanner.workoutplan.api.WorkoutPlanQueries;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service of the assignment module (US-06, US-07, US-08 closing). Activation is the
 * orchestration of spec 10.5, executed in one transaction; reactions of other modules
 * (interrupting workouts, copying weekly days) run synchronously through events.
 */
@Service
class AssignmentService implements AssignmentQueries {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final PlanAssignmentRepository assignments;
    private final UserDirectory users;
    private final WorkoutPlanQueries plans;
    private final BusinessCalendar calendar;
    private final ApplicationEventPublisher events;

    AssignmentService(PlanAssignmentRepository assignments, UserDirectory users, WorkoutPlanQueries plans,
            BusinessCalendar calendar, ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.users = users;
        this.plans = plans;
        this.calendar = calendar;
        this.events = events;
    }

    // ------------------------------------------------------------------ ADMIN

    /** One {@link PlanAssignment} per USER, all-or-nothing (US-06). */
    @Transactional
    public List<AssignmentResponse> assign(AssignRequest request, UUID adminId) {
        PlanSummary plan = plans.findPlan(request.planId()).orElseThrow(() -> new NotFoundException("Workout plan"));
        if (plan.deleted()) {
            throw new BusinessRuleException("PLAN_DELETED", "A deleted plan cannot be assigned");
        }
        if (request.activate()) {
            plans.requireExecutable(plan.id());
        }
        Set<UUID> userIds = new LinkedHashSet<>(request.userIds());
        Map<UUID, UserSummary> found = users.findAll(userIds);
        for (UUID userId : userIds) {
            UserSummary user = found.get(userId);
            if (user == null || user.role() != UserRole.USER) {
                throw new BusinessRuleException("USER_NOT_ASSIGNABLE", "Plans can only be assigned to USER accounts");
            }
        }
        List<PlanAssignment> created = new ArrayList<>();
        for (UUID userId : userIds) {
            PlanAssignment assignment = assignments.save(
                    new PlanAssignment(userId, plan.id(), adminId, request.startDate()));
            if (request.activate()) {
                activateInternal(assignment, request.copySchedule() == null || request.copySchedule());
            }
            created.add(assignment);
        }
        assignments.flush();
        log.info("Plan id={} assigned to {} user(s), activate={}", plan.id(), created.size(), request.activate());
        return toResponses(created);
    }

    @Transactional
    public AssignmentResponse activate(UUID assignmentId, boolean copySchedule) {
        PlanAssignment assignment = load(assignmentId);
        switch (assignment.status()) {
            case ACTIVE -> {
                return toResponses(List.of(assignment)).getFirst();
            }
            case CLOSED -> throw new BusinessRuleException("ASSIGNMENT_CLOSED",
                    "A closed assignment cannot be activated again: create a new one");
            case PENDING -> {
                plans.requireExecutable(assignment.getWorkoutPlanId());
                activateInternal(assignment, copySchedule);
            }
        }
        assignments.flush();
        return toResponses(List.of(assignment)).getFirst();
    }

    @Transactional
    public AssignmentResponse close(UUID assignmentId) {
        PlanAssignment assignment = load(assignmentId);
        closeInternal(assignment);
        assignments.flush();
        return toResponses(List.of(assignment)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listForPlan(UUID planId) {
        plans.findPlan(planId).orElseThrow(() -> new NotFoundException("Workout plan"));
        return toResponses(assignments.findByWorkoutPlanIdOrderByCreatedAtDesc(planId));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> listForUser(UUID userId) {
        users.find(userId).orElseThrow(() -> new NotFoundException("User"));
        return toResponses(assignments.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** US-08: deleting a plan closes its active assignments in the same transaction. */
    @EventListener
    @Transactional
    public void onPlanDeleted(WorkoutPlanEvents.PlanDeleted event) {
        List<PlanAssignment> active = assignments.findByWorkoutPlanIdAndActiveTrue(event.planId());
        active.forEach(this::closeInternal);
        if (!active.isEmpty()) {
            log.info("Plan id={} deleted: {} active assignment(s) closed", event.planId(), active.size());
        }
    }

    // ------------------------------------------------------------------ USER

    @Transactional(readOnly = true)
    public List<AssignmentResponse> myAssignments(UUID userId) {
        return toResponses(assignments.findByUserIdOrderByCreatedAtDesc(userId));
    }

    /** US-07/US-21: another user's assignment is indistinguishable from a missing one (404). */
    @Transactional(readOnly = true)
    public PlanStructure myPlan(UUID assignmentId, UUID userId) {
        PlanAssignment assignment = assignments.findByIdAndUserId(assignmentId, userId)
                .orElseThrow(() -> new NotFoundException("Assignment"));
        return plans.getStructure(assignment.getWorkoutPlanId());
    }

    // ------------------------------------------------------------------ public API

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentView> findActiveForUser(UUID userId) {
        return assignments.findByUserIdAndActiveTrue(userId).map(AssignmentService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentView> findForUser(UUID assignmentId, UUID userId) {
        return assignments.findByIdAndUserId(assignmentId, userId).map(AssignmentService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssignmentView> find(UUID assignmentId) {
        return assignments.findById(assignmentId).map(AssignmentService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentView> findActiveByPlan(UUID planId) {
        return assignments.findByWorkoutPlanIdAndActiveTrue(planId).stream().map(AssignmentService::view).toList();
    }

    @Override
    @Transactional
    public void updateRotationAnchor(UUID assignmentId, LocalDate anchorDate, int anchorIndex) {
        load(assignmentId).reanchor(anchorDate, anchorIndex);
    }

    // ------------------------------------------------------------------ internals

    /**
     * Spec 10.5: close the previous active assignment (its in-progress workout is interrupted by
     * the execution module), activate, initialise the rotation anchor, copy the weekly days.
     */
    private void activateInternal(PlanAssignment assignment, boolean copySchedule) {
        UUID previousId = null;
        Optional<PlanAssignment> previous = assignments.lockActiveByUser(assignment.getUserId());
        if (previous.isPresent()) {
            PlanAssignment old = previous.get();
            if (old.getWorkoutPlanId().equals(assignment.getWorkoutPlanId())) {
                throw new ConflictException("ASSIGNMENT_ALREADY_ACTIVE",
                        "The user already has this plan as active assignment");
            }
            closeInternal(old);
            previousId = old.getId();
            // Flush now: the partial unique index is checked row by row.
            assignments.flush();
        }
        assignment.activate(calendar.today());
        assignments.flush();
        events.publishEvent(new AssignmentEvents.AssignmentActivated(assignment.getId(), assignment.getUserId(),
                previousId, copySchedule));
    }

    private void closeInternal(PlanAssignment assignment) {
        if (assignment.status() == PlanAssignment.Status.CLOSED) {
            return;
        }
        boolean wasActive = assignment.isActive();
        assignment.close(calendar.today());
        if (wasActive) {
            events.publishEvent(new AssignmentEvents.AssignmentClosed(assignment.getId(), assignment.getUserId()));
        }
    }

    private PlanAssignment load(UUID id) {
        return assignments.findById(id).orElseThrow(() -> new NotFoundException("Assignment"));
    }

    private static AssignmentView view(PlanAssignment a) {
        return new AssignmentView(a.getId(), a.getUserId(), a.getWorkoutPlanId(), a.getStartDate(), a.getEndDate(),
                a.isActive(), a.getRotationAnchorDate(), a.getRotationAnchorIndex());
    }

    /** Bulk resolution of user and plan names (no N+1). */
    private List<AssignmentResponse> toResponses(Collection<PlanAssignment> list) {
        Map<UUID, UserSummary> userMap = users.findAll(list.stream().map(PlanAssignment::getUserId).toList());
        Map<UUID, PlanSummary> planMap = plans.findPlans(list.stream().map(PlanAssignment::getWorkoutPlanId).toList());
        return list.stream().map(a -> {
            UserSummary u = userMap.get(a.getUserId());
            PlanSummary p = planMap.get(a.getWorkoutPlanId());
            return new AssignmentResponse(a.getId(), a.getUserId(), u == null ? "?" : u.fullName(),
                    u == null ? "?" : u.username(), a.getWorkoutPlanId(), p == null ? "?" : p.name(),
                    p != null && p.deleted(), a.getStartDate(), a.getEndDate(), a.isActive(), a.status().name(),
                    a.getCreatedAt());
        }).toList();
    }
}
