CREATE TABLE push_subscriptions (
    endpoint_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_version integer NOT NULL,
    endpoint varchar(4096) NOT NULL,
    p256dh varchar(128) NOT NULL,
    auth varchar(64) NOT NULL,
    updated_at timestamptz NOT NULL
);
CREATE INDEX ix_push_subscriptions_user ON push_subscriptions(user_id);
ALTER TABLE workouts ADD COLUMN rest_notified_version bigint NOT NULL DEFAULT -1;
CREATE INDEX ix_workouts_push_rest ON workouts(rest_ends_at) WHERE status = 'IN_PROGRESS' AND rest_ends_at IS NOT NULL;
