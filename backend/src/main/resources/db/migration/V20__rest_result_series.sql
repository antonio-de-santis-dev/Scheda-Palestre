-- Persist the exact completed series whose results can be entered during recovery.
ALTER TABLE workouts ADD COLUMN rest_set_id uuid REFERENCES workout_sets(id) ON DELETE SET NULL;
-- Existing active recoveries: recover only an unambiguous latest completed series.
WITH candidates AS (
    SELECT w.id AS workout_id, s.id AS set_id,
           rank() OVER (PARTITION BY w.id ORDER BY s.completed_at DESC) AS recency
    FROM workouts w JOIN workout_exercises e ON e.workout_id = w.id
    JOIN workout_sets s ON s.workout_exercise_id = e.id
    WHERE w.status = 'IN_PROGRESS' AND s.completed_at IS NOT NULL
      AND (w.rest_ends_at IS NOT NULL OR w.rest_remaining_millis IS NOT NULL)
), unambiguous AS (
    SELECT workout_id FROM candidates WHERE recency = 1 GROUP BY workout_id HAVING count(*) = 1
)
UPDATE workouts w SET rest_set_id = c.set_id FROM candidates c JOIN unambiguous u USING (workout_id)
WHERE w.id = c.workout_id AND c.recency = 1;
