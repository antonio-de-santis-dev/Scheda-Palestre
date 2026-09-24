package com.gymplanner.assignment.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class AssignmentDtos {

    private AssignmentDtos() {
    }

    /**
     * Spec 13.5. {@code copySchedule} (O-07, default true) copies the weekly days of the user's
     * previous active assignment when activating.
     */
    record AssignRequest(
            @NotNull UUID planId,
            @NotEmpty @Size(max = 200) List<@NotNull UUID> userIds,
            @NotNull LocalDate startDate,
            boolean activate,
            Boolean copySchedule) {

        boolean copy() {
            return copySchedule == null || copySchedule;
        }
    }

    record ActivateRequest(Boolean copySchedule) {

        boolean copy() {
            return copySchedule == null || copySchedule;
        }
    }

    record AssignmentResponse(UUID id, UUID userId, String userFullName, String username, UUID planId,
            String planName, boolean planDeleted, LocalDate startDate, LocalDate endDate, boolean active,
            String status, Instant createdAt) {
    }
}
