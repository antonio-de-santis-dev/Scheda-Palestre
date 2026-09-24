-- GymPlanner - assignment module.
-- PlanAssignment is a real entity between users and workout_plans (no many-to-many):
-- it owns dates, state and the rotation anchor.

CREATE TABLE plan_assignments (
    id                    uuid        PRIMARY KEY,
    user_id               uuid        NOT NULL REFERENCES users (id),
    workout_plan_id       uuid        NOT NULL REFERENCES workout_plans (id),
    assigned_by           uuid        NOT NULL REFERENCES users (id),
    start_date            date        NOT NULL,
    end_date              date,
    active                boolean     NOT NULL DEFAULT false,
    rotation_anchor_date  date,
    rotation_anchor_index integer     NOT NULL DEFAULT 0,
    created_at            timestamptz NOT NULL,
    CONSTRAINT ck_plan_assignments_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_plan_assignments_active_open CHECK (NOT active OR end_date IS NULL),
    CONSTRAINT ck_plan_assignments_active_anchor CHECK (NOT active OR rotation_anchor_date IS NOT NULL),
    CONSTRAINT ck_plan_assignments_anchor_index CHECK (rotation_anchor_index >= 0)
);

-- At most one active assignment per USER (spec 8.10).
CREATE UNIQUE INDEX ux_plan_assignments_active_user ON plan_assignments (user_id) WHERE active;
CREATE INDEX ix_plan_assignments_user ON plan_assignments (user_id, created_at DESC);
CREATE INDEX ix_plan_assignments_plan ON plan_assignments (workout_plan_id, active);
CREATE INDEX ix_plan_assignments_assigned_by ON plan_assignments (assigned_by);
