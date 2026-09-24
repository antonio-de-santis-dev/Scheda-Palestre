package com.gymplanner.identity.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/** Stores and clears the authenticated principal in the server-side HTTP session. */
@Component
class SessionAuthentication {

    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();
    private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

    SecurityContextRepository repository() {
        return repository;
    }

    static AuthenticatedUser principalOf(User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole().name(),
                user.isMustChangePassword(), user.getSessionVersion());
    }

    /** Establishes the session after a successful login, rotating the session id (fixation). */
    void login(User user, HttpServletRequest request, HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        store(principalOf(user), request, response);
    }

    /** Replaces the principal of the current session (e.g. after a password change). */
    void store(AuthenticatedUser principal, HttpServletRequest request, HttpServletResponse response) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        SecurityContext context = holder.createEmptyContext();
        context.setAuthentication(authentication);
        holder.setContext(context);
        repository.saveContext(context, request, response);
    }

    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holder.clearContext();
    }
}
