package com.gymplanner.calendar.api;

import java.time.LocalDate;
import java.util.UUID;

/** Checks whether an assignment already has a workout on a date. */
public interface WorkoutScheduleAvailability {

    boolean hasWorkout(UUID assignmentId, LocalDate date);
}
