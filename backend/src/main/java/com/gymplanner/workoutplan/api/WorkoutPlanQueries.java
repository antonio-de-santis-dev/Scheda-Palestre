package com.gymplanner.workoutplan.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read access to plans for the assignment, calendar and execution modules. */
public interface WorkoutPlanQueries {

    Optional<PlanSummary> findPlan(UUID planId);

    Map<UUID, PlanSummary> findPlans(Collection<UUID> planIds);

    /** @throws com.gymplanner.shared.error.NotFoundException if the plan does not exist */
    PlanStructure getStructure(UUID planId);

    /** Sessions ordered by position: the rotation order decided by the ADMIN. */
    List<PlanSessionRef> sessionsInOrder(UUID planId);

    /**
     * @throws com.gymplanner.shared.error.NotFoundException     if the plan does not exist
     * @throws com.gymplanner.shared.error.BusinessRuleException {@code PLAN_NOT_EXECUTABLE} or {@code PLAN_DELETED}
     */
    void requireExecutable(UUID planId);
}
