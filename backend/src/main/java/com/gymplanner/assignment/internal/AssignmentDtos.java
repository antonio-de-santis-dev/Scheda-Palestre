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
     * Spec 13.5. {@code copySchedule} (ADR 0008, default false): when activating, copy from the
     * user's most recently closed plan only the weekdays that are still free.
     */
    record AssignRequest(
            @NotNull UUID planId,
            @NotEmpty @Size(max = 200) List<@NotNull UUID> userIds,
            @NotNull LocalDate startDate,
            boolean activate,
            Boolean copySchedule) {

        boolean copy() {
            return Boolean.TRUE.equals(copySchedule);
        }
    }

    record ActivateRequest(Boolean copySchedule) {

        boolean copy() {
            return Boolean.TRUE.equals(copySchedule);
        }
    }

    /**
     * {@code copiedWeekdays}/{@code skippedWeekdays} are filled only by an activation that asked
     * for the copy: skipped days were already used by another active plan of the user.
     * {@code planExpiresOn} is the end of the plan's recommended duration (ADR 0009) and
     * {@code recommendedDurationEnded} is computed by the server in the gym's time zone for ACTIVE
     * assignments ({@code today > planExpiresOn}): the plan stays fully usable.
     */
    record AssignmentResponse(UUID id, UUID userId, String userFullName, String username, UUID planId,
            String planName, boolean planDeleted, LocalDate startDate, LocalDate endDate, boolean active,
            String status, Instant createdAt, List<Integer> copiedWeekdays, List<Integer> skippedWeekdays,
            LocalDate planExpiresOn, boolean recommendedDurationEnded) {
    }

    /** ADMIN indicator: an active assignment whose plan's recommended duration has ended. */
    record EndedDurationResponse(UUID userId, UUID assignmentId, UUID planId, String planName, LocalDate expiresOn) {
    }
}
