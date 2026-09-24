package com.gymplanner.catalog.api;

import java.util.UUID;

/** Read-only view of a muscle group or an exercise for other modules. */
public record CatalogItemView(UUID id, String name, boolean active) {
}
