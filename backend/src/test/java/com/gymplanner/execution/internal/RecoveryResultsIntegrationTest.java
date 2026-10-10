package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.TestFixtures;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class RecoveryResultsIntegrationTest {
    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    AuthenticatedUser user;
    AuthenticatedUser admin;
    String id;
    String setId;
    Api.Response initial;
    static final LocalDate DAY = LocalDate.of(2026, 10, 5);

    @BeforeEach
    void setup() {
        clock.setDate(DAY); admin = fixtures.createAdmin(); user = fixtures.createUser();
        var plan = factory.executablePlan(admin, "Risultati recupero", 1, 1, 2);
        String assignment = api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}
                """.formatted(plan.planId(), user.id(), DAY)).expect(201).read("$[0].id");
        api.put(user, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1]}").expect(200);
        initial = api.post(user, "/api/me/workouts", "{\"date\":\"" + DAY + "\"}").expect(201);
        id = initial.read("$.workoutId"); setId = initial.read("$.currentSetId");
    }
    @AfterEach void cleanup() { clock.reset(); }
    Api.Response complete(String series) { return api.post(user, "/api/me/workouts/" + id + "/sets/" + series + "/complete", null).expect(200); }
    String payload(Api.Response state, String values) {
        return "{\"results\":%s,\"expectedExecutionVersion\":%s,\"expectedRestVersion\":%s}"
                .formatted(values, state.read("$.executionVersion"), state.read("$.restVersion"));
    }
    Api.Response save(AuthenticatedUser actor, String series, String body) {
        return api.post(actor, "/api/me/workouts/" + id + "/sets/" + series + "/results", body);
    }
    @Test
    void savesCompletedRecoverySeriesWithoutAdvancingOrChangingTimerAndRetriesSafely() {
        Api.Response state = complete(setId);
        assertThat((String) state.read("$.resultEntrySetId")).isEqualTo(setId);
        String next = state.read("$.currentSetId"), end = state.read("$.restEndsAt");
        String body = payload(state, "{\"weightKgUsed\":32.75,\"repsActual\":8}");
        clock.advance(Duration.ofSeconds(10));
        Api.Response saved = save(user, setId, body).expect(200);
        assertThat((String) saved.read("$.currentSetId")).isEqualTo(next);
        assertThat((String) saved.read("$.restEndsAt")).isEqualTo(end);
        assertThat(saved.<Object>read("$.restVersion")).isEqualTo(state.read("$.restVersion"));
        assertThat((String) saved.read("$.exercises[0].sets[0].completedAt")).isEqualTo(state.read("$.exercises[0].sets[0].completedAt"));
        assertThat(((Number) saved.read("$.volume.recordedKgReps")).doubleValue()).isEqualTo(262);
        assertThat(save(user, setId, body).expect(200).<Object>read("$.executionVersion")).isEqualTo(saved.read("$.executionVersion"));
        save(user, setId, payload(state, "{\"weightKgUsed\":50}")).expectCode(409, "SET_RESULTS_CHANGED");
        save(user, next, payload(saved, "{\"weightKgUsed\":50}")).expectCode(409, "SET_RESULTS_WINDOW_CLOSED");
        Api.Response refreshed = api.get(user, "/api/me/workouts/" + id).expect(200);
        assertThat(((Number) refreshed.read("$.exercises[0].sets[0].weightKgUsed")).doubleValue()).isEqualTo(32.75);
        clock.advance(Duration.ofSeconds(51));
        assertThat(api.get(user, "/api/me/workouts/" + id).expect(200).<Object>read("$.resultEntrySetId")).isNull();
        save(user, setId, payload(saved, "{\"weightKgUsed\":70}")).expectCode(409, "SET_RESULTS_WINDOW_CLOSED");
        save(user, setId, body).expect(200); // transport retry after expiry is a read-only success
    }
    @Test
    void pauseKeepsResultWindowOpenButSkippingRecoveryClosesIt() {
        Api.Response state = complete(setId);
        String request = "{\"action\":\"PAUSE\",\"expectedVersion\":%s,\"expectedExecutionVersion\":%s}"
                .formatted(state.read("$.restVersion"), state.read("$.executionVersion"));
        state = api.post(user, "/api/me/workouts/" + id + "/rest", request).expect(200);
        clock.advance(Duration.ofHours(1));
        state = save(user, setId, payload(state, "{\"weightKgUsed\":0,\"repsActual\":0}")).expect(200);
        assertThat(((Number) state.read("$.volume.recordedKgReps")).doubleValue()).isZero();
        request = "{\"action\":\"SKIP\",\"expectedVersion\":%s,\"expectedExecutionVersion\":%s}"
                .formatted(state.read("$.restVersion"), state.read("$.executionVersion"));
        state = api.post(user, "/api/me/workouts/" + id + "/rest", request).expect(200);
        assertThat(state.<Object>read("$.resultEntrySetId")).isNull();
        save(user, setId, payload(state, "{\"weightKgUsed\":1}")).expectCode(409, "SET_RESULTS_WINDOW_CLOSED");
    }
    @Test
    void finalRecoveryRestoresAfterRefreshAndSavingDoesNotExtendWorkoutDuration() {
        Api.Response state = complete(setId); String finalSet = state.read("$.currentSetId");
        clock.advance(Duration.ofSeconds(61)); state = complete(finalSet);
        assertThat((String) state.read("$.status")).isEqualTo("COMPLETED");
        assertThat((String) state.read("$.resultEntrySetId")).isEqualTo(finalSet);
        assertThat(state.<Object>read("$.finalResultEndsAt")).isNotNull();
        String finished = state.read("$.finishedAt"); Object duration = state.read("$.durationSeconds");
        clock.advance(Duration.ofSeconds(10));
        state = save(user, finalSet, payload(state, "{\"repsActual\":12}")).expect(200);
        assertThat(state.<Object>read("$.durationSeconds")).isEqualTo(duration);
        assertThat((String) state.read("$.finishedAt")).isEqualTo(finished);
        Api.Response refreshed = api.get(user, "/api/me/workouts/" + id).expect(200);
        assertThat((String) refreshed.read("$.resultEntrySetId")).isEqualTo(finalSet);
        assertThat((Integer) refreshed.read("$.exercises[0].sets[1].repsActual")).isEqualTo(12);
        assertThat(refreshed.<Object>read("$.exercises[0].sets[1].weightKgUsed")).isNull();
        clock.advance(Duration.ofSeconds(51));
        save(user, finalSet, payload(state, "{\"repsActual\":15}")).expectCode(409, "SET_RESULTS_WINDOW_CLOSED");
    }
    @Test
    void interruptionValidationAndOwnershipPreventWrongResults() {
        save(user, setId, payload(initial, "{\"repsActual\":5}")).expectCode(409, "SET_RESULTS_WINDOW_CLOSED");
        Api.Response state = complete(setId);
        save(fixtures.createUser(), setId, payload(state, "{\"repsActual\":5}")).expect(404);
        save(admin, setId, payload(state, "{\"repsActual\":5}")).expect(403);
        save(null, setId, payload(state, "{\"repsActual\":5}")).expect(401);
        save(user, UUID.randomUUID().toString(), payload(state, "{\"repsActual\":5}")).expect(404);
        save(user, setId, payload(state, "{\"repsActual\":7.5}")).expect(400);
        save(user, setId, payload(state, "{\"weightKgUsed\":1001}")).expect(400);
        save(user, setId, "{\"results\":null,\"expectedExecutionVersion\":0,\"expectedRestVersion\":0}").expect(400);
        state = api.post(user, "/api/me/workouts/" + id + "/interrupt", null).expect(200);
        save(user, setId, payload(state, "{\"repsActual\":5}")).expectCode(409, "SET_RESULTS_WINDOW_CLOSED");
    }
}
