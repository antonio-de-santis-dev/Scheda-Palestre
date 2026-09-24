package com.gymplanner.catalog.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CatalogItemRequest(@NotBlank @Size(max = 100) String name) {
}
