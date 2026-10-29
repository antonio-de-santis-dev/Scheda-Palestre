package com.gymplanner.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Migrations of tasks 1-6 applied to a database that already contains production-like data. */
class TaskMigrationsTest {

    static PostgreSQLContainer postgres;
    static DriverManagerDataSource ds;
    static JdbcTemplate jdbc;

    @BeforeAll
    static void start() {
        postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
        postgres.start();
        ds = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(ds);
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @Test
    void existingExercisesKeepTheirLastSetRecoveryBetweenExercises() {
        Flyway.configure().dataSource(ds).target("22").load().migrate();
        UUID user = UUID.randomUUID(), plan = UUID.randomUUID(), session = UUID.randomUUID(), group = UUID.randomUUID();
        UUID exercise = UUID.randomUUID(), section = UUID.randomUUID();
        UUID simple = UUID.randomUUID(), custom = UUID.randomUUID(), workoutExercise = UUID.randomUUID();
        jdbc.update("""
            insert into users (id, first_name, last_name, username, email, password_hash, role, created_at, updated_at)
            values (?, 'Test', 'Migrazione', 'migration_t1', 'migration_t1@example.test', 'x', 'USER', now(), now())""", user);
        jdbc.update("insert into workout_plans (id, name, created_by, created_at, updated_at) values (?, 'Scheda', ?, now(), now())",
                plan, user);
        jdbc.update("insert into plan_sessions (id, workout_plan_id, title, position) values (?, ?, 'Giorno 1', 1)", session, plan);
        jdbc.update("insert into muscle_groups (id, name, created_at, updated_at) values (?, 'Petto', now(), now())", group);
        jdbc.update("insert into exercises (id, name, muscle_group_id, created_at, updated_at) values (?, 'Panca', ?, now(), now())",
                exercise, group);
        jdbc.update("insert into muscle_sections (id, plan_session_id, muscle_group_id, position) values (?, ?, ?, 1)",
                section, session, group);
        jdbc.update("""
            insert into plan_exercises (id, muscle_section_id, exercise_id, position, sets_count, reps, rest_seconds)
            values (?, ?, ?, 1, 3, 10, 75)""", simple, section, exercise);
        jdbc.update("""
            insert into plan_exercises (id, muscle_section_id, exercise_id, position, sets_count, reps, rest_seconds)
            values (?, ?, ?, 2, 2, 10, 90)""", custom, section, exercise);
        jdbc.update("insert into plan_sets (id, plan_exercise_id, set_index, reps, rest_seconds) values (?, ?, 1, 10, 60)",
                UUID.randomUUID(), custom);
        jdbc.update("insert into plan_sets (id, plan_exercise_id, set_index, reps, rest_seconds) values (?, ?, 2, 10, 30)",
                UUID.randomUUID(), custom);
        UUID assignment = UUID.randomUUID(), workout = UUID.randomUUID();
        jdbc.update("""
            insert into plan_assignments (id, user_id, workout_plan_id, assigned_by, start_date, active,
                rotation_anchor_date, rotation_anchor_index, created_at)
            values (?, ?, ?, ?, current_date, true, current_date, 0, now())""", assignment, user, plan, user);
        jdbc.update("""
            insert into workouts (id, user_id, plan_assignment_id, scheduled_date, started_at, status,
                plan_name_snapshot, session_title_snapshot)
            values (?, ?, ?, current_date, now(), 'IN_PROGRESS', 'Scheda', 'Giorno 1')""", workout, user, assignment);
        jdbc.update("""
            insert into workout_exercises (id, workout_id, position, status, exercise_name_snapshot,
                muscle_group_name_snapshot, sets_planned)
            values (?, ?, 1, 'IN_PROGRESS', 'Panca', 'Petto', 3)""", workoutExercise, workout);

        Flyway.configure().dataSource(ds).target("23").load().migrate();

        assertThat(jdbc.queryForObject("select rest_after_seconds from plan_exercises where id = ?", Integer.class, simple))
                .isEqualTo(75);
        assertThat(jdbc.queryForObject("select rest_after_seconds from plan_exercises where id = ?", Integer.class, custom))
                .isEqualTo(30);
        // Workouts started before the migration have no snapshot: the last set recovery applies.
        assertThat(jdbc.queryForObject("select rest_after_seconds from workout_exercises where id = ?", Integer.class,
                workoutExercise)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from workout_sets", Integer.class)).isZero();

        // V24 (Task 3): existing active assignments are tracked from yesterday, so no past day is
        // rescheduled retroactively and today's rotation of the users does not change.
        Flyway.configure().dataSource(ds).target("24").load().migrate();
        assertThat(jdbc.queryForObject("select checked_until = current_date - 1 from schedule_tracking where plan_assignment_id = ?",
                Boolean.class, assignment)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from schedule_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from workouts", Integer.class)).isOne();
    }
}
