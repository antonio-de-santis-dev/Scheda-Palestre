package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request/response bodies of {@code /api/auth/**}. */
final class AuthDtos {

    private AuthDtos() {
    }

    record LoginRequest(
            @NotBlank @Size(max = 254) String username,
            @NotBlank @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=****]";
        }
    }

    record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[****]";
        }
    }

    record CurrentUserResponse(UUID id, String username, String firstName, String lastName, String email,
            UserRole role, boolean mustChangePassword) {

        static CurrentUserResponse of(User user) {
            return new CurrentUserResponse(user.getId(), user.getUsername(), user.getFirstName(),
                    user.getLastName(), user.getEmail(), user.getRole(), user.isMustChangePassword());
        }
    }

    record CsrfResponse(String headerName, String token) {
    }
}
