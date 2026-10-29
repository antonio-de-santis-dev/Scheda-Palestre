-- GymPlanner - a USER can have several active plans at the same time (ADR 0008).
-- Rules after this migration:
--   * at most one ACTIVE assignment of the same plan per user (unique partial index below);
--   * a weekday belongs to at most one active plan of the user: enforced by the calendar module
--     under a per-user advisory lock (pg_advisory_xact_lock), see ADR 0008;
--   * still at most one workout IN_PROGRESS per user (ux_workouts_user_in_progress, unchanged).
-- Existing data cannot violate the new rule because the old index allowed only one active
-- assignment per user; the check below documents it and stops the migration loudly otherwise.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM plan_assignments WHERE active
        GROUP BY user_id, workout_plan_id HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'V8: duplicated active assignments of the same plan for a user; close the extra ones first';
    END IF;
END $$;

DROP INDEX ux_plan_assignments_active_user;
CREATE UNIQUE INDEX ux_plan_assignments_active_user_plan ON plan_assignments (user_id, workout_plan_id) WHERE active;
CREATE INDEX ix_plan_assignments_user_active ON plan_assignments (user_id) WHERE active;
