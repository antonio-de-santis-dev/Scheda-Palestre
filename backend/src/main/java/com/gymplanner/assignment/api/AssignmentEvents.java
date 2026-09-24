package com.gymplanner.assignment.api;

import java.util.UUID;

/** Synchronous domain events of the assignment module (ADR 0002). */
public final class AssignmentEvents {

    private AssignmentEvents() {
    }

    /** An active assignment was closed: its in-progress workout must be interrupted (spec 10.5). */
    public record AssignmentClosed(UUID assignmentId, UUID userId) {
    }

    /**
     * An assignment became active.
     *
     * @param previousAssignmentId the assignment it replaced, if any
     * @param copySchedule         O-07: copy the weekly days of the previous assignment
     */
    public record AssignmentActivated(UUID assignmentId, UUID userId, UUID previousAssignmentId, boolean copySchedule) {
    }
}
