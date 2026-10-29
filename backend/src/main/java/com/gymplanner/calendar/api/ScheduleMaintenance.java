package com.gymplanner.calendar.api;

import java.util.UUID;

/**
 * Records the training days of a USER that ended without a workout (Task 3). Idempotent and
 * cheap when nothing changed: called before the "Oggi", calendar and schedule views and before a
 * workout starts, so the missed day is rescheduled at the first access after the end of the day.
 */
public interface ScheduleMaintenance {

    void recordMissedSessions(UUID userId);
}
