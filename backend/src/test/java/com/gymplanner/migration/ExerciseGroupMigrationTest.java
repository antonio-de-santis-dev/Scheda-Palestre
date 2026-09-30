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

/**
 * V7 on a database that already holds V6 data: no row is lost, every exercise gets a group,
 * workout snapshots stay readable. Runs on its own container (independent from the app context).
 */
class ExerciseGroupMigrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
    static final String HISTORIC_GROUP = "00000000-0000-4000-8000-00000000c0de";

    static JdbcTemplate jdbc;
    static DriverManagerDataSource dataSource;

    @BeforeAll
    static void start() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    @Test
    void v7AssignsAGroupToEveryExerciseWithoutLosingData() {
        flyway("6").migrate();

        UUID admin = UUID.randomUUID();
        UUID user = UUID.randomUUID();
        insertUser(admin, "admin_mig", "ADMIN");
        insertUser(user, "user_mig", "USER");
        UUID chest = group("Petto");
        UUID back = group("Dorso");
        UUID arms = group("Braccia");
        UUID bench = exercise("Panca piana");      // used only under Petto
        UUID pullUp = exercise("Trazioni");        // twice under Dorso, once under Braccia -> Dorso
        UUID tie = exercise("Rematore");           // once under Dorso, once under Braccia -> Braccia (name order)
        UUID unused = exercise("Plank");           // never used -> historic group

        UUID plan = UUID.randomUUID();
        jdbc.update("""
                insert into workout_plans (id, name, created_by, created_at, updated_at, version)
                values (?, 'Scheda storica', ?, now(), now(), 0)""", plan, admin);
        UUID s1 = session(plan, 1);
        UUID s2 = session(plan, 2);
        UUID s3 = session(plan, 3);
        UUID chestSection = section(s1, chest);
        UUID backSection1 = section(s1, back);
        UUID backSection2 = section(s2, back);
        UUID armsSection = section(s3, arms);
        UUID benchPe = planExercise(chestSection, bench, 1);
        planExercise(backSection1, pullUp, 1);
        planExercise(backSection2, pullUp, 1);
        planExercise(armsSection, pullUp, 1);
        planExercise(backSection2, tie, 2);
        planExercise(armsSection, tie, 2);

        UUID assignment = UUID.randomUUID();
        jdbc.update("""
                insert into plan_assignments (id, user_id, workout_plan_id, assigned_by, start_date, active,
                                              rotation_anchor_date, rotation_anchor_index, created_at)
                values (?, ?, ?, ?, date '2026-09-01', true, date '2026-09-01', 0, now())""",
                assignment, user, plan, admin);
        UUID workout = UUID.randomUUID();
        jdbc.update("""
                insert into workouts (id, user_id, plan_assignment_id, plan_session_id, scheduled_date, started_at,
                                      finished_at, status, plan_name_snapshot, session_title_snapshot)
                values (?, ?, ?, ?, date '2026-09-07', now(), now(), 'COMPLETED', 'Scheda storica', 'Giorno 1')""",
                workout, user, assignment, s1);
        jdbc.update("""
                insert into workout_exercises (id, workout_id, plan_exercise_id, position, status,
                                               exercise_name_snapshot, muscle_group_name_snapshot, sets_planned)
                values (?, ?, ?, 1, 'COMPLETED', 'Panca piana', 'Petto', 3)""", UUID.randomUUID(), workout, benchPe);

        long exercisesBefore = count("exercises");
        long planExercisesBefore = count("plan_exercises");
        long workoutExercisesBefore = count("workout_exercises");

        flyway(null).migrate();

        assertThat(count("exercises")).isEqualTo(exercisesBefore);
        assertThat(count("plan_exercises")).isEqualTo(planExercisesBefore);
        assertThat(count("workout_exercises")).isEqualTo(workoutExercisesBefore);
        assertThat(jdbc.queryForObject("select count(*) from exercises where muscle_group_id is null", Long.class))
                .isZero();
        assertThat(groupOf(bench)).isEqualTo(chest);
        assertThat(groupOf(pullUp)).isEqualTo(back);
        assertThat(groupOf(tie)).isEqualTo(arms);
        assertThat(groupOf(unused)).isEqualTo(UUID.fromString(HISTORIC_GROUP));
        assertThat(jdbc.queryForObject("select active from muscle_groups where id = ?::uuid", Boolean.class,
                HISTORIC_GROUP)).isFalse();
        assertThat(jdbc.queryForObject("select is_nullable from information_schema.columns "
                + "where table_name = 'exercises' and column_name = 'muscle_group_id'", String.class)).isEqualTo("NO");
        // Snapshots are untouched and still readable.
        assertThat(jdbc.queryForObject("select exercise_name_snapshot || '/' || muscle_group_name_snapshot "
                + "from workout_exercises where workout_id = ?", String.class, workout)).isEqualTo("Panca piana/Petto");
    }

    private void insertUser(UUID id, String username, String role) {
        jdbc.update("""
                insert into users (id, first_name, last_name, username, email, password_hash, role, created_at, updated_at)
                values (?, 'Test', 'Migrazione', ?, ?, 'x', ?, now(), now())""", id, username, username + "@example.test", role);
    }

    private UUID group(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into muscle_groups (id, name, active, created_at, updated_at) values (?, ?, true, now(), now())",
                id, name);
        return id;
    }

    private UUID exercise(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into exercises (id, name, active, created_at, updated_at) values (?, ?, true, now(), now())",
                id, name);
        return id;
    }

    private UUID session(UUID plan, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into plan_sessions (id, workout_plan_id, title, position) values (?, ?, ?, ?)", id, plan,
                "Giorno " + position, position);
        return id;
    }

    private UUID section(UUID session, UUID group) {
        UUID id = UUID.randomUUID();
        Integer next = jdbc.queryForObject("select coalesce(max(position), 0) + 1 from muscle_sections where plan_session_id = ?",
                Integer.class, session);
        jdbc.update("insert into muscle_sections (id, plan_session_id, muscle_group_id, position) values (?, ?, ?, ?)",
                id, session, group, next);
        return id;
    }

    private UUID planExercise(UUID section, UUID exercise, int position) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into plan_exercises (id, muscle_section_id, exercise_id, position, sets_count, reps, to_failure, rest_seconds)
                values (?, ?, ?, ?, 3, 10, false, 60)""", id, section, exercise, position);
        return id;
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private UUID groupOf(UUID exercise) {
        return jdbc.queryForObject("select muscle_group_id from exercises where id = ?", UUID.class, exercise);
    }
}
