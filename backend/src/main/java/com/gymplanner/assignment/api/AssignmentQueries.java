package com.gymplanner.assignment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Assignment access for calendar and execution. */
public interface AssignmentQueries {

    Optional<AssignmentView> findActiveForUser(UUID userId);

    /** Empty when the assignment does not exist or belongs to someone else. */
    Optional<AssignmentView> findForUser(UUID assignmentId, UUID userId);

    Optional<AssignmentView> find(UUID assignmentId);

    List<AssignmentView> findActiveByPlan(UUID planId);

    /** Moves the rotation anchor (owned by this module, computed by the calendar). */
    void updateRotationAnchor(UUID assignmentId, LocalDate anchorDate, int anchorIndex);
}
