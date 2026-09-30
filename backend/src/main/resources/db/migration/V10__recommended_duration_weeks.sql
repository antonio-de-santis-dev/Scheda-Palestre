-- The old absolute expiry is kept as historical data; a shared plan now uses a relative duration.
ALTER TABLE workout_plans ADD COLUMN duration_weeks integer;
ALTER TABLE workout_plans ADD CONSTRAINT ck_workout_plans_duration_weeks
    CHECK (duration_weeks IS NULL OR duration_weeks BETWEEN 1 AND 520);
