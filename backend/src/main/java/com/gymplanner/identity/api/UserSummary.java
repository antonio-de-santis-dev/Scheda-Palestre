package com.gymplanner.identity.api;

import java.util.UUID;

/** Public, non-sensitive view of an account for other modules. */
public record UserSummary(UUID id, String firstName, String lastName, String username, String email,
        UserRole role, boolean active) {

    public String fullName() {
        return firstName + " " + lastName;
    }
}
