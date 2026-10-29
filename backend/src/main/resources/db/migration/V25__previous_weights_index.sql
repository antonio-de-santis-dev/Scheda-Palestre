-- Task 5: "last weight used" looks up earlier workouts by stable catalog identity.
CREATE INDEX ix_workout_exercises_catalog ON workout_exercises (catalog_exercise_id)
    WHERE catalog_exercise_id IS NOT NULL;
