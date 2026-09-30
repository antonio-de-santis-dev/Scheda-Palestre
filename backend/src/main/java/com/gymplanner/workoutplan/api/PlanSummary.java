package com.gymplanner.workoutplan.api;

import java.time.LocalDate;
import java.util.UUID;

/** Shared plan; each assignment calculates its own date from its start. */
public record PlanSummary(UUID id, String name, boolean deleted, Integer durationWeeks) {
    public LocalDate expiresOn(LocalDate startDate) {
        return durationWeeks == null ? null : startDate.plusWeeks(durationWeeks);
    }

    /** Show a reminder during the final seven days, including the exact day of expiry. */
    public boolean recommendedDurationWarning(LocalDate startDate, LocalDate today) {
        LocalDate end = expiresOn(startDate);
        return end != null && !today.isBefore(end.minusWeeks(1));
    }

    public boolean recommendedDurationEnded(LocalDate startDate, LocalDate today) {
        LocalDate end = expiresOn(startDate);
        return end != null && today.isAfter(end);
    }
}
