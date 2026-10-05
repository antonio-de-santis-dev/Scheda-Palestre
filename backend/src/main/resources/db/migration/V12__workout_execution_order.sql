-- Reorder only the workout snapshot; the shared workout plan is unchanged.
ALTER TABLE workouts ADD COLUMN execution_version bigint NOT NULL DEFAULT 0;
ALTER TABLE workout_exercises DROP CONSTRAINT uq_workout_exercises_position;
ALTER TABLE workout_exercises ADD CONSTRAINT uq_workout_exercises_position
    UNIQUE (workout_id, position) DEFERRABLE INITIALLY DEFERRED;
