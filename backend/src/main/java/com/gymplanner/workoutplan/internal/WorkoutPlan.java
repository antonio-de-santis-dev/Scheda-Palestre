package com.gymplanner.workoutplan.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Shared workout plan (spec 8.5). It never references a single USER: assignments live in the
 * assignment module. {@code createdBy} is the authoring ADMIN (referenced by id, see ADR 0002).
 */
@Entity
@Table(name = "workout_plans")
public class WorkoutPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    private LocalDate expiresOn;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "copied_from_plan_id", updatable = false)
    private UUID copiedFromPlanId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "workoutPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<PlanSession> sessions = new ArrayList<>();

    protected WorkoutPlan() {
    }

    WorkoutPlan(String name, String description, LocalDate expiresOn, UUID createdBy, UUID copiedFromPlanId) {
        this.name = name;
        this.description = description;
        this.expiresOn = expiresOn;
        this.createdBy = createdBy;
        this.copiedFromPlanId = copiedFromPlanId;
    }

    void updateMetadata(String name, String description, LocalDate expiresOn) {
        this.name = name;
        this.description = description;
        this.expiresOn = expiresOn;
    }

    /** Marks a structural change so that updatedAt and the optimistic version move forward. */
    void touch(Instant now) {
        this.updatedAt = now;
    }

    void markDeleted(Instant now) {
        this.deletedAt = now;
    }

    void restore() {
        this.deletedAt = null;
    }

    boolean isDeleted() {
        return deletedAt != null;
    }

    PlanSession addSession(String title) {
        PlanSession session = new PlanSession(this, title, sessions.size() + 1);
        sessions.add(session);
        return session;
    }

    void removeSession(PlanSession session) {
        sessions.remove(session);
        renumber(sessions);
    }

    static <T extends Positioned> void renumber(List<T> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setPosition(i + 1);
        }
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getCopiedFromPlanId() {
        return copiedFromPlanId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<PlanSession> getSessions() {
        return sessions;
    }
}
