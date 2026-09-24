package com.gymplanner.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Converts the UTC clock into calendar dates using the gym's configured time zone. Used only
 * where the server itself must decide "today" (e.g. closing an assignment, re-anchoring the
 * rotation). Dates requested by the client are always taken from the client.
 */
@Component
public class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, @Value("${gymplanner.time-zone:Europe/Rome}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    /** Current instant truncated to milliseconds (stable across JSON and PostgreSQL). */
    public Instant now() {
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    public ZoneId zone() {
        return zone;
    }
}
