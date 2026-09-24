package com.gymplanner.identity.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-validates the session principal against the database on every request: a deactivated
 * account or a changed session version (password reset/change, deactivation) invalidates the
 * session immediately (spec 11 / US-25); role and password-change flag are refreshed.
 */
class SessionUserRefreshFilter extends OncePerRequestFilter {

    private final UserRepository users;
    private final SessionAuthentication sessionAuthentication;

    SessionUserRefreshFilter(UserRepository users, SessionAuthentication sessionAuthentication) {
        this.users = users;
        this.sessionAuthentication = sessionAuthentication;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal) {
            Optional<User> current = users.findById(principal.id());
            if (current.isEmpty() || !current.get().isActive()
                    || current.get().getSessionVersion() != principal.sessionVersion()) {
                sessionAuthentication.logout(request);
            } else {
                AuthenticatedUser refreshed = SessionAuthentication.principalOf(current.get());
                if (!refreshed.equals(principal)) {
                    sessionAuthentication.store(refreshed, request, response);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
