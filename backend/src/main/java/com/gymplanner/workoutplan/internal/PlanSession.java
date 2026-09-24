package com.gymplanner.workoutplan.internal;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A session of the plan ("Giorno 1", "Giorno 2", ...), rotated by the calendar (spec 8.6). */
@Entity
@Table(name = "plan_sessions")
public class PlanSession implements Positioned {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_plan_id", nullable = false, updatable = false)
    private WorkoutPlan workoutPlan;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "planSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<MuscleSection> sections = new ArrayList<>();

    protected PlanSession() {
    }

    PlanSession(WorkoutPlan workoutPlan, String title, int position) {
        this.workoutPlan = workoutPlan;
        this.title = title;
        this.position = position;
    }

    MuscleSection addSection(UUID muscleGroupId) {
        MuscleSection section = new MuscleSection(this, muscleGroupId, sections.size() + 1);
        sections.add(section);
        return section;
    }

    void removeSection(MuscleSection section) {
        sections.remove(section);
        WorkoutPlan.renumber(sections);
    }

    boolean hasMuscleGroup(UUID muscleGroupId) {
        return sections.stream().anyMatch(s -> s.getMuscleGroupId().equals(muscleGroupId));
    }

    long exerciseCount() {
        return sections.stream().mapToLong(s -> s.getExercises().size()).sum();
    }

    void rename(String title) {
        this.title = title;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public WorkoutPlan getWorkoutPlan() {
        return workoutPlan;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public void setPosition(int position) {
        this.position = position;
    }

    public List<MuscleSection> getSections() {
        return sections;
    }
}
