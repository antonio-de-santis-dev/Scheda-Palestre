package com.gymplanner.shared.concurrency;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serialises the operations that change the set of active plans or weekly days of one USER
 * (ADR 0008). It takes a PostgreSQL transaction-level advisory lock keyed by the user id: the
 * lock is released automatically at commit/rollback, it is re-entrant inside the same
 * transaction and it does not block other users. Callers must re-read and validate the data
 * <em>after</em> acquiring it.
 */
@Component
public class UserLock {

    /** Namespace so these keys never collide with other advisory locks. */
    private static final long NAMESPACE = 0x4750_5553_4552_4C4BL; // "GPUSERLK"

    private final JdbcTemplate jdbc;

    public UserLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(UUID userId) {
        long key = NAMESPACE ^ userId.getMostSignificantBits() ^ Long.rotateLeft(userId.getLeastSignificantBits(), 17);
        jdbc.queryForObject("select pg_advisory_xact_lock(?)", Object.class, key);
    }
}
