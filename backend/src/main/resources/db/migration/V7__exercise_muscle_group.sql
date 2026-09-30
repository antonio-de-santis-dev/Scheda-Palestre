-- GymPlanner - catalog: every exercise belongs to exactly one muscle group (ADR 0007).
-- Safe data migration, nothing is deleted:
--   1. nullable column;
--   2. existing exercises get the group they are already used with in plans
--      (muscle_sections.muscle_group_id of the sections containing them). When an exercise is
--      used under several groups the most used one wins, ties broken by group name then id
--      (deterministic, documented in the ADR);
--   3. exercises never used in a plan go to a dedicated, inactive "Senza gruppo (storico)" group
--      with a fixed id, created only when needed;
--   4. NOT NULL: steps 2-3 cover 100% of the rows by construction.
-- Plans that already use an exercise under a different group stay valid (tolerated "historic"
-- mismatch, flagged in the editor). Workout snapshots are not touched.

ALTER TABLE exercises ADD COLUMN muscle_group_id uuid;

WITH usage AS (
    SELECT pe.exercise_id, ms.muscle_group_id, count(*) AS uses
    FROM plan_exercises pe
    JOIN muscle_sections ms ON ms.id = pe.muscle_section_id
    GROUP BY pe.exercise_id, ms.muscle_group_id
), ranked AS (
    SELECT u.exercise_id, u.muscle_group_id,
           row_number() OVER (PARTITION BY u.exercise_id ORDER BY u.uses DESC, lower(mg.name), mg.id) AS rn
    FROM usage u
    JOIN muscle_groups mg ON mg.id = u.muscle_group_id
)
UPDATE exercises e
SET muscle_group_id = r.muscle_group_id
FROM ranked r
WHERE r.exercise_id = e.id AND r.rn = 1;

INSERT INTO muscle_groups (id, name, active, created_at, updated_at)
SELECT '00000000-0000-4000-8000-00000000c0de', 'Senza gruppo (storico)', false, now(), now()
WHERE EXISTS (SELECT 1 FROM exercises WHERE muscle_group_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM muscle_groups WHERE lower(name) = lower('Senza gruppo (storico)'));

-- If a group with that name already existed (created by an ADMIN), it is reused.
UPDATE exercises
SET muscle_group_id = (
    SELECT id FROM muscle_groups
    WHERE id = '00000000-0000-4000-8000-00000000c0de' OR lower(name) = lower('Senza gruppo (storico)')
    ORDER BY (id = '00000000-0000-4000-8000-00000000c0de') DESC
    LIMIT 1)
WHERE muscle_group_id IS NULL;

ALTER TABLE exercises ALTER COLUMN muscle_group_id SET NOT NULL;
ALTER TABLE exercises
    ADD CONSTRAINT fk_exercises_muscle_group FOREIGN KEY (muscle_group_id) REFERENCES muscle_groups (id);
CREATE INDEX ix_exercises_muscle_group ON exercises (muscle_group_id, active, lower(name));
