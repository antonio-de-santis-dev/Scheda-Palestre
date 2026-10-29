CREATE TABLE password_reset_tokens (
    token_hash char(64) PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    session_version integer NOT NULL,
    email varchar(255) NOT NULL,
    expires_at timestamptz NOT NULL,
    requested_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX ux_password_reset_user ON password_reset_tokens(user_id);
CREATE TABLE recovery_rate_limits (
    bucket char(64) PRIMARY KEY,
    window_start timestamptz NOT NULL,
    requests integer NOT NULL
);
