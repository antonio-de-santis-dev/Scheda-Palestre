package com.gymplanner.assignment.api;

import java.time.LocalDate;
import java.util.UUID;

/** Assignment data needed by the calendar and execution modules. */
public record AssignmentView(UUID id, UUID userId, UUID planId, LocalDate startDate, LocalDate endDate,
        boolean active, LocalDate rotationAnchorDate, int rotationAnchorIndex) {
}
