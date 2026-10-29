package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.PlanFactory.BuiltPlan;
import com.gymplanner.support.TestFixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 5: last weight used for the same exercise and set number. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class PreviousWeightsIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    AuthenticatedUser admin;
    AuthenticatedUser user;
    BuiltPlan plan;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        plan = factory.executablePlan(admin, "Pesi " + UUID.randomUUID(), 1, 1, 3); // 3 sets, 60 s rest
        UUID assignment = UUID.fromString(api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id"));
        api.put(user, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private Api.Response start(LocalDate day) {
        clock.setDate(day);
        return api.post(user, "/api/me/workouts", "{\"date\":\"" + day + "\"}").expect(201);
    }

    /** Completes the current set with the given weight (null = not recorded). */
    private Api.Response complete(Api.Response state, String weight) {
        Api.Response next = api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId")
                + "/complete", weight == null ? null : "{\"weightKgUsed\":" + weight + "}").expect(200);
        clock.advance(Duration.ofSeconds(61));
        return next;
    }

    private Map<Integer, Double> previous(Api.Response state) {
        List<Map<String, Object>> rows = api.get(user, "/api/me/workouts/" + state.read("$.workoutId") + "/previous-weights")
                .expect(200).read("$");
        return rows.stream().collect(java.util.stream.Collectors.toMap(r -> (Integer) r.get("setIndex"),
                r -> ((Number) r.get("weightKgUsed")).doubleValue()));
    }

    @Test
    void eachSetShowsTheWeightOfTheSameSetInTheLastCompletedWorkout() {
        Api.Response first = start(MONDAY);
        assertThat(previous(first)).isEmpty(); // no history yet
        first = complete(first, "20");
        first = complete(first, "22.5");
        first = complete(first, null);
        assertThat((String) first.read("$.status")).isEqualTo("COMPLETED");

        // An interrupted workout and a skipped exercise are never a reference.
        Api.Response interrupted = start(MONDAY.plusDays(1));
        complete(interrupted, "99");
        api.post(user, "/api/me/workouts/" + interrupted.read("$.workoutId") + "/interrupt", null).expect(200);

        Api.Response current = start(MONDAY.plusDays(2));
        assertThat(previous(current)).containsExactlyInAnyOrderEntriesOf(Map.of(1, 20.0, 2, 22.5));
        // The current workout itself is ignored, even after recording a weight.
        current = complete(current, "30");
        assertThat(previous(current)).containsEntry(1, 20.0);
        List<Map<String, Object>> rows = api.get(user, "/api/me/workouts/" + current.read("$.workoutId") + "/previous-weights")
                .expect(200).read("$");
        assertThat(rows.getFirst().get("exerciseId")).isEqualTo(current.read("$.exercises[0].id"));
        assertThat(rows.getFirst().get("scheduledDate")).isEqualTo(MONDAY.toString());
    }

    @Test
    void usesTheStableIdentityAcrossRenamesAndAddedSets() {
        Api.Response first = start(MONDAY);
        for (String weight : new String[] {"40", "42.25", "45"}) first = complete(first, weight);
        UUID catalogId = jdbc.queryForObject("select exercise_id from plan_exercises where id = ?", UUID.class,
                plan.planExerciseIds().getFirst());
        jdbc.update("update exercises set name = 'Nome nuovo' where id = ?", catalogId);
        api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(), """
                {"exerciseId":"%s","setsCount":4,"reps":10,"toFailure":false,"restSeconds":60,"customSets":[],"restAfterSeconds":0}"""
                .formatted(catalogId)).expect(200);

        Api.Response second = start(MONDAY.plusDays(1));
        assertThat((Integer) second.read("$.exercises[0].setsPlanned")).isEqualTo(4);
        assertThat(previous(second)).containsExactlyInAnyOrderEntriesOf(Map.of(1, 40.0, 2, 42.25, 3, 45.0));
    }

    @Test
    void otherUsersCannotReadThePreviousWeights() {
        Api.Response state = start(MONDAY);
        api.get(fixtures.createUser(), "/api/me/workouts/" + state.read("$.workoutId") + "/previous-weights").expect(404);
        api.get(admin, "/api/me/workouts/" + state.read("$.workoutId") + "/previous-weights").expect(403);
    }
}
