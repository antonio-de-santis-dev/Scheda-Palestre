package com.gymplanner.execution.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration(proxyBeanMethods=false)
@EnableScheduling
class PushScheduling {}

@Component
class PushReminders {
    private final JdbcTemplate jdbc;
    private final PushProperties properties;
    private final PushTransport transport;
    private final Clock clock;
    private final TransactionTemplate transactions;
    PushReminders(JdbcTemplate jdbc, PushProperties properties, PushTransport transport, Clock clock, PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.properties=properties; this.transport=transport; this.clock=clock; this.transactions=new TransactionTemplate(manager);
    }
    @Scheduled(fixedDelayString="${gymplanner.push.poll-ms:10000}")
    public void poll() {
        if (!properties.configured()) return;
        jdbc.update("delete from push_subscriptions where updated_at < ?", Timestamp.from(clock.instant().minus(Duration.ofDays(90))));
        // SKIP LOCKED + revision marker prevent duplicate claims across backend replicas.
        var claimed = transactions.execute(status -> {
            var workouts = jdbc.queryForList("""
                select w.id, w.user_id, w.rest_version from workouts w where w.status = 'IN_PROGRESS'
                and w.rest_remaining_millis is null and w.rest_ends_at <= ? and w.rest_ends_at > ?
                and w.rest_notified_version <> w.rest_version
                order by w.rest_ends_at limit 10 for update skip locked
                """, Timestamp.from(clock.instant()), Timestamp.from(clock.instant().minusSeconds(120)));
            for (var w : workouts) jdbc.update("update workouts set rest_notified_version = rest_version where id = ?", (UUID)w.get("id"));
            return workouts;
        });
        if (claimed == null) return;
        for (var w : claimed) {
                Integer stillDue = jdbc.queryForObject("select count(*) from workouts where id = ? and status = 'IN_PROGRESS' and rest_version = ? and rest_ends_at <= ? and rest_remaining_millis is null",
                    Integer.class, w.get("id"), w.get("rest_version"), Timestamp.from(clock.instant()));
                if (stillDue == null || stillDue != 1) continue;
                var devices = jdbc.queryForList("""
                    select p.* from push_subscriptions p join users u on u.id = p.user_id
                    where p.user_id = ? and p.session_version = u.session_version and u.active and u.deleted_at is null
                    """, w.get("user_id"));
                for (var device : devices) {
                    try {
                        int code = transport.send((String)device.get("endpoint"), (String)device.get("p256dh"), (String)device.get("auth"));
                        if (code == 404 || code == 410) jdbc.update("delete from push_subscriptions where endpoint_hash = ? and user_id = ? and session_version = ?", device.get("endpoint_hash"), device.get("user_id"), device.get("session_version"));
                    } catch (Exception e) {
                        // Best effort: do not block workout execution or log endpoint/key material.
                        org.slf4j.LoggerFactory.getLogger(PushReminders.class).warn("Push delivery failed");
                        if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                    }
                }
        }
    }
}
