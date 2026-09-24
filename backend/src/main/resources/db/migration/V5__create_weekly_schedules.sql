-- GymPlanner - calendar module.
-- The USER only chooses weekdays; the session of each day is computed by the rotation
-- (there is deliberately no plan_session_id here).

CREATE TABLE weekly_schedules (
    id                 uuid    PRIMARY KEY,
    plan_assignment_id uuid    NOT NULL REFERENCES plan_assignments (id) ON DELETE CASCADE,
    weekday            integer NOT NULL,
    CONSTRAINT ck_weekly_schedules_weekday CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT uq_weekly_schedules_day UNIQUE (plan_assignment_id, weekday)
);
