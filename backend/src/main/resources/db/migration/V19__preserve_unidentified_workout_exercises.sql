-- fixV2 snapshots created after V14 did not yet populate historical identity columns.
-- Preserve their own plan-entry identity when still available, otherwise their snapshot UUID.
-- Do not infer a catalog identity from a current plan relationship or from names.
UPDATE workout_exercises
SET legacy_exercise_id = coalesce(plan_exercise_id, id)
WHERE legacy_exercise_id IS NULL AND catalog_exercise_id IS NULL;
