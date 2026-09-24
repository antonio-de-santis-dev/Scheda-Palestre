package com.gymplanner.identity.internal;

import com.gymplanner.identity.internal.AuthDtos.ChangePasswordRequest;
import com.gymplanner.identity.internal.AuthDtos.CsrfResponse;
import com.gymplanner.identity.internal.AuthDtos.CurrentUserResponse;
import com.gymplanner.identity.internal.AuthDtos.LoginRequest;
import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final SessionAuthentication sessionAuthentication;

    AuthController(AuthService authService, SessionAuthentication sessionAuthentication) {
        this.authService = authService;
        this.sessionAuthentication = sessionAuthentication;
    }

    /** Issues the CSRF cookie ({@code XSRF-TOKEN}) the SPA must echo in {@code X-XSRF-TOKEN}. */
    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    CurrentUserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        User user = authService.authenticate(body.username(), body.password());
        sessionAuthentication.login(user, request, response);
        return CurrentUserResponse.of(user);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        sessionAuthentication.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return CurrentUserResponse.of(authService.get(principal.id()));
    }

    @PostMapping("/change-password")
    CurrentUserResponse changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request,
            HttpServletResponse response) {
        User user = authService.changePassword(principal.id(), body.currentPassword(), body.newPassword());
        // Other sessions are invalidated by the new session version; this one is refreshed.
        sessionAuthentication.store(SessionAuthentication.principalOf(user), request, response);
        return CurrentUserResponse.of(user);
    }
}
