package com.gymplanner.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Last day already examined for missed sessions of an assignment (Task 3). */
@Entity
@Table(name = "schedule_tracking")
public class ScheduleTracking {

    @Id
    @Column(name = "plan_assignment_id")
    private UUID planAssignmentId;

    @Column(nullable = false)
    private LocalDate checkedUntil;

    protected ScheduleTracking() {
    }

    ScheduleTracking(UUID planAssignmentId, LocalDate checkedUntil) {
        this.planAssignmentId = planAssignmentId;
        this.checkedUntil = checkedUntil;
    }

    void checkedUntil(LocalDate date) {
        this.checkedUntil = date;
    }

    public UUID getPlanAssignmentId() { return planAssignmentId; }
    public LocalDate getCheckedUntil() { return checkedUntil; }
}
