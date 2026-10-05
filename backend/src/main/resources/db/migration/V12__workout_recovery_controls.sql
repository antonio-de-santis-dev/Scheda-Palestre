-- Mutable recovery state belongs to execution, never to the immutable set snapshot.
ALTER TABLE workouts ADD COLUMN rest_ends_at timestamptz;
ALTER TABLE workouts ADD COLUMN rest_remaining_millis bigint;
ALTER TABLE workouts ADD COLUMN rest_duration_seconds integer;
ALTER TABLE workouts ADD COLUMN rest_version bigint NOT NULL DEFAULT 0;
ALTER TABLE workouts ADD CONSTRAINT ck_workout_recovery_state CHECK (
    (rest_remaining_millis IS NULL OR (rest_remaining_millis > 0 AND rest_ends_at IS NULL))
    AND (rest_duration_seconds IS NULL OR rest_duration_seconds > 0)
    AND rest_version >= 0
);

-- Preserve running recovery for workouts started before V2.
WITH latest AS (
    SELECT DISTINCT ON (we.workout_id) we.workout_id, ws.completed_at, ws.rest_seconds
    FROM workout_sets ws
    JOIN workout_exercises we ON we.id = ws.workout_exercise_id
    WHERE ws.completed_at IS NOT NULL
    ORDER BY we.workout_id, ws.completed_at DESC, we.position DESC, ws.set_index DESC
)
UPDATE workouts w
SET rest_ends_at = latest.completed_at + latest.rest_seconds * INTERVAL '1 second',
    rest_duration_seconds = latest.rest_seconds
FROM latest
WHERE latest.workout_id = w.id AND w.status = 'IN_PROGRESS' AND latest.rest_seconds > 0;
