-- V2: optional prescribed loads and actual results. NULL means unrecorded, never zero.
ALTER TABLE plan_exercises ADD COLUMN planned_weight_kg numeric(6,2);
ALTER TABLE plan_sets ADD COLUMN planned_weight_kg numeric(6,2);
ALTER TABLE workout_sets ADD COLUMN weight_kg_planned numeric(6,2);
ALTER TABLE workout_sets ADD COLUMN weight_kg_used numeric(6,2);
ALTER TABLE workout_sets ADD COLUMN reps_actual integer;
ALTER TABLE plan_exercises ADD CONSTRAINT ck_plan_exercises_weight
    CHECK (planned_weight_kg BETWEEN 0 AND 1000);
ALTER TABLE plan_sets ADD CONSTRAINT ck_plan_sets_weight
    CHECK (planned_weight_kg BETWEEN 0 AND 1000);
ALTER TABLE workout_sets ADD CONSTRAINT ck_workout_sets_weights
    CHECK (weight_kg_planned BETWEEN 0 AND 1000 AND weight_kg_used BETWEEN 0 AND 1000);
ALTER TABLE workout_sets ADD CONSTRAINT ck_workout_sets_actual_reps
    CHECK (reps_actual BETWEEN 0 AND 1000);
ALTER TABLE workout_sets ADD CONSTRAINT ck_workout_sets_results_completed
    CHECK (completed_at IS NOT NULL OR (weight_kg_used IS NULL AND reps_actual IS NULL));
