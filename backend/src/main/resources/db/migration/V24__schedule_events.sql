-- Task 3: skipped and rescheduled training days. Workouts are never modified by these rows.
CREATE TABLE schedule_events (
    id                 uuid        PRIMARY KEY,
    plan_assignment_id uuid        NOT NULL REFERENCES plan_assignments (id) ON DELETE CASCADE,
    -- The training day that was not used (missed, skipped or moved away).
    original_date      date        NOT NULL,
    kind               varchar(10) NOT NULL,
    -- Only for MOVED: the free day that receives the session.
    target_date        date,
    -- Snapshot of the session planned on original_date (no FK: history survives plan edits).
    session_id         uuid,
    session_title      varchar(60) NOT NULL,
    created_at         timestamptz NOT NULL,
    -- Undo of a past event: the day counts again as used (the session is not recovered).
    cancelled_at       timestamptz,
    CONSTRAINT ck_schedule_events_kind CHECK (kind IN ('MISSED', 'SKIPPED', 'MOVED')),
    CONSTRAINT ck_schedule_events_target CHECK ((kind = 'MOVED') = (target_date IS NOT NULL)),
    -- One event per day: refreshes and repeated requests can never reschedule a day twice.
    CONSTRAINT uq_schedule_events_original UNIQUE (plan_assignment_id, original_date)
);
CREATE UNIQUE INDEX ux_schedule_events_target ON schedule_events (plan_assignment_id, target_date)
    WHERE target_date IS NOT NULL AND cancelled_at IS NULL;

-- Last day already examined for missed sessions. Existing assignments start from yesterday, so
-- the rotation of the users in production does not change retroactively.
CREATE TABLE schedule_tracking (
    plan_assignment_id uuid PRIMARY KEY REFERENCES plan_assignments (id) ON DELETE CASCADE,
    checked_until      date NOT NULL
);
INSERT INTO schedule_tracking (plan_assignment_id, checked_until)
SELECT id, current_date - 1 FROM plan_assignments WHERE active;
