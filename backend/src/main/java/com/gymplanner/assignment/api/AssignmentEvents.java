package com.gymplanner.assignment.api;

import java.util.UUID;

/** Synchronous domain events of the assignment module (ADR 0002). */
public final class AssignmentEvents {

    private AssignmentEvents() {
    }

    /** An active assignment was closed: its in-progress workout must be interrupted (spec 10.5). */
    public record AssignmentClosed(UUID assignmentId, UUID userId) {
    }
}
