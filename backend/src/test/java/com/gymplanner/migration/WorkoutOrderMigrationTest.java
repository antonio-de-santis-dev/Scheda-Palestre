package com.gymplanner.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Existing V2 data/history must survive the new execution-order migration. */
class WorkoutOrderMigrationTest {
    @Test
    void existingV16DatabaseKeepsHistorySetsAndRecoveryWhenV17IsApplied() {
        try (var postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))) {
            postgres.start();
            var ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(ds);
            Flyway.configure().dataSource(ds).target("16").load().migrate();
            var history = jdbc.queryForList("select version, checksum from flyway_schema_history order by installed_rank");
            assertThat(jdbc.queryForObject("select checksum from flyway_schema_history where version = '12'", Integer.class))
                    .isEqualTo(-1948114297);
            UUID user = UUID.randomUUID(), plan = UUID.randomUUID(), assignment = UUID.randomUUID();
            UUID workout = UUID.randomUUID(), exercise = UUID.randomUUID(), set = UUID.randomUUID();
            jdbc.update("""
                insert into users (id, first_name, last_name, username, email, password_hash, role, created_at, updated_at)
                values (?, 'Test', 'Migrazione', 'migration_user', 'migration@example.test', 'x', 'USER', now(), now())
                """, user);
            jdbc.update("""
                insert into workout_plans (id, name, created_by, created_at, updated_at, version)
                values (?, 'Scheda conservata', ?, now(), now(), 0)
                """, plan, user);
            jdbc.update("""
                insert into plan_assignments (id, user_id, workout_plan_id, assigned_by, start_date, active,
                    rotation_anchor_date, rotation_anchor_index, created_at)
                values (?, ?, ?, ?, current_date, true, current_date, 0, now())
                """, assignment, user, plan, user);
            jdbc.update("""
                insert into workouts (id, user_id, plan_assignment_id, scheduled_date, started_at, status,
                    plan_name_snapshot, session_title_snapshot, rest_ends_at, rest_remaining_millis, rest_duration_seconds, rest_version)
                values (?, ?, ?, current_date, now(), 'IN_PROGRESS', 'Scheda conservata', 'Giorno 1', NULL, 45123, 60, 3)
                """, workout, user, assignment);
            jdbc.update("""
                insert into workout_exercises (id, workout_id, position, status, exercise_name_snapshot,
                    muscle_group_name_snapshot, sets_planned)
                values (?, ?, 1, 'IN_PROGRESS', 'Panca', 'Petto', 2)
                """, exercise, workout);
            jdbc.update("""
                insert into workout_sets (id, workout_exercise_id, set_index, reps_planned, to_failure,
                    rest_seconds, completed_at, weight_kg_used, reps_actual)
                values (?, ?, 1, 10, false, 60, now(), 25.5, 8)
                """, set, exercise);
            var beforeWorkout = jdbc.queryForMap("select * from workouts where id = ?", workout);
            var beforeSet = jdbc.queryForMap("select * from workout_sets where id = ?", set);
            var current = Flyway.configure().dataSource(ds).target("17").load();
            assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
            current.validate();
            assertThat(jdbc.queryForList("select version, checksum from flyway_schema_history where version::integer <= 16 order by installed_rank"))
                    .isEqualTo(history);
            assertThat(jdbc.queryForMap("select * from workout_sets where id = ?", set)).isEqualTo(beforeSet);
            assertThat(jdbc.queryForMap("select * from workouts where id = ?", workout)).containsAllEntriesOf(beforeWorkout);
            assertThat(jdbc.queryForObject("select execution_version from workouts where id = ?", Long.class, workout))
                    .isZero();
            assertThat(jdbc.queryForObject("select condeferrable and condeferred from pg_constraint where conname = 'uq_workout_exercises_position'", Boolean.class))
                    .isTrue();
            // Also create a timer last updated by old fixV2 (revision 0, obsolete deadline).
            UUID legacyUser = UUID.randomUUID(), legacyAssignment = UUID.randomUUID();
            UUID legacyWorkout = UUID.randomUUID(), legacyExercise = UUID.randomUUID(), legacySet = UUID.randomUUID();
            jdbc.update("insert into users (id, first_name, last_name, username, email, password_hash, role, created_at, updated_at) values (?, 'Legacy', 'Timer', 'legacy_timer', 'legacy@example.test', 'x', 'USER', now(), now())", legacyUser);
            jdbc.update("""
                insert into plan_assignments (id, user_id, workout_plan_id, assigned_by, start_date, active, rotation_anchor_date, rotation_anchor_index, created_at)
                select ?, ?, workout_plan_id, assigned_by, start_date, active, rotation_anchor_date, rotation_anchor_index, created_at
                from plan_assignments where id = ?
                """, legacyAssignment, legacyUser, assignment);
            jdbc.update("""
                insert into workouts (id, user_id, plan_assignment_id, scheduled_date, started_at, status, plan_name_snapshot, session_title_snapshot, rest_ends_at, rest_duration_seconds, rest_version)
                values (?, ?, ?, current_date, now()-interval '2 minutes', 'IN_PROGRESS', 'Legacy', 'Legacy', now()-interval '1 day', 60, 0)
                """, legacyWorkout, legacyUser, legacyAssignment);
            jdbc.update("insert into workout_exercises (id, workout_id, position, status, exercise_name_snapshot, muscle_group_name_snapshot, sets_planned) values (?, ?, 1, 'IN_PROGRESS', 'Panca', 'Petto', 2)", legacyExercise, legacyWorkout);
            jdbc.update("insert into workout_sets (id, workout_exercise_id, set_index, reps_planned, to_failure, rest_seconds, completed_at) values (?, ?, 1, 10, false, 60, now())", legacySet, legacyExercise);
            assertThat(Flyway.configure().dataSource(ds).target("18").load().migrate().migrationsExecuted).isEqualTo(1);
            assertThat(jdbc.queryForObject("select rest_ends_at = (select completed_at + rest_seconds*interval '1 second' from workout_sets where id = ?) from workouts where id = ?",
                    Boolean.class, legacySet, legacyWorkout)).isTrue();
            assertThat(jdbc.queryForObject("select rest_version from workouts where id = ?", Long.class, legacyWorkout)).isEqualTo(1L);
            // Existing paused V2 recovery, historical migrations, and recorded results remain untouched.
            assertThat(jdbc.queryForMap("select * from workouts where id = ?", workout)).containsAllEntriesOf(beforeWorkout);
            assertThat(jdbc.queryForMap("select * from workout_sets where id = ?", set)).isEqualTo(beforeSet);
            assertThat(jdbc.queryForList("select version, checksum from flyway_schema_history where version::integer <= 16 order by installed_rank")).isEqualTo(history);
            // V19 preserves catalog/legacy identities and fills snapshots written by older fixV2.
            UUID catalog = UUID.randomUUID(), preservedLegacy = UUID.randomUUID();
            jdbc.update("update workout_exercises set catalog_exercise_id = ?, legacy_exercise_id = ? where id = ?",
                    catalog, preservedLegacy, exercise);
            UUID group = UUID.randomUUID(), session = UUID.randomUUID(), section = UUID.randomUUID(), entry = UUID.randomUUID();
            jdbc.update("insert into muscle_groups (id, name, active, created_at, updated_at) values (?, 'Gruppo identità', true, now(), now())", group);
            jdbc.update("insert into exercises (id, name, active, muscle_group_id, created_at, updated_at) values (?, 'Panca', true, ?, now(), now())", catalog, group);
            jdbc.update("insert into plan_sessions (id, workout_plan_id, title, position) values (?, ?, 'Giorno identità', 1)", session, plan);
            jdbc.update("insert into muscle_sections (id, plan_session_id, muscle_group_id, position) values (?, ?, ?, 1)", section, session, group);
            jdbc.update("insert into plan_exercises (id, muscle_section_id, exercise_id, position, sets_count, reps, to_failure, rest_seconds) values (?, ?, ?, 1, 1, 10, false, 60)", entry, section, catalog);
            UUID linkedSnapshot = UUID.randomUUID(), existingLegacySnapshot = UUID.randomUUID();
            jdbc.update("""
                insert into workout_exercises (id, workout_id, plan_exercise_id, position, status, exercise_name_snapshot, muscle_group_name_snapshot, sets_planned)
                values (?, ?, ?, 2, 'TODO', 'Panca', 'Petto', 1)
                """, linkedSnapshot, legacyWorkout, entry);
            jdbc.update("""
                insert into workout_exercises (id, workout_id, legacy_exercise_id, position, status, exercise_name_snapshot, muscle_group_name_snapshot, sets_planned)
                values (?, ?, ?, 3, 'TODO', 'Panca', 'Petto', 1)
                """, existingLegacySnapshot, legacyWorkout, preservedLegacy);
            var beforeRecovery = jdbc.queryForMap("select * from workouts where id = ?", legacyWorkout);
            var identityMigration = Flyway.configure().dataSource(ds).target("19").load();
            assertThat(identityMigration.migrate().migrationsExecuted).isEqualTo(1);
            identityMigration.validate();
            assertThat(jdbc.queryForObject("select legacy_exercise_id from workout_exercises where id = ?", UUID.class, legacyExercise)).isEqualTo(legacyExercise);
            assertThat(jdbc.queryForObject("select legacy_exercise_id from workout_exercises where id = ?", UUID.class, linkedSnapshot)).isEqualTo(entry);
            assertThat(jdbc.queryForObject("select legacy_exercise_id from workout_exercises where id = ?", UUID.class, existingLegacySnapshot)).isEqualTo(preservedLegacy);
            assertThat(jdbc.queryForObject("select catalog_exercise_id from workout_exercises where id = ?", UUID.class, linkedSnapshot)).isNull();
            assertThat(jdbc.queryForObject("select catalog_exercise_id from workout_exercises where id = ?", UUID.class, exercise)).isEqualTo(catalog);
            assertThat(jdbc.queryForObject("select legacy_exercise_id from workout_exercises where id = ?", UUID.class, exercise)).isEqualTo(preservedLegacy);
            jdbc.update("delete from plan_exercises where id = ?", entry);
            assertThat(jdbc.queryForObject("select legacy_exercise_id from workout_exercises where id = ?", UUID.class, linkedSnapshot)).isEqualTo(entry);
            assertThat(jdbc.queryForMap("select * from workouts where id = ?", legacyWorkout)).isEqualTo(beforeRecovery);
            assertThat(jdbc.queryForMap("select * from workout_sets where id = ?", set)).isEqualTo(beforeSet);
            assertThat(jdbc.queryForList("select version, checksum from flyway_schema_history where version::integer <= 16 order by installed_rank")).isEqualTo(history);


        }
    }
}
