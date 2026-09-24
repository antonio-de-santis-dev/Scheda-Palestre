package com.gymplanner.calendar.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What the rotation plans for a date.
 *
 * @param sessionIndex 0-based position in rotation order, -1 when not a training day
 */
public record DayPlan(LocalDate date, Type type, UUID sessionId, String sessionTitle, int sessionIndex) {

    public enum Type {
        /** The assignment is not active on that date (before start, after end, closed). */
        OUT_OF_PERIOD,
        /** The USER has not chosen any weekday yet. */
        NO_SCHEDULE,
        /** The plan has no sessions (in preparation). */
        NO_SESSIONS,
        REST,
        TRAINING
    }

    public boolean isTraining() {
        return type == Type.TRAINING;
    }

    public static DayPlan of(LocalDate date, Type type) {
        return new DayPlan(date, type, null, null, -1);
    }
}
