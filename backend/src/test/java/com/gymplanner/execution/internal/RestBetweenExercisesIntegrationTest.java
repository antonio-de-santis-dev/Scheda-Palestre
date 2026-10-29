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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 1: recovery between the last set of an exercise and the next exercise. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class RestBetweenExercisesIntegrationTest {

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
        // One session, three exercises of two sets with 60 s between sets.
        plan = factory.executablePlan(admin, "Recupero tra esercizi", 1, 3, 2);
        setRestAfter(0, 150);
        setRestAfter(1, 0);
        UUID assignment = UUID.fromString(api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id"));
        api.put(user, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private void setRestAfter(int index, int seconds) {
        String exerciseId = api.get(admin, "/api/admin/plans/" + plan.planId()).expect(200)
                .read("$.sessions[0].sections[0].exercises[" + index + "].exerciseId");
        Api.Response saved = api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().get(index), """
                {"exerciseId":"%s","setsCount":2,"reps":10,"toFailure":false,"restSeconds":60,"customSets":[],"restAfterSeconds":%d}"""
                .formatted(exerciseId, seconds)).expect(200);
        assertThat((Integer) saved.read("$.sessions[0].sections[0].exercises[" + index + "].restAfterSeconds")).isEqualTo(seconds);
    }

    private Api.Response complete(Api.Response state) {
        return api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId")
                + "/complete", null).expect(200);
    }

    private Api.Response skipRest(Api.Response state) {
        return api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/rest", """
                {"action":"SKIP","expectedVersion":%s,"expectedExecutionVersion":%s}"""
                .formatted(state.read("$.restVersion"), state.read("$.executionVersion"))).expect(200);
    }

    @Test
    void lastSetStartsTheRecoveryBeforeTheNextExerciseInsteadOfTheSetRecovery() {
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        String first = state.read("$.exercises[0].id"), second = state.read("$.exercises[1].id");
        assertThat((Integer) state.read("$.exercises[0].restAfterSeconds")).isEqualTo(150);

        state = complete(state);
        assertThat((String) state.read("$.restKind")).isEqualTo("BETWEEN_SETS");
        assertThat((Integer) state.read("$.restSeconds")).isEqualTo(60);
        clock.advance(Duration.ofSeconds(60));

        Instant now = clock.instant();
        state = complete(state);
        assertThat((String) state.read("$.exercises[0].status")).isEqualTo("COMPLETED");
        assertThat((String) state.read("$.currentExerciseId")).isEqualTo(second);
        assertThat((String) state.read("$.restKind")).isEqualTo("BETWEEN_EXERCISES");
        assertThat((Integer) state.read("$.restSeconds")).isEqualTo(150);
        assertThat(Instant.parse(state.read("$.restEndsAt"))).isEqualTo(now.plusSeconds(150));
        // A single timer: the next set is blocked until the recovery ends or is skipped.
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId")
                + "/complete", null).expectCode(422, "REST_NOT_FINISHED");
        // The timer survives a reload.
        Api.Response reloaded = api.get(user, "/api/me/workouts/" + state.read("$.workoutId")).expect(200);
        assertThat((String) reloaded.read("$.restKind")).isEqualTo("BETWEEN_EXERCISES");
        assertThat((String) reloaded.read("$.restEndsAt")).isEqualTo(state.read("$.restEndsAt"));

        state = skipRest(state);
        assertThat((Object) state.read("$.restKind")).isNull();
        assertThat((String) state.read("$.nextAction")).isEqualTo("COMPLETE_SET");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void zeroMeansNoRecoveryAndTheLastExerciseNeverStartsOne() {
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        for (int i = 0; i < 2; i++) {
            state = complete(state);
            state = skipRest(state);
        }
        // Second exercise ("Nessun recupero"): its last set goes straight to the third exercise.
        state = complete(state);
        state = skipRest(state);
        state = complete(state);
        assertThat((String) state.read("$.exercises[1].status")).isEqualTo("COMPLETED");
        assertThat((Object) state.read("$.restKind")).isNull();
        assertThat((String) state.read("$.nextAction")).isEqualTo("COMPLETE_SET");

        state = complete(state);
        state = skipRest(state);
        state = complete(state);
        assertThat((String) state.read("$.status")).isEqualTo("COMPLETED");
        assertThat((Object) state.read("$.restKind")).isNull();
        assertThat((Object) state.read("$.restEndsAt")).isNull();
    }

    @Test
    void workoutsStartedBeforeTheMigrationKeepTheLastSetRecovery() {
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        jdbc.update("update workout_exercises set rest_after_seconds = null where workout_id = ?",
                UUID.fromString(state.read("$.workoutId")));
        state = complete(state);
        state = skipRest(state);
        state = complete(state);
        assertThat((String) state.read("$.restKind")).isEqualTo("BETWEEN_EXERCISES");
        assertThat((Integer) state.read("$.restSeconds")).isEqualTo(60);
    }

    @Test
    void theAdminValueIsValidatedAndCopiedWithThePlan() {
        String exerciseId = api.get(admin, "/api/admin/plans/" + plan.planId()).expect(200)
                .read("$.sessions[0].sections[0].exercises[0].exerciseId");
        api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(), """
                {"exerciseId":"%s","setsCount":2,"reps":10,"toFailure":false,"restSeconds":60,"customSets":[],"restAfterSeconds":1201}"""
                .formatted(exerciseId)).expectCode(400, "VALIDATION_ERROR");
        Api.Response copy = api.post(admin, "/api/admin/plans/" + plan.planId() + "/duplicate", null).expect(201);
        assertThat((Integer) copy.read("$.sessions[0].sections[0].exercises[0].restAfterSeconds")).isEqualTo(150);
        assertThat((Integer) copy.read("$.sessions[0].sections[0].exercises[1].restAfterSeconds")).isZero();
    }
}
