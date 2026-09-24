package com.gymplanner.catalog.internal;

import java.time.Instant;
import java.util.UUID;

record CatalogItemResponse(UUID id, String name, boolean active, Instant createdAt, Instant updatedAt) {

    static CatalogItemResponse of(CatalogItem item) {
        return new CatalogItemResponse(item.getId(), item.getName(), item.isActive(), item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
