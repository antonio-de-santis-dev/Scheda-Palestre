package com.gymplanner.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Catalog access for the workout plan module. */
public interface CatalogLookup {

    /** Bulk lookup (no N+1). Inactive items are included: they stay visible in existing plans. */
    Map<UUID, CatalogItemView> muscleGroups(Collection<UUID> ids);

    Map<UUID, CatalogItemView> exercises(Collection<UUID> ids);

    /**
     * Returns the muscle group if it can be used in a new configuration.
     *
     * @throws com.gymplanner.shared.error.NotFoundException      if it does not exist
     * @throws com.gymplanner.shared.error.BusinessRuleException  {@code CATALOG_ITEM_INACTIVE} if deactivated
     */
    CatalogItemView requireSelectableMuscleGroup(UUID id);

    CatalogItemView requireSelectableExercise(UUID id);
}
