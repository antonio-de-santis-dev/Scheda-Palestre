package com.gymplanner.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "muscle_groups")
public class MuscleGroup extends CatalogItem {

    protected MuscleGroup() {
    }

    MuscleGroup(String name) {
        super(name);
    }
}
