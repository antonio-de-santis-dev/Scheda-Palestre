package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.BadRequestException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RecoveryService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RecoveryProperties properties;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void request(String email, String remoteAddress) {
        if (!properties.isEnabled()) return;
        URI origin;
        try { origin = URI.create(properties.getPublicUrl()); } catch (Exception e) { return; }
        if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getQuery() != null
                || origin.getFragment() != null || origin.getUserInfo() != null
                || !(origin.getPath().isEmpty() || origin.getPath().equals("/")) || properties.getFrom().isBlank()) return;
        var now = clock.instant();
        jdbc.update("delete from recovery_rate_limits where window_start < ?", Timestamp.from(now.minus(Duration.ofHours(1))));
        if (!allow("ip:" + remoteAddress, 20) || !allow("email:" + email.strip().toLowerCase(java.util.Locale.ROOT), 3)) return;
        var found = users.findByEmailIgnoreCase(email.strip());
        if (found.isEmpty()) return;
        User user = users.lockById(found.get().getId()).orElseThrow();
        if (!user.isActive() || user.isDeleted()) return;
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("delete from password_reset_tokens where user_id = ? or expires_at <= ?", user.getId(), Timestamp.from(now));
        jdbc.update("insert into password_reset_tokens values (?, ?, ?, ?, ?, ?)", hash(raw), user.getId(),
                user.getSessionVersion(), user.getEmail(), Timestamp.from(now.plus(Duration.ofMinutes(20))), Timestamp.from(now));
        String base = properties.getPublicUrl().replaceAll("/+$", "");
        events.publishEvent(new RecoveryRequested(user.getEmail(), base + "/reset-password#token=" + raw));
    }

    private boolean allow(String bucket, int limit) {
        Integer count = jdbc.queryForObject("""
            insert into recovery_rate_limits (bucket, window_start, requests) values (?, ?, 1)
            on conflict (bucket) do update set requests = recovery_rate_limits.requests + 1
            returning requests
            """, Integer.class, hash(bucket), Timestamp.from(clock.instant()));
        return count != null && count <= limit;
    }

    @Transactional
    public void reset(String token, String password) {
        PasswordPolicy.validate("newPassword", password);
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String hash = hash(token);
        var rows = jdbc.queryForList("select user_id from password_reset_tokens where token_hash = ?", hash);
        if (rows.isEmpty()) throw invalid();
        User user = users.lockById((UUID) rows.getFirst().get("user_id")).orElseThrow(RecoveryService::invalid);
        Integer valid = jdbc.queryForObject("""
            select count(*) from password_reset_tokens where token_hash = ? and expires_at > ?
            and session_version = ? and email = ?
            """, Integer.class, hash, Timestamp.from(clock.instant()), user.getSessionVersion(), user.getEmail());
        if (!properties.isEnabled() || valid == null || valid != 1 || !user.isActive() || user.isDeleted()) throw invalid();
        if (encoder.matches(password, user.getPasswordHash())) throw BadRequestException.field("newPassword", "Choose a different password");
        user.changePassword(encoder.encode(password)); user.registerSuccessfulLogin();
        jdbc.update("delete from password_reset_tokens where user_id = ?", user.getId());
    }
    static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static BadRequestException invalid() { return new BadRequestException("RESET_LINK_INVALID", "Reset link invalid or expired"); }
}
