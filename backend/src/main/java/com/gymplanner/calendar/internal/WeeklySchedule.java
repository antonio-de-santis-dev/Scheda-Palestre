package com.gymplanner.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A weekday (ISO 1-7) chosen by the USER for an assignment (spec 8.11). */
@Entity
@Table(name = "weekly_schedules")
public class WeeklySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "plan_assignment_id", nullable = false, updatable = false)
    private UUID planAssignmentId;

    @Column(nullable = false, updatable = false)
    private int weekday;

    protected WeeklySchedule() {
    }

    WeeklySchedule(UUID planAssignmentId, int weekday) {
        this.planAssignmentId = planAssignmentId;
        this.weekday = weekday;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPlanAssignmentId() {
        return planAssignmentId;
    }

    public int getWeekday() {
        return weekday;
    }
}
