package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.error.UnauthorizedException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credential verification with temporary lock (spec 11) and password change. Every failure of
 * the login returns the same generic error to avoid account enumeration.
 */
@Service
class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final String dummyHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, SecurityProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        // Used to spend comparable time when the username does not exist.
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User authenticate(String username, String rawPassword) {
        Optional<User> found = users.findByUsernameIgnoreCase(username.trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword, dummyHash);
            throw invalidCredentials();
        }
        User user = found.get();
        var now = clock.instant();
        if (user.isLocked(now)) {
            passwordEncoder.matches(rawPassword, dummyHash);
            log.info("Login rejected for locked account id={}", user.getId());
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            user.registerFailedLogin(now, properties.maxFailedLogins(), properties.lockDuration());
            if (user.isLocked(now)) {
                log.warn("Account id={} temporarily locked after repeated failed logins", user.getId());
            }
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            log.info("Login rejected for inactive account id={}", user.getId());
            throw invalidCredentials();
        }
        user.registerSuccessfulLogin();
        return user;
    }

    @Transactional
    public User changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw BadRequestException.field("currentPassword", "Current password is not correct");
        }
        PasswordPolicy.validate("newPassword", newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw BadRequestException.field("newPassword", "New password must differ from the current one");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return user;
    }

    @Transactional(readOnly = true)
    public User get(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User"));
    }

    private static UnauthorizedException invalidCredentials() {
        return new UnauthorizedException("INVALID_CREDENTIALS", "Invalid username or password");
    }
}
