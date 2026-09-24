package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserRole;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first ADMIN (spec 10.1) when no ADMIN exists. Missing configuration aborts the
 * startup with a clear message. The password is never logged.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, AdminBootstrapProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(UserRole.ADMIN)) {
            log.info("Admin bootstrap skipped: an ADMIN account already exists");
            return;
        }
        List<String> missing = new ArrayList<>();
        if (isBlank(properties.username())) {
            missing.add("GYM_ADMIN_USERNAME");
        }
        if (isBlank(properties.email())) {
            missing.add("GYM_ADMIN_EMAIL");
        }
        if (isBlank(properties.password())) {
            missing.add("GYM_ADMIN_PASSWORD");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No ADMIN account exists and the initial admin cannot be created: "
                    + "missing environment variable(s) " + String.join(", ", missing));
        }
        if (!PasswordPolicy.isValid(properties.password())) {
            throw new IllegalStateException("GYM_ADMIN_PASSWORD does not satisfy the password policy "
                    + "(8-128 characters, at least one letter and one digit)");
        }
        String username = properties.username().trim();
        String email = properties.email().trim();
        if (users.existsUsername(username, null) || users.existsEmail(email, null)) {
            throw new IllegalStateException("Cannot create the initial ADMIN: username or email already used by a USER");
        }
        User admin = new User(properties.firstName(), properties.lastName(), username, email, null,
                passwordEncoder.encode(properties.password()), UserRole.ADMIN);
        users.save(admin);
        log.info("Initial ADMIN account '{}' created; password change required at first login", username);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
