-- Task 1: recovery after the last set of an exercise, before the next exercise starts.
-- Belongs to the single plan exercise, so one workout can use different recoveries.
ALTER TABLE plan_exercises ADD COLUMN rest_after_seconds integer;

-- Existing plans keep their behaviour: until now the rest of the last set ran between exercises.
UPDATE plan_exercises pe
SET rest_after_seconds = coalesce(
        (SELECT ps.rest_seconds FROM plan_sets ps WHERE ps.plan_exercise_id = pe.id
         ORDER BY ps.set_index DESC LIMIT 1),
        pe.rest_seconds);

ALTER TABLE plan_exercises ALTER COLUMN rest_after_seconds SET NOT NULL;
ALTER TABLE plan_exercises ADD CONSTRAINT ck_plan_exercises_rest_after
    CHECK (rest_after_seconds BETWEEN 0 AND 1200);

-- Immutable snapshot in the workout. NULL = workout started before this migration: the rest of
-- the last set is used, exactly as before.
ALTER TABLE workout_exercises ADD COLUMN rest_after_seconds integer;
ALTER TABLE workout_exercises ADD CONSTRAINT ck_workout_exercises_rest_after
    CHECK (rest_after_seconds IS NULL OR rest_after_seconds BETWEEN 0 AND 1200);
