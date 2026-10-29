package com.gymplanner.calendar.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * What the rotation plans for a date.
 *
 * @param sessionIndex    0-based position in rotation order, -1 when not a training day
 * @param rescheduledFrom TRAINING only: first skipped day whose session is recovered here (Task 3)
 * @param skip            SKIPPED only: the event that removed this training day
 */
public record DayPlan(LocalDate date, Type type, UUID sessionId, String sessionTitle, int sessionIndex,
        LocalDate rescheduledFrom, Skip skip) {

    public enum Type {
        /** The assignment is not active on that date (before start, after end, closed). */
        OUT_OF_PERIOD,
        /** The USER has not chosen any weekday yet. */
        NO_SCHEDULE,
        /** The plan has no sessions (in preparation). */
        NO_SESSIONS,
        REST,
        TRAINING,
        /** A training day that was missed, skipped or moved: its session is trained on {@code skip.landsOn}. */
        SKIPPED
    }

    /**
     * @param kind     MISSED (automatic, end of day), SKIPPED (by the USER) or MOVED (to a free day)
     * @param landsOn  day that receives the session, null when beyond the look-ahead window
     */
    public record Skip(UUID eventId, String kind, LocalDate landsOn, UUID sessionId, String sessionTitle) {
    }

    public DayPlan(LocalDate date, Type type, UUID sessionId, String sessionTitle, int sessionIndex) {
        this(date, type, sessionId, sessionTitle, sessionIndex, null, null);
    }

    public boolean isTraining() {
        return type == Type.TRAINING;
    }

    public static DayPlan of(LocalDate date, Type type) {
        return new DayPlan(date, type, null, null, -1);
    }
}
