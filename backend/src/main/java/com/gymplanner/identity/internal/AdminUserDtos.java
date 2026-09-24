package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Bodies of {@code /api/admin/users/**}. The role is never accepted from the client (US-01). */
final class AdminUserDtos {

    static final String USERNAME_PATTERN = "^[A-Za-z0-9._-]+$";
    /** Empty is accepted (phone is optional) and normalised to null. */
    static final String PHONE_PATTERN = "^$|^[+0-9 ()./-]{5,30}$";

    private AdminUserDtos() {
    }

    record UserRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = USERNAME_PATTERN,
                    message = "may contain only letters, digits, dot, underscore and dash") String username,
            @NotBlank @Email @Size(max = 254) String email,
            @Size(max = 30) @Pattern(regexp = PHONE_PATTERN, message = "is not a valid phone number") String phone) {

        UserRequest normalized() {
            return new UserRequest(firstName.trim(), lastName.trim(), username.trim(), email.trim(),
                    phone == null || phone.isBlank() ? null : phone.trim());
        }
    }

    record UserResponse(UUID id, String firstName, String lastName, String username, String email, String phone,
            UserRole role, boolean active, boolean mustChangePassword, boolean locked, Instant createdAt,
            Instant updatedAt) {

        static UserResponse of(User user, Instant now) {
            return new UserResponse(user.getId(), user.getFirstName(), user.getLastName(), user.getUsername(),
                    user.getEmail(), user.getPhone(), user.getRole(), user.isActive(), user.isMustChangePassword(),
                    user.isLocked(now), user.getCreatedAt(), user.getUpdatedAt());
        }
    }

    /** The temporary password is returned only in this response and never stored in clear. */
    record UserWithPasswordResponse(UserResponse user, String temporaryPassword) {

        @Override
        public String toString() {
            return "UserWithPasswordResponse[user=" + user.id() + ", temporaryPassword=****]";
        }
    }
}
