package com.gymplanner.migration;

import static org.assertj.core.api.Assertions.assertThat;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

class WorkoutRecoveryMigrationTest {
    @Test
    void v12PreservesActiveRecoveryAndOldSnapshots() {
        try (var postgres = new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).target("11").load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID user = UUID.randomUUID();
            UUID plan = UUID.randomUUID();
            UUID assignment = UUID.randomUUID();
            UUID workout = UUID.randomUUID();
            UUID exercise = UUID.randomUUID();
            Instant start = Instant.parse("2026-10-05T08:00:00Z");
            jdbc.update("""
                INSERT INTO users (id, first_name, last_name, username, email, password_hash, role,
                    active, must_change_password, created_at, updated_at)
                VALUES (?, 'Mario', 'Rossi', 'migration-v2', 'migration-v2@example.test', 'hash', 'USER',
                    true, false, now(), now())
                """, user);
            jdbc.update("""
                INSERT INTO workout_plans (id, name, created_by, created_at, updated_at, version)
                VALUES (?, 'Scheda', ?, now(), now(), 0)
                """, plan, user);
            jdbc.update("""
                INSERT INTO plan_assignments (id, user_id, workout_plan_id, assigned_by, start_date, active,
                    rotation_anchor_date, rotation_anchor_index, created_at)
                VALUES (?, ?, ?, ?, date '2026-10-05', true, date '2026-10-05', 0, now())
                """, assignment, user, plan, user);
            jdbc.update("""
                INSERT INTO workouts (id, user_id, plan_assignment_id, scheduled_date, started_at,
                    status, plan_name_snapshot, session_title_snapshot)
                VALUES (?, ?, ?, date '2026-10-05', ?, 'IN_PROGRESS', 'Scheda storica', 'A')
                """, workout, user, assignment, Timestamp.from(start));
            jdbc.update("""
                INSERT INTO workout_exercises (id, workout_id, position, status, exercise_name_snapshot,
                    muscle_group_name_snapshot, sets_planned)
                VALUES (?, ?, 1, 'IN_PROGRESS', 'Panca storica', 'Petto', 3)
                """, exercise, workout);
            for (int i = 1; i <= 3; i++) {
                jdbc.update("""
                    INSERT INTO workout_sets (id, workout_exercise_id, set_index, reps_planned, to_failure,
                        rest_seconds, completed_at) VALUES (?, ?, ?, 10, false, 60, ?)
                    """, UUID.randomUUID(), exercise, i, i < 3 ? Timestamp.from(start.plusSeconds(i * 30L)) : null);
            }
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("select rest_ends_at from workouts where id = ?", Timestamp.class, workout).toInstant())
                    .isEqualTo(start.plusSeconds(120));
            assertThat(jdbc.queryForObject("select rest_duration_seconds from workouts where id = ?", Integer.class, workout)).isEqualTo(60);
            assertThat(jdbc.queryForObject("select rest_version from workouts where id = ?", Long.class, workout)).isZero();
            assertThat(jdbc.queryForObject("select exercise_name_snapshot from workout_exercises where id = ?", String.class, exercise)).isEqualTo("Panca storica");
            assertThat(jdbc.queryForObject("select count(*) from workout_sets", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from workout_sets where weight_kg_planned is null and weight_kg_used is null and reps_actual is null", Long.class)).isEqualTo(3);
        }
    }
}
