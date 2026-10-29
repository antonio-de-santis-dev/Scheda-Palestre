package com.gymplanner.identity.api;

import java.util.UUID;

/** Synchronous domain events of the identity module (ADR 0002). */
public final class UserEvents {

    private UserEvents() {
    }

    /** An account was deleted logically (ADR 0010): its active assignments must be closed. */
    public record UserDeleted(UUID userId) {
    }
}
