package com.gymplanner.shared.security;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Principal stored in the HTTP session. It lives in {@code shared} so every module can obtain
 * the current user id from the security context without depending on {@code identity}.
 * Controllers of {@code /api/me/**} must derive the user exclusively from this principal.
 *
 * @param role           {@code ADMIN} or {@code USER}
 * @param sessionVersion copy of the account's session version at login; a mismatch with the
 *                       database invalidates the session (deactivation, password reset)
 */
public record AuthenticatedUser(UUID id, String username, String role, boolean mustChangePassword,
        int sessionVersion) implements Serializable {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
