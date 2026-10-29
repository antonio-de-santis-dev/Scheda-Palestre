package com.gymplanner.execution.internal;

import com.gymplanner.calendar.api.WorkoutScheduleAvailability;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class WorkoutScheduleAvailabilityService
        implements WorkoutScheduleAvailability {

    private final WorkoutRepository workouts;

    @Override
    @Transactional(readOnly = true)
    public boolean hasWorkout(UUID assignmentId, LocalDate date) {
        return workouts.existsByPlanAssignmentIdAndScheduledDate(
                assignmentId, date);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalDate> workoutDates(UUID assignmentId, LocalDate from, LocalDate to) {
        return new TreeSet<>(workouts.findScheduledDates(assignmentId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalDate> userWorkoutDates(UUID userId, LocalDate from, LocalDate to) {
        return new TreeSet<>(workouts.findUserScheduledDates(userId, from, to));
    }
}
