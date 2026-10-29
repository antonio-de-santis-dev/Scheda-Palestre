package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.PlanFactory.BuiltPlan;
import com.gymplanner.support.TestFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 4: history organised by plan, without mixing workouts of different plans. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class HistoryByPlanIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    AuthenticatedUser admin;
    AuthenticatedUser user;
    BuiltPlan first;
    BuiltPlan second;
    UUID firstAssignment;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        first = factory.executablePlan(admin, "Forza " + UUID.randomUUID(), 1, 1, 1);
        second = factory.executablePlan(admin, "Cardio " + UUID.randomUUID(), 1, 1, 1);
        firstAssignment = assign(first, "[1]");
        assign(second, "[2]");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private UUID assign(BuiltPlan plan, String days) {
        UUID id = UUID.fromString(api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id"));
        api.put(user, "/api/me/assignments/" + id + "/schedule", "{\"weekdays\":" + days + "}").expect(200);
        return id;
    }

    private void train(LocalDate date) {
        clock.setDate(date);
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + date + "\"}").expect(201);
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId") + "/complete",
                "{\"weightKgUsed\":22.5,\"repsActual\":10}").expect(200);
    }

    @Test
    void plansWithWorkoutsAreListedWithTheirSnapshotNameEvenWhenClosed() {
        String originalName = jdbc.queryForObject("select name from workout_plans where id = ?", String.class, first.planId());
        train(MONDAY);
        train(MONDAY.plusDays(1));
        api.post(admin, "/api/admin/assignments/" + firstAssignment + "/close", null).expect(200);
        jdbc.update("update workout_plans set name = 'Rinominata' where id = ?", first.planId());

        List<Map<String, Object>> plans = api.get(user, "/api/me/workout-plans").expect(200).read("$");
        assertThat(plans).extracting(p -> p.get("planId")).containsExactly(second.planId().toString(), first.planId().toString());
        Map<String, Object> closed = plans.get(1);
        assertThat(closed.get("planName")).isEqualTo(originalName);
        assertThat(closed.get("active")).isEqualTo(false);
        assertThat(((Number) closed.get("completedWorkouts")).intValue()).isOne();
        assertThat(plans.getFirst().get("active")).isEqualTo(true);

        // Each plan shows only its own workouts, totals and charts.
        Api.Response list = api.get(user, "/api/me/workouts?plan=" + first.planId()).expect(200);
        assertThat((Integer) list.read("$.totalElements")).isOne();
        assertThat((String) list.read("$.content[0].planName")).isEqualTo(originalName);
        assertThat(((Number) api.get(user, "/api/me/workout-stats?plan=" + second.planId()).expect(200).read("$.totalWorkouts")).intValue())
                .isOne();
        assertThat((List<Object>) api.get(user, "/api/me/workout-progress?plan=" + first.planId()).expect(200).read("$")).hasSize(1);
        assertThat((Integer) api.get(user, "/api/me/workouts").expect(200).read("$.totalElements")).isEqualTo(2);
    }

    @Test
    void theTextFilterAlsoMatchesExerciseNamesAndOtherUsersSeeNothing() {
        train(MONDAY);
        String exercise = jdbc.queryForObject("""
                select e.exercise_name_snapshot from workout_exercises e join workouts w on w.id = e.workout_id
                where w.user_id = ?""", String.class, user.id());
        assertThat((Integer) api.get(user, "/api/me/workouts?q=" + exercise.substring(0, 8)).expect(200).read("$.totalElements")).isOne();
        assertThat((List<Object>) api.get(user, "/api/me/workout-plans?q=" + exercise.substring(0, 8)).expect(200).read("$")).hasSize(1);
        assertThat((Integer) api.get(user, "/api/me/workouts?q=inesistente").expect(200).read("$.totalElements")).isZero();

        AuthenticatedUser other = fixtures.createUser();
        assertThat((List<Object>) api.get(other, "/api/me/workout-plans").expect(200).read("$")).isEmpty();
        assertThat((Integer) api.get(other, "/api/me/workouts?plan=" + first.planId()).expect(200).read("$.totalElements")).isZero();
        api.get(user, "/api/me/workouts?plan=non-uuid").expect(400);
        api.get(admin, "/api/me/workout-plans").expect(403);
        api.get(null, "/api/me/workout-plans").expect(401);
    }

    @Test
    void theRecordsEndpointWasRemoved() {
        api.get(user, "/api/me/workout-records").expect(404);
    }
}
