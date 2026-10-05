package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class ExecutionIntegrationTest {

    /** Monday 5 October 2026, 10:00 Europe/Rome. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired
    Api api;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    PlanFactory factory;
    @Autowired
    MutableClock clock;
    @Autowired
    JdbcTemplate jdbc;

    AuthenticatedUser admin;
    AuthenticatedUser user;
    BuiltPlan plan;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        // 2 sessions x 2 exercises x 2 sets, 10 reps, 60 s rest.
        plan = factory.executablePlan(admin, "Esecuzione", 2, 2, 2);
        assignmentId = assignTo(user, plan);
        api.put(user, scheduleUrl(assignmentId), "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
    }

    UUID assignmentId;

    private static String scheduleUrl(UUID id) {
        return "/api/me/assignments/" + id + "/schedule";
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private UUID assignTo(AuthenticatedUser who, BuiltPlan p) {
        return UUID.fromString(api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(p.planId(), who.id(), MONDAY)).expect(201).read("$[0].id"));
    }

    private Api.Response start(AuthenticatedUser who, LocalDate date) {
        return api.post(who, "/api/me/workouts", "{\"date\":\"" + date + "\"}");
    }

    private Api.Response complete(AuthenticatedUser who, Api.Response state) {
        String workoutId = state.read("$.workoutId");
        String setId = state.read("$.currentSetId");
        return api.post(who, "/api/me/workouts/" + workoutId + "/sets/" + setId + "/complete", null);
    }

    private static String completeUrl(Api.Response state) {
        return "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId") + "/complete";
    }

    @Test
    void actualResultsPersistWithoutChangingPrescribedRepetitions() {
        Api.Response initial = start(user, MONDAY).expect(201);
        Api.Response saved = api.post(user, completeUrl(initial), "{\"weightKgUsed\":32.75,\"repsActual\":8}").expect(200);
        assertThat(((Number) saved.read("$.exercises[0].sets[0].weightKgUsed")).doubleValue()).isEqualTo(32.75);
        assertThat((Integer) saved.read("$.exercises[0].sets[0].repsActual")).isEqualTo(8);
        assertThat((Integer) saved.read("$.exercises[0].sets[0].repsPlanned")).isEqualTo(10);
        UUID setId = UUID.fromString(initial.read("$.currentSetId"));
        assertThat(jdbc.queryForObject("select weight_kg_used from workout_sets where id = ?",
                java.math.BigDecimal.class, setId)).isEqualByComparingTo("32.75");
        assertThat(jdbc.queryForObject("select reps_actual from workout_sets where id = ?", Integer.class, setId)).isEqualTo(8);
        api.post(user, "/api/me/workouts/" + initial.read("$.workoutId") + "/interrupt", null).expect(200);
        Api.Response reopened = api.get(user, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
        assertThat((Integer) reopened.read("$.exercises[0].sets[0].repsActual")).isEqualTo(8);
        assertThat(((Number) reopened.read("$.exercises[0].sets[0].weightKgUsed")).doubleValue()).isEqualTo(32.75);
        assertThat(jdbc.queryForObject("select reps from plan_exercises where id = ?", Integer.class,
                plan.planExerciseIds().getFirst())).isEqualTo(10);
    }

    @Test
    void resultRetriesAreIdempotentAndCannotOverwriteCompletedSets() {
        Api.Response initial = start(user, MONDAY).expect(201);
        String url = completeUrl(initial);
        Api.Response saved = api.post(user, url, "{\"weightKgUsed\":30,\"repsActual\":9}").expect(200);
        clock.advance(Duration.ofSeconds(1));
        Api.Response repeated = api.post(user, url, "{\"weightKgUsed\":30.00,\"repsActual\":9}").expect(200);
        assertThat((Number) repeated.read("$.executionVersion")).isEqualTo(saved.read("$.executionVersion"));
        assertThat((Number) repeated.read("$.restVersion")).isEqualTo(saved.read("$.restVersion"));
        assertThat((String) repeated.read("$.restEndsAt")).isEqualTo(saved.read("$.restEndsAt"));
        assertThat((String) repeated.read("$.exercises[0].sets[0].completedAt")).isEqualTo(saved.read("$.exercises[0].sets[0].completedAt"));
        api.post(user, url, "{\"weightKgUsed\":40,\"repsActual\":9}").expectCode(409, "SET_RESULTS_CHANGED");
        api.post(user, url, "{\"weightKgUsed\":30,\"repsActual\":10}").expectCode(409, "SET_RESULTS_CHANGED");
        api.post(user, url, "{}").expectCode(409, "SET_RESULTS_CHANGED");
        // Legacy retries with no body remain compatible and never erase stored results.
        Api.Response legacy = api.post(user, url, null).expect(200);
        assertThat((Integer) legacy.read("$.exercises[0].sets[0].repsActual")).isEqualTo(9);
    }

    @Test
    void missingResultsRemainNullAndZeroIsAnExplicitResult() {
        Api.Response initial = start(user, MONDAY).expect(201);
        Api.Response missing = complete(user, initial).expect(200);
        assertThat((Object) missing.read("$.exercises[0].sets[0].weightKgUsed")).isNull();
        assertThat((Object) missing.read("$.exercises[0].sets[0].repsActual")).isNull();
        clock.advance(Duration.ofSeconds(60));
        Api.Response zero = api.post(user, completeUrl(missing), "{\"weightKgUsed\":0,\"repsActual\":0}").expect(200);
        assertThat(((Number) zero.read("$.exercises[0].sets[1].weightKgUsed")).doubleValue()).isZero();
        assertThat((Integer) zero.read("$.exercises[0].sets[1].repsActual")).isZero();
        clock.advance(Duration.ofSeconds(60));
        Api.Response partial = api.post(user, completeUrl(zero), "{\"repsActual\":7}").expect(200);
        assertThat((Integer) partial.read("$.exercises[1].sets[0].repsActual")).isEqualTo(7);
        assertThat((Object) partial.read("$.exercises[1].sets[0].weightKgUsed")).isNull();
    }

    @Test
    void invalidActualResultsDoNotCompleteTheSet() {
        Api.Response initial = start(user, MONDAY).expect(201);
        for (String body : List.of("{\"weightKgUsed\":-1}", "{\"weightKgUsed\":1000.01}",
                "{\"weightKgUsed\":1.234}", "{\"repsActual\":-1}", "{\"repsActual\":1001}",
                "{\"repsActual\":7.5}", "{\"repsActual\":\"abc\"}")) {
            api.post(user, completeUrl(initial), body).expect(400);
        }
        Api.Response loaded = api.get(user, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
        assertThat((String) loaded.read("$.currentSetId")).isEqualTo(initial.read("$.currentSetId"));
        assertThat((Number) loaded.read("$.executionVersion")).isEqualTo(initial.read("$.executionVersion"));
        assertThat((Object) loaded.read("$.exercises[0].sets[0].repsActual")).isNull();
    }

    @Test
    void resultsRespectOwnershipCurrentSetAndRecoveryGuards() {
        Api.Response initial = start(user, MONDAY).expect(201);
        String body = "{\"weightKgUsed\":20,\"repsActual\":5}";
        api.post(fixtures.createUser(), completeUrl(initial), body).expect(404);
        api.post(admin, completeUrl(initial), body).expect(403);
        String laterId = initial.read("$.exercises[0].sets[1].id");
        String laterUrl = "/api/me/workouts/" + initial.read("$.workoutId") + "/sets/" + laterId + "/complete";
        api.post(user, laterUrl, body).expectCode(422, "SET_NOT_CURRENT");
        complete(user, initial).expect(200);
        api.post(user, laterUrl, body).expectCode(422, "REST_NOT_FINISHED");
        assertThat(jdbc.queryForObject("select reps_actual from workout_sets where id = ?", Integer.class,
                UUID.fromString(laterId))).isNull();
        assertThat(jdbc.queryForObject("select completed_at from workout_sets where id = ?", java.sql.Timestamp.class,
                UUID.fromString(laterId))).isNull();
    }

    @Test
    void concurrentDifferentResultsCannotOverwriteTheWinningCompletion() throws Exception {
        Api.Response initial = start(user, MONDAY).expect(201);
        String url = completeUrl(initial);
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Future<Api.Response> a = pool.submit(() -> { go.await(); return api.post(user, url, "{\"repsActual\":8}"); });
            Future<Api.Response> b = pool.submit(() -> { go.await(); return api.post(user, url, "{\"repsActual\":9}"); });
            go.countDown();
            Api.Response first = a.get(), second = b.get();
            assertThat(List.of(first.status(), second.status())).containsExactlyInAnyOrder(200, 409);
            Api.Response winner = first.status() == 200 ? first : second;
            Api.Response loaded = api.get(user, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
            assertThat((Integer) loaded.read("$.exercises[0].sets[0].repsActual"))
                    .isEqualTo(winner.read("$.exercises[0].sets[0].repsActual"));
            assertThat(((Number) loaded.read("$.executionVersion")).longValue()).isEqualTo(1);
        }
    }

    private static String reorderBody(List<String> ids, Number version) {
        return "{\"exerciseIds\":[" + ids.stream().map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(",")) + "],\"expectedVersion\":" + version + "}";
    }

    private Api.Response reorder(AuthenticatedUser who, Api.Response state, List<String> ids) {
        return api.post(who, "/api/me/workouts/" + state.read("$.workoutId") + "/exercises/reorder",
                reorderBody(ids, (Number) state.read("$.executionVersion")));
    }

    @Test
    void reorderPersistsAndPreservesPartialSetsRestAndSharedPlan() {
        Api.Response initial = start(user, MONDAY).expect(201);
        Api.Response partial = complete(user, initial).expect(200);
        List<String> ids = partial.read("$.exercises[*].id");
        Api.Response changed = reorder(user, partial, List.of(ids.get(1), ids.getFirst())).expect(200);
        assertThat((List<String>) changed.read("$.exercises[*].id")).containsExactly(ids.get(1), ids.getFirst());
        assertThat((List<Integer>) changed.read("$.exercises[*].position")).containsExactly(1, 2);
        assertThat((List<String>) changed.read("$.exercises[*].status")).containsExactly("IN_PROGRESS", "TODO");
        assertThat((String) changed.read("$.currentExerciseId")).isEqualTo(ids.get(1));
        assertThat((Integer) changed.read("$.exercises[1].setsCompleted")).isEqualTo(1);
        assertThat((String) changed.read("$.exercises[1].sets[0].completedAt"))
                .isEqualTo(partial.read("$.exercises[0].sets[0].completedAt"));
        assertThat((String) changed.read("$.restEndsAt")).isEqualTo(partial.read("$.restEndsAt"));
        complete(user, changed).expectCode(422, "REST_NOT_FINISHED");
        Api.Response loaded = api.get(user, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
        assertThat((List<String>) loaded.read("$.exercises[*].id")).containsExactly(ids.get(1), ids.getFirst());
        // Resume the partially executed exercise at its next set, even after a second reorder.
        changed = reorder(user, loaded, ids).expect(200);
        assertThat((String) changed.read("$.currentSetId")).isEqualTo(partial.read("$.currentSetId"));
        assertThat(jdbc.queryForList("select id from plan_exercises where muscle_section_id = ? order by position",
                UUID.class, plan.sectionIds().getFirst())).containsExactlyElementsOf(plan.planExerciseIds().subList(0, 2));
    }

    @Test
    void reorderRetryIsIdempotentAndStaleOrderCannotOverwriteLaterActions() {
        Api.Response initial = start(user, MONDAY).expect(201);
        List<String> ids = initial.read("$.exercises[*].id");
        List<String> reversed = List.of(ids.get(1), ids.getFirst());
        Api.Response changed = reorder(user, initial, reversed).expect(200);
        Api.Response retry = reorder(user, initial, reversed).expect(200);
        assertThat((Number) retry.read("$.executionVersion")).isEqualTo(changed.read("$.executionVersion"));
        reorder(user, initial, ids).expectCode(409, "WORKOUT_STATE_CHANGED");
        complete(user, changed).expect(200);
        reorder(user, initial, reversed).expectCode(409, "WORKOUT_STATE_CHANGED");
    }

    @Test
    void reorderedExerciseIsExecutedFirstAndCompletedOnesCannotBeReopened() {
        Api.Response initial = start(user, MONDAY).expect(201);
        List<String> ids = initial.read("$.exercises[*].id");
        Api.Response changed = reorder(user, initial, List.of(ids.get(1), ids.getFirst())).expect(200);
        changed = complete(user, changed).expect(200);
        clock.advance(Duration.ofSeconds(60));
        changed = complete(user, changed).expect(200);
        assertThat((String) changed.read("$.currentExerciseId")).isEqualTo(ids.getFirst());
        assertThat((String) changed.read("$.exercises[0].status")).isEqualTo("COMPLETED");
        reorder(user, changed, ids).expectCode(400, "INVALID_EXERCISE_ORDER");
        reorder(user, changed, List.of(ids.getFirst())).expect(200);
        api.post(user, "/api/me/workouts/" + changed.read("$.workoutId") + "/interrupt", null).expect(200);
        reorder(user, changed, List.of(ids.getFirst())).expectCode(422, "WORKOUT_NOT_IN_PROGRESS");
    }

    @Test
    void reorderRejectsDuplicatesMissingForeignExercisesAndOtherUsers() {
        Api.Response initial = start(user, MONDAY).expect(201);
        List<String> ids = initial.read("$.exercises[*].id");
        reorder(user, initial, List.of(ids.getFirst(), ids.getFirst())).expectCode(400, "INVALID_EXERCISE_ORDER");
        reorder(user, initial, List.of(ids.getFirst())).expectCode(400, "INVALID_EXERCISE_ORDER");
        reorder(user, initial, List.of(ids.getFirst(), UUID.randomUUID().toString())).expectCode(400, "INVALID_EXERCISE_ORDER");
        reorder(fixtures.createUser(), initial, ids).expect(404);
        reorder(admin, initial, ids).expect(403);
        api.post(user, "/api/me/workouts/" + initial.read("$.workoutId") + "/exercises/reorder",
                "{\"exerciseIds\":[],\"expectedVersion\":0}").expect(400);
        api.post(user, "/api/me/workouts/" + initial.read("$.workoutId") + "/exercises/reorder",
                "{\"exerciseIds\":[null],\"expectedVersion\":0}").expect(400);
        api.post(user, "/api/me/workouts/" + initial.read("$.workoutId") + "/exercises/reorder",
                "{\"exerciseIds\":[\"" + ids.getFirst() + "\"]}").expect(400);
    }

    @Test
    void simultaneousReordersClaimTheCurrentExerciseOnlyOnce() throws Exception {
        Api.Response initial = start(user, MONDAY).expect(201);
        List<String> ids = initial.read("$.exercises[*].id");
        String url = "/api/me/workouts/" + initial.read("$.workoutId") + "/exercises/reorder";
        String body = reorderBody(List.of(ids.get(1), ids.getFirst()), (Number) initial.read("$.executionVersion"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> task = () -> { go.await(); return api.post(user, url, body).status(); };
            Future<Integer> a = pool.submit(task), b = pool.submit(task);
            go.countDown();
            assertThat(List.of(a.get(), b.get())).containsOnly(200);
        }
        Api.Response loaded = api.get(user, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
        assertThat((Number) loaded.read("$.executionVersion")).isEqualTo(1);
        assertThat((List<String>) loaded.read("$.exercises[*].status")).containsExactly("IN_PROGRESS", "TODO");
    }

    @Test
    void differentConcurrentOrdersCannotOverwriteEachOther() throws Exception {
        AuthenticatedUser other = fixtures.createUser();
        BuiltPlan larger = factory.executablePlan(admin, "Riordino concorrente", 1, 3, 2);
        UUID assignment = assignTo(other, larger);
        api.put(other, scheduleUrl(assignment), "{\"weekdays\":[1]}").expect(200);
        Api.Response initial = start(other, MONDAY).expect(201);
        List<String> ids = initial.read("$.exercises[*].id");
        String url = "/api/me/workouts/" + initial.read("$.workoutId") + "/exercises/reorder";
        String bodyA = reorderBody(List.of(ids.get(1), ids.getFirst(), ids.get(2)), 0);
        String bodyB = reorderBody(List.of(ids.get(2), ids.getFirst(), ids.get(1)), 0);
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> a = pool.submit(() -> { go.await(); return api.post(other, url, bodyA).status(); });
            Future<Integer> b = pool.submit(() -> { go.await(); return api.post(other, url, bodyB).status(); });
            go.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        }
        Api.Response loaded = api.get(other, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
        assertThat((Number) loaded.read("$.executionVersion")).isEqualTo(1);
        assertThat((List<Integer>) loaded.read("$.exercises[*].position")).containsExactly(1, 2, 3);
    }

    private Api.Response rest(Api.Response state, String action) {
        return api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/rest", restBody(state, action));
    }

    private String restBody(Api.Response state, String action) {
        return "{\"action\":\"%s\",\"expectedVersion\":%s,\"expectedExecutionVersion\":%s}"
                .formatted(action, state.read("$.restVersion"), state.read("$.executionVersion"));
    }

    @Test
    void restPauseExtendResumeSkipPersistsAcrossReadsAndKeepsPartialSetsAndReorder() {
        Api.Response state = complete(user, start(user, MONDAY).expect(201)).expect(200);
        clock.advance(Duration.ofMillis(10_123));
        state = rest(state, "PAUSE").expect(200);
        assertThat((Boolean) state.read("$.restPaused")).isTrue();
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isEqualTo(49_877L);
        assertThat((Object) state.read("$.restEndsAt")).isNull();
        clock.advance(Duration.ofHours(1));
        String url = "/api/me/workouts/" + state.read("$.workoutId");
        state = api.get(user, url).expect(200);
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isEqualTo(49_877L);
        assertThat((String) state.read("$.nextAction")).isEqualTo("WAIT_FOR_REST");
        complete(user, state).expectCode(422, "REST_NOT_FINISHED");
        List<String> ids = state.read("$.exercises[*].id");
        state = reorder(user, state, List.of(ids.get(1), ids.getFirst())).expect(200);
        assertThat((Boolean) state.read("$.restPaused")).isTrue();
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isEqualTo(49_877L);
        state = rest(state, "EXTEND").expect(200);
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isEqualTo(79_877L);
        assertThat((Boolean) state.read("$.restPaused")).isTrue();
        Instant resumedAt = clock.instant();
        state = rest(state, "RESUME").expect(200);
        assertThat(Instant.parse(state.read("$.restEndsAt"))).isEqualTo(resumedAt.plusMillis(79_877));
        state = rest(state, "EXTEND").expect(200);
        assertThat(Instant.parse(state.read("$.restEndsAt"))).isEqualTo(resumedAt.plusMillis(109_877));
        complete(user, state).expectCode(422, "REST_NOT_FINISHED");
        state = rest(state, "SKIP").expect(200);
        state = api.get(user, url).expect(200);
        assertThat((String) state.read("$.nextAction")).isEqualTo("COMPLETE_SET");
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isZero();
        assertThat((Integer) state.read("$.exercises[1].setsCompleted")).isEqualTo(1);
        state = complete(user, state).expect(200);
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isEqualTo(60_000L);
        assertThat((Boolean) state.read("$.restPaused")).isFalse();
    }

    @Test
    void duplicateAndConcurrentRestRequestsCannotExtendTwiceOrAffectALaterState() throws Exception {
        Api.Response initial = complete(user, start(user, MONDAY).expect(201)).expect(200);
        String url = "/api/me/workouts/" + initial.read("$.workoutId") + "/rest";
        String body = restBody(initial, "EXTEND");
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> a = pool.submit(() -> { go.await(); return api.post(user, url, body).status(); });
            Future<Integer> b = pool.submit(() -> { go.await(); return api.post(user, url, body).status(); });
            go.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
        }
        api.post(user, url, body).expectCode(409, "REST_STATE_CHANGED");
        Api.Response state = api.get(user, "/api/me/workouts/" + initial.read("$.workoutId")).expect(200);
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isEqualTo(90_000L);
        List<String> ids = state.read("$.exercises[*].id");
        Api.Response changed = reorder(user, state, List.of(ids.get(1), ids.getFirst())).expect(200);
        rest(state, "PAUSE").expectCode(409, "REST_STATE_CHANGED");
        changed = rest(changed, "SKIP").expect(200);
        changed = complete(user, changed).expect(200);
        rest(state, "SKIP").expectCode(409, "REST_STATE_CHANGED");
        assertThat(((Number) changed.read("$.restRemainingMillis")).longValue()).isEqualTo(60_000L);
    }

    @Test
    void expiredInvalidForeignAndFinishedRecoveryActionsAreRejected() {
        Api.Response state = start(user, MONDAY).expect(201);
        rest(state, "PAUSE").expectCode(422, "REST_NOT_ACTIVE");
        state = complete(user, state).expect(200);
        String url = "/api/me/workouts/" + state.read("$.workoutId") + "/rest";
        rest(state, "RESUME").expectCode(422, "REST_NOT_PAUSED");
        api.post(fixtures.createUser(), url, restBody(state, "PAUSE")).expectCode(404, "NOT_FOUND");
        api.post(admin, url, restBody(state, "PAUSE")).expectCode(403, "FORBIDDEN");
        api.post(user, url, "{\"action\":\"PAUSE\"}").expectCode(400, "VALIDATION_ERROR");
        api.post(user, url, "{\"action\":\"PAUSE\",\"expectedVersion\":-1,\"expectedExecutionVersion\":0}").expectCode(400, "VALIDATION_ERROR");
        api.post(user, url, "{\"action\":\"UNKNOWN\",\"expectedVersion\":0,\"expectedExecutionVersion\":0}").expect(400);
        state = rest(state, "PAUSE").expect(200);
        rest(state, "PAUSE").expectCode(422, "REST_ALREADY_PAUSED");
        state = rest(state, "RESUME").expect(200);
        clock.advance(Duration.ofSeconds(60));
        rest(state, "EXTEND").expectCode(422, "REST_NOT_ACTIVE");
        state = api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        assertThat(((Number) state.read("$.restRemainingMillis")).longValue()).isZero();
        assertThat((Boolean) state.read("$.restPaused")).isFalse();
        rest(state, "PAUSE").expectCode(422, "WORKOUT_NOT_IN_PROGRESS");
    }

    @Test
    void fullWorkoutWithRestTimerUntilCompletion() {
        Api.Response state = start(user, MONDAY).expect(201);
        assertThat((String) state.read("$.status")).isEqualTo("IN_PROGRESS");
        assertThat((String) state.read("$.sessionTitle")).isEqualTo("Giorno 1");
        assertThat((String) state.read("$.planName")).isEqualTo("Esecuzione");
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("IN_PROGRESS", "TODO");
        assertThat((List<Object>) state.read("$.exercises[0].sets")).hasSize(2);
        assertThat((String) state.read("$.nextAction")).isEqualTo("COMPLETE_SET");
        assertThat((Object) state.read("$.restEndsAt")).isNull();
        assertThat((String) state.read("$.serverTime")).isNotNull();

        assertThat((Object) state.read("$.durationSeconds")).isNull();

        // Set 1 -> rest timer of 60 s starts from the server instant.
        Instant now = clock.instant();
        state = complete(user, state).expect(200);
        assertThat(Instant.parse(state.read("$.restEndsAt"))).isEqualTo(now.plusSeconds(60).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
        assertThat((String) state.read("$.nextAction")).isEqualTo("WAIT_FOR_REST");
        assertThat((Integer) state.read("$.exercises[0].setsCompleted")).isEqualTo(1);

        // Fase F (supersedes O-06): the next set is refused while the rest is running...
        clock.advance(Duration.ofSeconds(20));
        Api.Response early = complete(user, state).expectCode(422, "REST_NOT_FINISHED");
        assertThat((String) early.read("$.restEndsAt")).isEqualTo(state.read("$.restEndsAt"));
        // ...but repeating the already completed set is still idempotent (200, same state).
        String firstSet = (String) state.read("$.exercises[0].sets[0].id");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + firstSet + "/complete", null)
                .expect(200);
        // At the exact end of the rest the next set is accepted.
        clock.advance(Duration.ofSeconds(40));
        state = complete(user, state).expect(200);
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("COMPLETED", "IN_PROGRESS");
        // O-03: rest also runs between exercises.
        assertThat((String) state.read("$.nextAction")).isEqualTo("WAIT_FOR_REST");

        clock.advance(Duration.ofSeconds(90));
        state = complete(user, state).expect(200);
        clock.advance(Duration.ofSeconds(60));
        state = complete(user, state).expect(200);
        assertThat((String) state.read("$.status")).isEqualTo("COMPLETED");
        assertThat((String) state.read("$.nextAction")).isEqualTo("FINISHED");
        assertThat((Object) state.read("$.restEndsAt")).isNull();
        assertThat((String) state.read("$.finishedAt")).isNotNull();
        assertThat(((Number) state.read("$.durationSeconds")).longValue()).isEqualTo(210L);
        String workoutId = state.read("$.workoutId");
        String finishedAt = state.read("$.finishedAt");
        String lastSetId = state.read("$.exercises[1].sets[1].id");
        clock.advance(Duration.ofDays(2));
        Api.Response retried = api.post(user, "/api/me/workouts/" + workoutId + "/sets/" + lastSetId + "/complete", null).expect(200);
        assertThat((String) retried.read("$.finishedAt")).isEqualTo(finishedAt);
        assertThat(((Number) retried.read("$.durationSeconds")).longValue()).isEqualTo(210L);
        assertThat(((Number) api.get(user, "/api/me/workouts/" + workoutId).expect(200).read("$.durationSeconds")).longValue()).isEqualTo(210L);
        assertThat(((Number) api.get(user, "/api/me/workouts").expect(200).read("$.content[0].durationSeconds")).longValue()).isEqualTo(210L);
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("COMPLETED", "COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from workout_sets ws join workout_exercises we on we.id = ws.workout_exercise_id "
                + "where we.workout_id = ? and ws.completed_at is not null", Integer.class,
                UUID.fromString(state.read("$.workoutId")))).isEqualTo(4);
    }

    @Test
    void interruptedDurationSurvivesRetryReloadAndHistoryUsingTheStoredServerTimestamps() {
        Api.Response initial = start(user, MONDAY).expect(201);
        String workoutId = initial.read("$.workoutId");
        String url = "/api/me/workouts/" + workoutId;
        clock.advance(Duration.ofMillis(3_723_456));
        Api.Response interrupted = api.post(user, url + "/interrupt", null).expect(200);
        assertThat(((Number) interrupted.read("$.durationSeconds")).longValue()).isEqualTo(3723L);
        Instant started = Instant.parse(interrupted.read("$.startedAt"));
        Instant finished = Instant.parse(interrupted.read("$.finishedAt"));
        assertThat(Duration.between(started, finished).getSeconds()).isEqualTo(3723L);
        assertThat(jdbc.queryForObject("select finished_at from workouts where id = ?", java.sql.Timestamp.class,
                UUID.fromString(workoutId)).toInstant()).isEqualTo(finished);
        clock.advance(Duration.ofDays(1));
        Api.Response retry = api.post(user, url + "/interrupt", null).expect(200);
        assertThat((String) retry.read("$.finishedAt")).isEqualTo(interrupted.read("$.finishedAt"));
        assertThat(((Number) retry.read("$.durationSeconds")).longValue()).isEqualTo(3723L);
        assertThat(((Number) api.get(user, url).expect(200).read("$.durationSeconds")).longValue()).isEqualTo(3723L);
        assertThat(((Number) api.get(user, "/api/me/workouts").expect(200).read("$.content[0].durationSeconds")).longValue()).isEqualTo(3723L);
    }

    @Test
    void skipAndInterruptStayAllowedDuringTheRest() {
        Api.Response state = start(user, MONDAY).expect(201);
        state = complete(user, state).expect(200);
        assertThat((String) state.read("$.nextAction")).isEqualTo("WAIT_FOR_REST");
        String workoutId = state.read("$.workoutId");
        state = api.post(user, "/api/me/workouts/" + workoutId + "/exercises/" + state.read("$.currentExerciseId")
                + "/skip", null).expect(200);
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("SKIPPED", "IN_PROGRESS");
        api.post(user, "/api/me/workouts/" + workoutId + "/interrupt", null).expect(200);
    }

    @Test
    void completingTheSameSetTwiceIsIdempotent() {
        Api.Response state = start(user, MONDAY).expect(201);
        String workoutId = state.read("$.workoutId");
        String setId = state.read("$.currentSetId");
        Api.Response first = api.post(user, "/api/me/workouts/" + workoutId + "/sets/" + setId + "/complete", null)
                .expect(200);
        clock.advance(Duration.ofSeconds(5));
        Api.Response second = api.post(user, "/api/me/workouts/" + workoutId + "/sets/" + setId + "/complete", null)
                .expect(200);
        assertThat((String) second.read("$.currentSetId")).isEqualTo(first.read("$.currentSetId"));
        assertThat((String) second.read("$.exercises[0].sets[0].completedAt"))
                .isEqualTo(first.read("$.exercises[0].sets[0].completedAt"));
        assertThat((Integer) second.read("$.exercises[0].setsCompleted")).isEqualTo(1);
    }

    @Test
    void concurrentCompletionOfTheSameSetAdvancesOnlyOnce() throws Exception {
        Api.Response state = start(user, MONDAY).expect(201);
        String workoutId = state.read("$.workoutId");
        // Last set of the first exercise: a double advance would also complete exercise 2.
        state = complete(user, state).expect(200);
        clock.advance(Duration.ofSeconds(60));
        String setId = state.read("$.currentSetId");
        String url = "/api/me/workouts/" + workoutId + "/sets/" + setId + "/complete";

        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            go.await();
            return api.post(user, url, null).status();
        };
        List<Future<Integer>> results = List.of(pool.submit(call), pool.submit(call), pool.submit(call), pool.submit(call));
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();

        Api.Response after = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat((List<String>) after.read("$.exercises[*].status")).containsExactly("COMPLETED", "IN_PROGRESS");
        assertThat((Integer) after.read("$.exercises[1].setsCompleted")).isZero();
    }

    @Test
    void onlyTheCurrentSetCanBeCompleted() {
        Api.Response state = start(user, MONDAY).expect(201);
        String workoutId = state.read("$.workoutId");
        String secondSet = state.read("$.exercises[0].sets[1].id");
        String otherExerciseSet = state.read("$.exercises[1].sets[0].id");
        api.post(user, "/api/me/workouts/" + workoutId + "/sets/" + secondSet + "/complete", null)
                .expectCode(422, "SET_NOT_CURRENT");
        api.post(user, "/api/me/workouts/" + workoutId + "/sets/" + otherExerciseSet + "/complete", null)
                .expectCode(422, "SET_NOT_CURRENT");
        api.post(user, "/api/me/workouts/" + workoutId + "/sets/" + UUID.randomUUID() + "/complete", null)
                .expectCode(404, "NOT_FOUND");
    }

    @Test
    void skippingKeepsCompletedSetsAndMovesOn() {
        Api.Response state = start(user, MONDAY).expect(201);
        String workoutId = state.read("$.workoutId");
        state = complete(user, state).expect(200);
        String first = state.read("$.exercises[0].id");
        String second = state.read("$.exercises[1].id");

        api.post(user, "/api/me/workouts/" + workoutId + "/exercises/" + second + "/skip", null)
                .expectCode(422, "EXERCISE_NOT_IN_PROGRESS");
        state = api.post(user, "/api/me/workouts/" + workoutId + "/exercises/" + first + "/skip", null).expect(200);
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("SKIPPED", "IN_PROGRESS");
        assertThat((Integer) state.read("$.exercises[0].setsCompleted")).isEqualTo(1);
        // Skipping again is idempotent (O-02: a skipped exercise cannot be resumed).
        api.post(user, "/api/me/workouts/" + workoutId + "/exercises/" + first + "/skip", null).expect(200);

        state = api.post(user, "/api/me/workouts/" + workoutId + "/exercises/" + second + "/skip", null).expect(200);
        assertThat((String) state.read("$.status")).isEqualTo("COMPLETED");
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("SKIPPED", "SKIPPED");
    }

    @Test
    void workoutCanBeResumedAfterReloadAndInterrupted() {
        api.get(user, "/api/me/workouts/current").expect(204);
        Api.Response state = start(user, MONDAY).expect(201);
        complete(user, state).expect(200);
        Api.Response resumed = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat((String) resumed.read("$.workoutId")).isEqualTo(state.read("$.workoutId"));
        assertThat((Integer) resumed.read("$.exercises[0].setsCompleted")).isEqualTo(1);

        Api.Response interrupted = api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null)
                .expect(200);
        assertThat((String) interrupted.read("$.status")).isEqualTo("INTERRUPTED");
        assertThat((String) interrupted.read("$.nextAction")).isEqualTo("FINISHED");
        api.get(user, "/api/me/workouts/current").expect(204);
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/"
                + interrupted.read("$.exercises[0].sets[1].id") + "/complete", null)
                .expectCode(422, "WORKOUT_NOT_IN_PROGRESS");
    }

    @Test
    void startingIsRestrictedToPlannedDays() {
        start(user, MONDAY.plusDays(5)).expectCode(422, "DATE_NOT_ALLOWED");
        start(user, MONDAY).expect(201);
        start(user, MONDAY).expectCode(409, "WORKOUT_ALREADY_EXISTS");
        // Tomorrow (time zone tolerance) is allowed only once the current one is closed.
        start(user, MONDAY.plusDays(1)).expectCode(409, "WORKOUT_ALREADY_IN_PROGRESS");

        api.put(user, scheduleUrl(assignmentId), "{\"weekdays\":[3]}").expect(200);
        start(user, MONDAY.plusDays(1)).expectCode(422, "NOT_A_TRAINING_DAY");

        AuthenticatedUser noPlan = fixtures.createUser();
        start(noPlan, MONDAY).expectCode(422, "NO_ACTIVE_ASSIGNMENT");
    }

    @Test
    void databaseAllowsOneWorkoutInProgressPerUser() {
        start(user, MONDAY).expect(201);
        UUID assignment = jdbc.queryForObject("select id from plan_assignments where user_id = ? and active",
                UUID.class, user.id());
        assertThatThrownBy(() -> jdbc.update("""
                insert into workouts (id, user_id, plan_assignment_id, scheduled_date, started_at, status,
                                      plan_name_snapshot, session_title_snapshot)
                values (?, ?, ?, ?, now(), 'IN_PROGRESS', 'x', 'y')""",
                UUID.randomUUID(), user.id(), assignment, MONDAY.plusDays(3)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void snapshotIsNotAffectedByLaterPlanChanges() {
        Api.Response state = start(user, MONDAY).expect(201);
        String workoutId = state.read("$.workoutId");
        UUID planExercise = plan.planExerciseIds().getFirst();
        String exerciseId = api.get(admin, "/api/admin/plans/" + plan.planId())
                .read("$.sessions[0].sections[0].exercises[0].exerciseId");

        // The ADMIN changes the shared plan and even deletes the session in use.
        api.put(admin, "/api/admin/plan-exercises/" + planExercise,
                PlanFactory.exerciseJson(UUID.fromString(exerciseId), 5, 3, false, 200)).expect(200);
        api.post(admin, "/api/admin/exercises/" + exerciseId + "/deactivate", null).expect(200);
        api.delete(admin, "/api/admin/sessions/" + plan.sessionIds().getFirst()).expect(200);

        Api.Response after = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat((String) after.read("$.sessionTitle")).isEqualTo("Giorno 1");
        assertThat((List<Object>) after.read("$.exercises[0].sets")).hasSize(2);
        assertThat((Integer) after.read("$.exercises[0].sets[0].repsPlanned")).isEqualTo(10);
        assertThat((Integer) after.read("$.exercises[0].sets[0].restSeconds")).isEqualTo(60);
        // Origin links were set to NULL by the database, the snapshot stays.
        assertThat(jdbc.queryForObject("select plan_session_id from workouts where id = ?", UUID.class,
                UUID.fromString(workoutId))).isNull();
        complete(user, after).expect(200);
    }

    @Test
    void usersCannotSeeOrChangeWorkoutsOfOthers() {
        Api.Response state = start(user, MONDAY).expect(201);
        String workoutId = state.read("$.workoutId");
        String setId = state.read("$.currentSetId");
        String exerciseId = state.read("$.currentExerciseId");
        AuthenticatedUser intruder = fixtures.createUser();

        api.post(intruder, "/api/me/workouts/" + workoutId + "/sets/" + setId + "/complete", null)
                .expectCode(404, "NOT_FOUND");
        api.post(intruder, "/api/me/workouts/" + workoutId + "/exercises/" + exerciseId + "/skip", null)
                .expectCode(404, "NOT_FOUND");
        api.post(intruder, "/api/me/workouts/" + workoutId + "/interrupt", null).expectCode(404, "NOT_FOUND");
        api.get(intruder, "/api/me/workouts/current").expect(204);
        api.post(admin, "/api/me/workouts/" + workoutId + "/interrupt", null).expectCode(403, "FORBIDDEN");
        // The owner's workout is untouched.
        assertThat((Integer) api.get(user, "/api/me/workouts/current").read("$.exercises[0].setsCompleted")).isZero();
    }

    @Test
    void closingTheAssignmentInterruptsTheWorkoutInProgress() {
        Api.Response state = start(user, MONDAY).expect(201);
        String assignmentId = api.get(user, "/api/me/assignments").read("$[0].id");
        api.post(admin, "/api/admin/assignments/" + assignmentId + "/close", null).expect(200);
        api.get(user, "/api/me/workouts/current").expect(204);
        assertThat(jdbc.queryForObject("select status from workouts where id = ?", String.class,
                UUID.fromString(state.read("$.workoutId")))).isEqualTo("INTERRUPTED");
    }

    @Test
    void todayViewDescribesTheDay() {
        Api.Response today = api.get(user, "/api/me/today?date=" + MONDAY).expect(200);
        assertThat((String) today.read("$.status")).isEqualTo("TRAINING_DAY");
        assertThat((String) today.read("$.session.title")).isEqualTo("Giorno 1");
        assertThat((String) today.read("$.session.sections[0].exercises[0].exerciseName")).startsWith("Esercizio 1.1");
        assertThat((Boolean) today.read("$.canStart")).isTrue();

        start(user, MONDAY).expect(201);
        today = api.get(user, "/api/me/today?date=" + MONDAY).expect(200);
        assertThat((String) today.read("$.workout.status")).isEqualTo("IN_PROGRESS");
        assertThat((Boolean) today.read("$.canStart")).isFalse();

        // O-04: the next day the unfinished workout is reported as pending.
        clock.setDate(MONDAY.plusDays(1));
        today = api.get(user, "/api/me/today?date=" + MONDAY.plusDays(1)).expect(200);
        assertThat((String) today.read("$.pendingWorkout.scheduledDate")).isEqualTo(MONDAY.toString());
        assertThat((String) today.read("$.session.title")).isEqualTo("Giorno 2");
        assertThat((Boolean) today.read("$.canStart")).isFalse();

        api.put(user, scheduleUrl(assignmentId), "{\"weekdays\":[5]}").expect(200);
        today = api.get(user, "/api/me/today?date=" + MONDAY.plusDays(1)).expect(200);
        assertThat((String) today.read("$.status")).isEqualTo("REST_DAY");
        assertThat((String) today.read("$.nextTraining.date")).isEqualTo(MONDAY.plusDays(4).toString());

        AuthenticatedUser noPlan = fixtures.createUser();
        assertThat((String) api.get(noPlan, "/api/me/today").read("$.status")).isEqualTo("NO_ACTIVE_ASSIGNMENT");
    }

    @Test
    void todayReportsMissingDays() {
        AuthenticatedUser fresh = fixtures.createUser();
        assignTo(fresh, plan);
        assertThat((String) api.get(fresh, "/api/me/today?date=" + MONDAY).read("$.status")).isEqualTo("NO_SCHEDULE");
    }

    @Test
    void todayAndCalendarResolveThePlanOfEachDayWithTwoActivePlans() {
        api.put(user, scheduleUrl(assignmentId), "{\"weekdays\":[1,3]}").expect(200);
        BuiltPlan cardio = factory.executablePlan(admin, "Cardio", 3, 1, 1);
        UUID cardioId = assignTo(user, cardio);

        // The second plan has no days yet: the user is guided to choose them.
        Api.Response today = api.get(user, "/api/me/today?date=" + MONDAY).expect(200);
        assertThat((Integer) today.read("$.activePlanCount")).isEqualTo(2);
        assertThat((List<String>) today.read("$.plansWithoutDays[*].assignmentId")).containsExactly(cardioId.toString());
        api.put(user, scheduleUrl(cardioId), "{\"weekdays\":[2,4]}").expect(200);

        today = api.get(user, "/api/me/today?date=" + MONDAY).expect(200);
        assertThat((String) today.read("$.status")).isEqualTo("TRAINING_DAY");
        assertThat((String) today.read("$.planName")).startsWith("Esecuzione");
        assertThat((String) today.read("$.assignmentId")).isEqualTo(assignmentId.toString());
        assertThat((List<Object>) today.read("$.plansWithoutDays")).isEmpty();

        today = api.get(user, "/api/me/today?date=" + MONDAY.plusDays(1)).expect(200);
        assertThat((String) today.read("$.status")).isEqualTo("TRAINING_DAY");
        assertThat((String) today.read("$.planName")).startsWith("Cardio");
        assertThat((String) today.read("$.session.title")).isEqualTo("Giorno 1");

        // Friday: no plan trains; the next training comes from the nearest plan.
        today = api.get(user, "/api/me/today?date=" + MONDAY.plusDays(4)).expect(200);
        assertThat((String) today.read("$.status")).isEqualTo("REST_DAY");
        assertThat((String) today.read("$.nextTraining.date")).isEqualTo(MONDAY.plusDays(7).toString());
        assertThat((String) today.read("$.nextTraining.planName")).startsWith("Esecuzione");

        Api.Response calendar = api.get(user, "/api/me/calendar?from=" + MONDAY + "&to=" + MONDAY.plusDays(6)).expect(200);
        assertThat((List<String>) calendar.read("$[*].type"))
                .containsExactly("TRAINING", "TRAINING", "TRAINING", "TRAINING", "REST", "REST", "REST");
        assertThat((String) calendar.read("$[1].planName")).startsWith("Cardio");
        assertThat((String) calendar.read("$[2].planName")).startsWith("Esecuzione");
        assertThat((List<String>) calendar.read("$[0:4].sessionTitle"))
                .containsExactly("Giorno 1", "Giorno 1", "Giorno 2", "Giorno 2");

        // Starting on Tuesday uses the cardio plan (snapshot of its name).
        clock.setDate(MONDAY.plusDays(1));
        Api.Response state = start(user, MONDAY.plusDays(1)).expect(201);
        assertThat((String) state.read("$.planName")).startsWith("Cardio");
    }

    @Test
    void changingDaysDuringAWorkoutKeepsTheWorkoutRunning() {
        Api.Response state = start(user, MONDAY).expect(201);
        api.put(user, scheduleUrl(assignmentId), "{\"weekdays\":[3,5]}").expect(200);
        Api.Response current = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat((String) current.read("$.workoutId")).isEqualTo(state.read("$.workoutId"));
        complete(user, current).expect(200);
        // The new days apply to future dates only.
        assertThat((String) api.get(user, "/api/me/today?date=" + MONDAY.plusDays(2)).read("$.status"))
                .isEqualTo("TRAINING_DAY");
    }

    @Test
    void calendarShowsPlannedSessionsAndOutcomes() {
        Api.Response state = start(user, MONDAY).expect(201);
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        Api.Response calendar = api.get(user, "/api/me/calendar?from=" + MONDAY + "&to=" + MONDAY.plusDays(6))
                .expect(200);
        assertThat((List<Object>) calendar.read("$")).hasSize(7);
        assertThat((List<String>) calendar.read("$[*].sessionTitle"))
                .containsExactly("Giorno 1", "Giorno 2", "Giorno 1", "Giorno 2", "Giorno 1", "Giorno 2", "Giorno 1");
        assertThat((String) calendar.read("$[0].workout.status")).isEqualTo("INTERRUPTED");
        assertThat((Object) calendar.read("$[1].workout")).isNull();

        api.get(user, "/api/me/calendar?from=" + MONDAY + "&to=" + MONDAY.plusDays(62)).expectCode(400, "RANGE_TOO_LARGE");
        api.get(user, "/api/me/calendar?from=" + MONDAY + "&to=" + MONDAY.minusDays(1)).expectCode(400, "VALIDATION_ERROR");
        api.get(user, "/api/me/calendar?from=nope&to=" + MONDAY).expect(400);
    }
}
