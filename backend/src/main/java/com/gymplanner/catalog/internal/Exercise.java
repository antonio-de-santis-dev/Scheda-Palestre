package com.gymplanner.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** An exercise belongs to exactly one muscle group of the same module (plain id, no lazy loading). */
@Entity
@Table(name = "exercises")
public class Exercise extends CatalogItem {

    @Column(name = "muscle_group_id", nullable = false)
    private UUID muscleGroupId;

    protected Exercise() {
    }

    Exercise(String name, UUID muscleGroupId) {
        super(name);
        this.muscleGroupId = muscleGroupId;
    }

    void moveTo(UUID muscleGroupId) {
        this.muscleGroupId = muscleGroupId;
    }

    public UUID getMuscleGroupId() {
        return muscleGroupId;
    }
}
