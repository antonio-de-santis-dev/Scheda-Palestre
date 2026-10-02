package com.gymplanner.execution.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.security.AuthenticatedUser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class PushService {
    private final JdbcTemplate jdbc;
    private final PushProperties properties;
    private final Clock clock;
    record Keys(String p256dh, String auth) {}
    record Subscription(String endpoint, Keys keys) {}

    @Transactional
    public void subscribe(AuthenticatedUser user, Subscription subscription) {
        if (!properties.configured()) throw new BadRequestException("PUSH_NOT_CONFIGURED", "Push unavailable");
        validate(subscription);
        // User row serialises the per-account device limit, also against concurrent registration.
        jdbc.queryForObject("select id from users where id = ? for update", java.util.UUID.class, user.id());
        jdbc.update("delete from push_subscriptions where user_id = ? and (session_version <> ? or updated_at < ?)",
            user.id(), user.sessionVersion(), Timestamp.from(clock.instant().minus(Duration.ofDays(90))));
        String hash = hash(subscription.endpoint());
        Integer count = jdbc.queryForObject("select count(*) from push_subscriptions where user_id = ? and endpoint_hash <> ?", Integer.class, user.id(), hash);
        if (count != null && count >= 5) throw new BadRequestException("PUSH_DEVICE_LIMIT", "Maximum 5 devices");
        int changed = jdbc.update("""
            insert into push_subscriptions values (?, ?, ?, ?, ?, ?, ?)
            on conflict (endpoint_hash) do update set session_version = excluded.session_version,
                p256dh = excluded.p256dh, auth = excluded.auth, updated_at = excluded.updated_at
            where push_subscriptions.user_id = excluded.user_id
            """, hash, user.id(), user.sessionVersion(), subscription.endpoint(), subscription.keys().p256dh(),
                subscription.keys().auth(), Timestamp.from(clock.instant()));
        if (changed == 0) throw new ConflictException("PUSH_ALREADY_REGISTERED", "Unsubscribe this browser before changing accounts");
    }
    @Transactional
    public void unsubscribe(AuthenticatedUser user, String endpoint) {
        if (endpoint == null || endpoint.length() > 4096) throw BadRequestException.field("endpoint", "Invalid endpoint");
        jdbc.update("delete from push_subscriptions where endpoint_hash = ? and user_id = ?", hash(endpoint), user.id());
    }
    static void validate(Subscription s) {
        try {
            if (s == null || s.endpoint() == null || s.endpoint().length() > 4096 || s.keys() == null || s.keys().p256dh() == null || s.keys().p256dh().length() > 128
                || s.keys().auth() == null || s.keys().auth().length() > 64) throw new IllegalArgumentException();
            URI uri = URI.create(s.endpoint()); String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getFragment() != null
                || host == null || !(host.equals("fcm.googleapis.com") || host.equals("web.push.apple.com")
                    || host.equals("updates.push.services.mozilla.com") || host.endsWith(".push.services.mozilla.com"))) throw new IllegalArgumentException();
            byte[] key = Base64.getUrlDecoder().decode(s.keys().p256dh());
            byte[] auth = Base64.getUrlDecoder().decode(s.keys().auth());
            if (key.length != 65 || key[0] != 4 || auth.length != 16) throw new IllegalArgumentException();
        } catch (Exception e) { throw BadRequestException.field("subscription", "Invalid or unsupported push subscription"); }
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
