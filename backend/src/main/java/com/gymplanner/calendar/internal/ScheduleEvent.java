package com.gymplanner.calendar.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A training day that was not used (Task 3): automatically MISSED at the end of the day, SKIPPED
 * or MOVED by the USER. While active, the day does not consume a session of the rotation, so the
 * same session goes to the next training slot (or to the target day of a move).
 */
@Entity
@Table(name = "schedule_events")
public class ScheduleEvent {

    public enum Kind { MISSED, SKIPPED, MOVED }

    @Id
    private UUID id;

    @Column(name = "plan_assignment_id", nullable = false, updatable = false)
    private UUID planAssignmentId;

    @Column(nullable = false, updatable = false)
    private LocalDate originalDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    private LocalDate targetDate;

    private UUID sessionId;

    @Column(nullable = false, length = 60)
    private String sessionTitle;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant cancelledAt;

    protected ScheduleEvent() {
    }

    ScheduleEvent(UUID planAssignmentId, LocalDate originalDate, Kind kind, LocalDate targetDate, UUID sessionId,
            String sessionTitle, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.planAssignmentId = planAssignmentId;
        this.originalDate = originalDate;
        this.createdAt = createdAt;
        reactivate(kind, targetDate, sessionId, sessionTitle, createdAt);
    }

    /** A cancelled past event can be marked again (e.g. "this day was skipped after all"). */
    void reactivate(Kind kind, LocalDate targetDate, UUID sessionId, String sessionTitle, Instant now) {
        this.kind = kind;
        this.targetDate = targetDate;
        this.sessionId = sessionId;
        this.sessionTitle = sessionTitle;
        this.createdAt = now;
        this.cancelledAt = null;
    }

    void cancel(Instant now) {
        this.cancelledAt = now;
    }

    boolean isActive() {
        return cancelledAt == null;
    }

    public UUID getId() { return id; }
    public UUID getPlanAssignmentId() { return planAssignmentId; }
    public LocalDate getOriginalDate() { return originalDate; }
    public Kind getKind() { return kind; }
    public LocalDate getTargetDate() { return targetDate; }
    public UUID getSessionId() { return sessionId; }
    public String getSessionTitle() { return sessionTitle; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
}
