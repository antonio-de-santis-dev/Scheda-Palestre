package com.gymplanner.workoutplan.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Minimal plan data for other modules.
 *
 * @param expiresOn end of the <em>recommended duration</em> of the plan (ADR 0009); it is not the
 *                  end of an assignment ({@code plan_assignments.end_date}) and it never blocks anything
 */
public record PlanSummary(UUID id, String name, boolean deleted, LocalDate expiresOn) {

    /** From the day after {@code expiresOn} (strictly after, never on the same day). */
    public boolean recommendedDurationEnded(LocalDate today) {
        return expiresOn != null && today.isAfter(expiresOn);
    }
}
