-- GymPlanner - execution module.
-- A workout is an immutable snapshot of the planned session taken when it starts: later edits
-- of the (shared) plan never change it. Links back to the configuration use ON DELETE SET NULL
-- because inner plan children can be deleted physically.

CREATE TABLE workouts (
    id                     uuid         PRIMARY KEY,
    user_id                uuid         NOT NULL REFERENCES users (id),
    plan_assignment_id     uuid         NOT NULL REFERENCES plan_assignments (id),
    plan_session_id        uuid         REFERENCES plan_sessions (id) ON DELETE SET NULL,
    scheduled_date         date         NOT NULL,
    started_at             timestamptz  NOT NULL,
    finished_at            timestamptz,
    status                 varchar(20)  NOT NULL,
    plan_name_snapshot     varchar(100) NOT NULL,
    session_title_snapshot varchar(60)  NOT NULL,
    CONSTRAINT ck_workouts_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'INTERRUPTED')),
    CONSTRAINT ck_workouts_finished CHECK (
        (status = 'IN_PROGRESS' AND finished_at IS NULL)
        OR (status IN ('COMPLETED', 'INTERRUPTED') AND finished_at IS NOT NULL)),
    CONSTRAINT uq_workouts_assignment_date UNIQUE (plan_assignment_id, scheduled_date)
);
-- At most one workout in progress per USER.
CREATE UNIQUE INDEX ux_workouts_user_in_progress ON workouts (user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_workouts_user_date ON workouts (user_id, scheduled_date DESC, started_at DESC);
CREATE INDEX ix_workouts_plan_session ON workouts (plan_session_id);

CREATE TABLE workout_exercises (
    id                         uuid         PRIMARY KEY,
    workout_id                 uuid         NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    plan_exercise_id           uuid         REFERENCES plan_exercises (id) ON DELETE SET NULL,
    position                   integer      NOT NULL,
    status                     varchar(20)  NOT NULL,
    exercise_name_snapshot     varchar(100) NOT NULL,
    muscle_group_name_snapshot varchar(100) NOT NULL,
    sets_planned               integer      NOT NULL,
    CONSTRAINT ck_workout_exercises_position CHECK (position > 0),
    CONSTRAINT ck_workout_exercises_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'COMPLETED', 'SKIPPED')),
    CONSTRAINT ck_workout_exercises_sets CHECK (sets_planned BETWEEN 1 AND 20),
    CONSTRAINT uq_workout_exercises_position UNIQUE (workout_id, position)
);
-- At most one exercise in progress per workout.
CREATE UNIQUE INDEX ux_workout_exercises_in_progress ON workout_exercises (workout_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_workout_exercises_plan_exercise ON workout_exercises (plan_exercise_id);

-- The completion state is completed_at (no boolean flag).
CREATE TABLE workout_sets (
    id                  uuid        PRIMARY KEY,
    workout_exercise_id uuid        NOT NULL REFERENCES workout_exercises (id) ON DELETE CASCADE,
    set_index           integer     NOT NULL,
    reps_planned        integer     NOT NULL,
    to_failure          boolean     NOT NULL,
    rest_seconds        integer     NOT NULL,
    completed_at        timestamptz,
    CONSTRAINT ck_workout_sets_index CHECK (set_index BETWEEN 1 AND 20),
    CONSTRAINT ck_workout_sets_reps CHECK (reps_planned BETWEEN 0 AND 100),
    CONSTRAINT ck_workout_sets_rest CHECK (rest_seconds BETWEEN 0 AND 600),
    CONSTRAINT uq_workout_sets_index UNIQUE (workout_exercise_id, set_index)
);
