package com.gymplanner.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Bound from {@code GYM_ADMIN_*} environment variables (see application.properties). */
@ConfigurationProperties("gymplanner.admin")
public record AdminBootstrapProperties(
        String username,
        String email,
        String password,
        @DefaultValue("Admin") String firstName,
        @DefaultValue("GymPlanner") String lastName) {

    @Override
    public String toString() {
        // Never expose the password, even accidentally through logging of the record.
        return "AdminBootstrapProperties[username=" + username + ", email=" + email + ", password=****]";
    }
}
