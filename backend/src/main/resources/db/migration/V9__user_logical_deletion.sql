-- GymPlanner - logical deletion of accounts with anonymization (ADR 0010).
-- Rows referencing users (workout_plans.created_by, plan_assignments.user_id/assigned_by,
-- workouts.user_id) are kept: a physical delete would destroy history and integrity.
-- A deleted account is never active; its personal data are replaced by deterministic
-- placeholders by the application in the same transaction that sets deleted_at.

ALTER TABLE users ADD COLUMN deleted_at timestamptz;
ALTER TABLE users ADD CONSTRAINT ck_users_deleted_inactive CHECK (deleted_at IS NULL OR NOT active);
CREATE INDEX ix_users_not_deleted ON users (role, active) WHERE deleted_at IS NULL;
