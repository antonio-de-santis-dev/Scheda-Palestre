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
                    plan_name_snapshot, session_title_snapshot, rest_ends_at, rest_duration_seconds, rest_version)
                values (?, ?, ?, current_date, now(), 'IN_PROGRESS', 'Scheda conservata', 'Giorno 1', now()+interval '1 minute', 60, 3)
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
            var current = Flyway.configure().dataSource(ds).load();
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
        }
    }
}
