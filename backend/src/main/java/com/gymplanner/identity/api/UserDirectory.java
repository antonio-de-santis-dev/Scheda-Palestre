package com.gymplanner.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read-only access to accounts for other modules (e.g. assignment). */
public interface UserDirectory {

    Optional<UserSummary> find(UUID id);

    /** Bulk lookup to avoid N+1 queries; unknown ids are simply absent from the map. */
    Map<UUID, UserSummary> findAll(Collection<UUID> ids);
}
