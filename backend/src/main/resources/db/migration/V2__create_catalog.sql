-- GymPlanner - catalog module: muscle groups and exercises.
-- Items are never physically deleted: they are deactivated (logical deletion).

CREATE TABLE muscle_groups (
    id         uuid         PRIMARY KEY,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT ck_muscle_groups_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX ux_muscle_groups_name_ci ON muscle_groups (lower(name));

CREATE TABLE exercises (
    id         uuid         PRIMARY KEY,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    CONSTRAINT ck_exercises_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE UNIQUE INDEX ux_exercises_name_ci ON exercises (lower(name));
