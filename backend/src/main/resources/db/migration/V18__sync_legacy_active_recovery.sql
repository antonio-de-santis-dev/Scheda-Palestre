-- V12 was already applied while fixV2 still derived rest from the latest set.
-- Synchronize only untouched (version 0) timers; preserve running/paused V2 controls.
WITH latest AS (
    SELECT DISTINCT ON (we.workout_id) we.workout_id, ws.completed_at, ws.rest_seconds
    FROM workout_sets ws JOIN workout_exercises we ON we.id = ws.workout_exercise_id
    WHERE ws.completed_at IS NOT NULL
    ORDER BY we.workout_id, ws.completed_at DESC, we.position DESC, ws.set_index DESC
)
UPDATE workouts w
SET rest_ends_at = CASE WHEN latest.rest_seconds > 0
        THEN latest.completed_at + latest.rest_seconds * INTERVAL '1 second' ELSE NULL END,
    rest_duration_seconds = NULLIF(latest.rest_seconds, 0),
    rest_version = 1
FROM latest
WHERE latest.workout_id = w.id AND w.status = 'IN_PROGRESS'
    AND w.rest_version = 0 AND w.rest_remaining_millis IS NULL;
