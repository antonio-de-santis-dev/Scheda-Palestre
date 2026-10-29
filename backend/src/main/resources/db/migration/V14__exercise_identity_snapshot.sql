-- Immutable catalog identity for new workouts. No guessed catalog backfill.
ALTER TABLE workout_exercises ADD COLUMN catalog_exercise_id uuid;
-- Preserve the original legacy grouping even after the plan entry is removed (SET NULL).
ALTER TABLE workout_exercises ADD COLUMN legacy_exercise_id uuid;
UPDATE workout_exercises SET legacy_exercise_id = coalesce(plan_exercise_id, id);
-- No FKs: identities must survive catalog/plan changes.
