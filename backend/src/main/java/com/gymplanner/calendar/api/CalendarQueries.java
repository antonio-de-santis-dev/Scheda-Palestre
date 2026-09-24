package com.gymplanner.calendar.api;

import com.gymplanner.assignment.api.AssignmentView;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Rotation queries for the execution module. */
public interface CalendarQueries {

    Set<Integer> weekdays(UUID assignmentId);

    DayPlan dayPlan(AssignmentView assignment, LocalDate date);

    /** Plans for every date of [from, to] (inclusive), computed with a single structure lookup. */
    List<DayPlan> range(AssignmentView assignment, LocalDate from, LocalDate to);
}
