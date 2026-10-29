package com.gymplanner.calendar.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Workouts of an assignment, seen by the calendar (port implemented by the execution module). */
public interface WorkoutScheduleAvailability {

    boolean hasWorkout(UUID assignmentId, LocalDate date);

    /** Dates in [from, to] that already have a workout of the assignment (any status). */
    Set<LocalDate> workoutDates(UUID assignmentId, LocalDate from, LocalDate to);

    /** Dates in [from, to] with a workout of the user, whatever the plan (one workout per day and plan). */
    Set<LocalDate> userWorkoutDates(UUID userId, LocalDate from, LocalDate to);
}
