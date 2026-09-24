package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserRole;
import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * US-03: the user edits only the phone; role, username, email, names and assignments are read
 * only. The password is changed through {@code /api/auth/change-password}.
 */
@RestController
@RequestMapping("/api/me/profile")
class ProfileController {

    record ProfileResponse(UUID id, String firstName, String lastName, String username, String email, String phone,
            UserRole role) {

        static ProfileResponse of(User u) {
            return new ProfileResponse(u.getId(), u.getFirstName(), u.getLastName(), u.getUsername(), u.getEmail(),
                    u.getPhone(), u.getRole());
        }
    }

    record ProfileRequest(
            @Size(max = 30) @Pattern(regexp = AdminUserDtos.PHONE_PATTERN, message = "is not a valid phone number")
            String phone) {
    }

    private final ProfileService service;

    ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    ProfileResponse get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ProfileResponse.of(service.get(principal.id()));
    }

    @PutMapping
    ProfileResponse update(@AuthenticationPrincipal AuthenticatedUser principal, @Valid @RequestBody ProfileRequest body) {
        return ProfileResponse.of(service.updatePhone(principal.id(), body.phone()));
    }
}
