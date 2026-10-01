DROP INDEX ux_exercises_name_ci;

CREATE UNIQUE INDEX ux_exercises_group_name_ci
    ON exercises (muscle_group_id, lower(name));