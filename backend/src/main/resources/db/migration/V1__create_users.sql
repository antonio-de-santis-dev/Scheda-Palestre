-- GymPlanner - identity module
-- Table is named "users" because "user" is a reserved word in PostgreSQL.

CREATE TABLE users (
    id                   uuid         PRIMARY KEY,
    first_name           varchar(80)  NOT NULL,
    last_name            varchar(80)  NOT NULL,
    username             varchar(50)  NOT NULL,
    email                varchar(254) NOT NULL,
    phone                varchar(30),
    password_hash        varchar(255) NOT NULL,
    role                 varchar(10)  NOT NULL,
    active               boolean      NOT NULL DEFAULT true,
    must_change_password boolean      NOT NULL DEFAULT true,
    failed_login_count   integer      NOT NULL DEFAULT 0,
    locked_until         timestamptz,
    -- Incremented when existing sessions must be invalidated (deactivation, password reset/change).
    session_version      integer      NOT NULL DEFAULT 0,
    created_at           timestamptz  NOT NULL,
    updated_at           timestamptz  NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_users_failed_login_count CHECK (failed_login_count >= 0),
    CONSTRAINT ck_users_first_name_not_blank CHECK (length(btrim(first_name)) > 0),
    CONSTRAINT ck_users_last_name_not_blank CHECK (length(btrim(last_name)) > 0),
    CONSTRAINT ck_users_username_not_blank CHECK (length(btrim(username)) > 0)
);

-- Case-insensitive uniqueness.
CREATE UNIQUE INDEX ux_users_username_ci ON users (lower(username));
CREATE UNIQUE INDEX ux_users_email_ci ON users (lower(email));
CREATE INDEX ix_users_role_active ON users (role, active);
