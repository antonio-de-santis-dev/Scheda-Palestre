package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.ForbiddenException;
import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * While {@code mustChangePassword} is true only login, logout, current user, CSRF and password
 * change are allowed; everything else answers 403 {@code PASSWORD_CHANGE_REQUIRED}.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "/api/auth/login", "/api/auth/logout", "/api/auth/me", "/api/auth/change-password", "/api/auth/csrf");

    private final HandlerExceptionResolver resolver;

    PasswordChangeRequiredFilter(HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser principal
                && principal.mustChangePassword() && path.startsWith("/api/") && !ALLOWED.contains(path)) {
            resolver.resolveException(request, response, null,
                    new ForbiddenException("PASSWORD_CHANGE_REQUIRED", "Password change required before continuing"));
            return;
        }
        chain.doFilter(request, response);
    }
}
