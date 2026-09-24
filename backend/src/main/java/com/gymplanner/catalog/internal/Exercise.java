package com.gymplanner.catalog.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "exercises")
public class Exercise extends CatalogItem {

    protected Exercise() {
    }

    Exercise(String name) {
        super(name);
    }
}
