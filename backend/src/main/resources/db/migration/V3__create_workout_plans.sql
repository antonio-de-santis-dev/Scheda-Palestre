-- GymPlanner - workoutplan module.
-- Plans are shared by many users (no user_id here) and deleted logically (deleted_at).
-- Inner children can be deleted physically: history is protected by workout snapshots.
-- Position/index uniqueness is DEFERRABLE so atomic reorders and "replace all" operations can
-- rewrite positions inside one transaction; it is checked at commit.

CREATE TABLE workout_plans (
    id                  uuid         PRIMARY KEY,
    name                varchar(100) NOT NULL,
    description         text,
    expires_on          date,
    created_by          uuid         NOT NULL REFERENCES users (id),
    copied_from_plan_id uuid         REFERENCES workout_plans (id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    deleted_at          timestamptz,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_workout_plans_name_not_blank CHECK (length(btrim(name)) > 0)
);
CREATE INDEX ix_workout_plans_deleted_at ON workout_plans (deleted_at);
CREATE INDEX ix_workout_plans_created_by ON workout_plans (created_by);
CREATE INDEX ix_workout_plans_copied_from ON workout_plans (copied_from_plan_id);

CREATE TABLE plan_sessions (
    id              uuid        PRIMARY KEY,
    workout_plan_id uuid        NOT NULL REFERENCES workout_plans (id) ON DELETE CASCADE,
    title           varchar(60) NOT NULL,
    position        integer     NOT NULL,
    CONSTRAINT ck_plan_sessions_position CHECK (position > 0),
    CONSTRAINT ck_plan_sessions_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT uq_plan_sessions_position UNIQUE (workout_plan_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE muscle_sections (
    id              uuid    PRIMARY KEY,
    plan_session_id uuid    NOT NULL REFERENCES plan_sessions (id) ON DELETE CASCADE,
    muscle_group_id uuid    NOT NULL REFERENCES muscle_groups (id),
    position        integer NOT NULL,
    CONSTRAINT ck_muscle_sections_position CHECK (position > 0),
    CONSTRAINT uq_muscle_sections_position UNIQUE (plan_session_id, position) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT uq_muscle_sections_group UNIQUE (plan_session_id, muscle_group_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_muscle_sections_muscle_group ON muscle_sections (muscle_group_id);

CREATE TABLE plan_exercises (
    id                uuid    PRIMARY KEY,
    muscle_section_id uuid    NOT NULL REFERENCES muscle_sections (id) ON DELETE CASCADE,
    exercise_id       uuid    NOT NULL REFERENCES exercises (id),
    position          integer NOT NULL,
    sets_count        integer NOT NULL,
    reps              integer NOT NULL,
    to_failure        boolean NOT NULL DEFAULT false,
    rest_seconds      integer NOT NULL,
    CONSTRAINT ck_plan_exercises_position CHECK (position > 0),
    CONSTRAINT ck_plan_exercises_sets_count CHECK (sets_count BETWEEN 1 AND 20),
    CONSTRAINT ck_plan_exercises_reps CHECK (reps BETWEEN 0 AND 100),
    CONSTRAINT ck_plan_exercises_rest CHECK (rest_seconds BETWEEN 0 AND 600),
    CONSTRAINT ck_plan_exercises_failure CHECK (
        (to_failure = true AND reps = 0) OR (to_failure = false AND reps BETWEEN 1 AND 100)),
    CONSTRAINT uq_plan_exercises_position UNIQUE (muscle_section_id, position) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX ix_plan_exercises_exercise ON plan_exercises (exercise_id);

-- Custom sets: either no rows (use plan_exercises values) or exactly sets_count rows 1..N
-- (all-or-nothing rule enforced by the service in a single transaction).
CREATE TABLE plan_sets (
    id               uuid    PRIMARY KEY,
    plan_exercise_id uuid    NOT NULL REFERENCES plan_exercises (id) ON DELETE CASCADE,
    set_index        integer NOT NULL,
    reps             integer NOT NULL,
    to_failure       boolean NOT NULL DEFAULT false,
    rest_seconds     integer NOT NULL,
    CONSTRAINT ck_plan_sets_index CHECK (set_index BETWEEN 1 AND 20),
    CONSTRAINT ck_plan_sets_reps CHECK (reps BETWEEN 0 AND 100),
    CONSTRAINT ck_plan_sets_rest CHECK (rest_seconds BETWEEN 0 AND 600),
    CONSTRAINT ck_plan_sets_failure CHECK (
        (to_failure = true AND reps = 0) OR (to_failure = false AND reps BETWEEN 1 AND 100)),
    CONSTRAINT uq_plan_sets_index UNIQUE (plan_exercise_id, set_index) DEFERRABLE INITIALLY DEFERRED
);
