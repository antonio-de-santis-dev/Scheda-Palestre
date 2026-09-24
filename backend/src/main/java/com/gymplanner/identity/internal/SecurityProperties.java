package com.gymplanner.identity.internal;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param maxFailedLogins      failed attempts before a temporary lock (spec: 5)
 * @param lockDuration         lock length (spec: 15 minutes)
 * @param corsAllowedOrigins   origins allowed by CORS; the SPA is normally served same-origin
 */
@ConfigurationProperties("gymplanner.security")
public record SecurityProperties(
        @DefaultValue("5") int maxFailedLogins,
        @DefaultValue("15m") Duration lockDuration,
        @DefaultValue("http://localhost:5173") List<String> corsAllowedOrigins) {
}
