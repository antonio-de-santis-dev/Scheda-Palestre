package com.gymplanner.support;

import com.gymplanner.shared.security.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Creates accounts straight in the database (independently from the module under test) and
 * builds authenticated MockMvc requests for them.
 */
@TestComponent
public class TestFixtures {

    public static final String PASSWORD = "Password123";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private volatile String encodedDefault;

    public TestFixtures(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthenticatedUser createUser() {
        return create("USER", false);
    }

    public AuthenticatedUser createAdmin() {
        return create("ADMIN", false);
    }

    public AuthenticatedUser create(String role, boolean mustChangePassword) {
        String suffix = UUID.randomUUID().toString().substring(0, 8) + SEQ.incrementAndGet();
        String username = role.toLowerCase() + "_" + suffix;
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                insert into users (id, first_name, last_name, username, email, phone, password_hash, role,
                                   active, must_change_password, failed_login_count, session_version, created_at, updated_at)
                values (?, ?, ?, ?, ?, null, ?, ?, true, ?, 0, 0, ?, ?)
                """, id, "Test", "User " + suffix, username, username + "@example.test", encoded(), role,
                mustChangePassword, now, now);
        return new AuthenticatedUser(id, username, role, mustChangePassword, 0);
    }

    /** Inserts an active muscle group with a unique name and returns its id. */
    public UUID createMuscleGroup(String name) {
        return insertCatalog("muscle_groups", name);
    }

    public UUID createExercise(String name) {
        return insertCatalog("exercises", name);
    }

    private UUID insertCatalog(String table, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into " + table + " (id, name, active, created_at, updated_at) values (?, ?, true, now(), now())",
                id, name + " " + id.toString().substring(0, 8));
        return id;
    }

    public RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
    }

    private String encoded() {
        if (encodedDefault == null) {
            encodedDefault = passwordEncoder.encode(PASSWORD);
        }
        return encodedDefault;
    }
}
