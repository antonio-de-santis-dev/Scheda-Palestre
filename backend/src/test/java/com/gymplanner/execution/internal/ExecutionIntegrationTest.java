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
        assertThat((List<String>) state.read("$.exercises[*].status")).containsExactly("COMPLETED", "COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from workout_sets ws join workout_exercises we on we.id = ws.workout_exercise_id "
                + "where we.workout_id = ? and ws.completed_at is not null", Integer.class,
                UUID.fromString(state.read("$.workoutId")))).isEqualTo(4);
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
    private Api.Response rest(AuthenticatedUser who, Api.Response state, String action, Integer seconds) {
        Number version = state.read("$.restVersion");
        return api.post(who, "/api/me/workouts/" + state.read("$.workoutId") + "/rest",
                "{\"action\":\"" + action + "\",\"expectedVersion\":" + version
                        + (seconds == null ? "" : ",\"seconds\":" + seconds) + "}");
    }

    @Test
    void recoveryControlsPersistAndRejectStaleAndForeignRequests() {
        Api.Response state = complete(user, start(user, MONDAY).expect(201)).expect(200);
        clock.advance(Duration.ofSeconds(15));
        Api.Response paused = rest(user, state, "PAUSE", null).expect(200);
        assertThat((Boolean) paused.read("$.restPaused")).isTrue();
        clock.advance(Duration.ofMinutes(5));
        Api.Response loaded = api.get(user, "/api/me/workouts/" + state.read("$.workoutId")).expect(200);
        assertThat(((Number) loaded.read("$.restRemainingSeconds")).longValue()).isEqualTo(45);
        complete(user, loaded).expectCode(422, "REST_NOT_FINISHED");
        rest(user, state, "EXTEND", 30).expectCode(409, "REST_STATE_CHANGED");
        rest(fixtures.createUser(), loaded, "SKIP", null).expectCode(404, "NOT_FOUND");
        rest(user, loaded, "EXTEND", 0).expectCode(400, "VALIDATION_ERROR");
        rest(user, loaded, "EXTEND", 301).expectCode(400, "VALIDATION_ERROR");
        rest(user, loaded, "EXTEND", null).expectCode(400, "VALIDATION_ERROR");
        loaded = rest(user, loaded, "EXTEND", 30).expect(200);
        assertThat(((Number) loaded.read("$.restRemainingSeconds")).longValue()).isEqualTo(75);
        loaded = rest(user, loaded, "RESUME", null).expect(200);
        assertThat((Boolean) loaded.read("$.restPaused")).isFalse();
        loaded = rest(user, loaded, "SKIP", null).expect(200);
        complete(user, loaded).expect(200);
    }

    @Test
    void simultaneousExtensionsApplyOnlyOnceForTheSameRevision() throws Exception {
        Api.Response state = complete(user, start(user, MONDAY).expect(201)).expect(200);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> call = () -> { go.await(); return rest(user, state, "EXTEND", 30).status(); };
            Future<Integer> first = pool.submit(call);
            Future<Integer> second = pool.submit(call);
            go.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            Api.Response loaded = api.get(user, "/api/me/workouts/current").expect(200);
            assertThat(((Number) loaded.read("$.restRemainingSeconds")).longValue()).isEqualTo(90);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void durationIsStableInHistoryAfterInterruptIncludingPausedRecovery() {
        Api.Response state = complete(user, start(user, MONDAY).expect(201)).expect(200);
        rest(user, state, "PAUSE", null).expect(200);
        clock.advance(Duration.ofSeconds(3700));
        Api.Response ended = api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        assertThat(((Number) ended.read("$.durationSeconds")).longValue()).isEqualTo(3700);
        clock.advance(Duration.ofDays(2));
        Api.Response history = api.get(user, "/api/me/workouts").expect(200);
        assertThat(((Number) history.read("$.content[0].durationSeconds")).longValue()).isEqualTo(3700);
        assertThat(((Number) api.get(user, "/api/me/workouts/" + state.read("$.workoutId")).read("$.durationSeconds")).longValue()).isEqualTo(3700);
    }

    private Api.Response completeWithResult(AuthenticatedUser who, Api.Response state, String result) {
        return api.post(who, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/"
                + state.read("$.currentSetId") + "/complete", result);
    }

    @Test
    void plannedWeightsSurviveDuplicationAndLaterPlanChanges() {
        String exerciseId = api.get(admin, "/api/admin/plans/" + plan.planId()).read("$.sessions[0].sections[0].exercises[0].exerciseId");
        String config = """
                {"exerciseId":"%s","setsCount":2,"reps":10,"toFailure":false,"restSeconds":60,
                 "plannedWeightKg":20,"customSets":[
                    {"setIndex":1,"reps":10,"toFailure":false,"restSeconds":60,"plannedWeightKg":25.50},
                    {"setIndex":2,"reps":8,"toFailure":false,"restSeconds":60,"plannedWeightKg":17.25}]}
                """.formatted(exerciseId);
        api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(), config).expect(200);
        Api.Response copy = api.post(admin, "/api/admin/plans/" + plan.planId() + "/duplicate", null).expect(201);
        assertThat(((Number) copy.read("$.sessions[0].sections[0].exercises[0].sets[0].plannedWeightKg")).doubleValue()).isEqualTo(25.5);
        Api.Response state = start(user, MONDAY).expect(201);
        assertThat(((Number) state.read("$.exercises[0].sets[0].weightKgPlanned")).doubleValue()).isEqualTo(25.5);
        assertThat(((Number) state.read("$.exercises[0].sets[1].weightKgPlanned")).doubleValue()).isEqualTo(17.25);
        api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(), config.replace("25.50", "99")).expect(200);
        Api.Response loaded = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat(((Number) loaded.read("$.exercises[0].sets[0].weightKgPlanned")).doubleValue()).isEqualTo(25.5);
    }

    @Test
    void actualResultsArePersistedAndDuplicateCompletionDoesNotOverwriteThem() {
        String exerciseId = api.get(admin, "/api/admin/plans/" + plan.planId())
                .read("$.sessions[0].sections[0].exercises[0].exerciseId");
        api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(),
                PlanFactory.exerciseJson(UUID.fromString(exerciseId), 2, 0, true, 60)).expect(200);
        Api.Response state = start(user, MONDAY).expect(201);
        assertThat((Boolean) state.read("$.exercises[0].sets[0].toFailure")).isTrue();
        Api.Response completed = completeWithResult(user, state, "{\"weightKgUsed\":22.75,\"repsActual\":9}").expect(200);
        assertThat(((Number) completed.read("$.exercises[0].sets[0].weightKgUsed")).doubleValue()).isEqualTo(22.75);
        assertThat(((Number) completed.read("$.exercises[0].sets[0].repsActual")).intValue()).isEqualTo(9);
        // Repeating the same set cannot silently change its results, even with a different body.
        completeWithResult(user, state, "{\"weightKgUsed\":99,\"repsActual\":1}").expect(200);
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        Api.Response history = api.get(user, "/api/me/workouts/" + state.read("$.workoutId")).expect(200);
        assertThat(((Number) history.read("$.exercises[0].sets[0].weightKgUsed")).doubleValue()).isEqualTo(22.75);
        assertThat(((Number) history.read("$.exercises[0].sets[0].repsActual")).intValue()).isEqualTo(9);
    }

    @Test
    void missingResultsStayNullAndExplicitZeroIsRecorded() {
        Api.Response state = start(user, MONDAY).expect(201);
        Api.Response completed = completeWithResult(user, state, "{\"weightKgUsed\":0,\"repsActual\":0}").expect(200);
        assertThat(((Number) completed.read("$.exercises[0].sets[0].weightKgUsed")).doubleValue()).isZero();
        assertThat(((Number) completed.read("$.exercises[0].sets[0].repsActual")).intValue()).isZero();
        clock.advance(Duration.ofSeconds(60));
        completed = complete(user, completed).expect(200);
        assertThat((Object) completed.read("$.exercises[0].sets[1].weightKgUsed")).isNull();
        assertThat((Object) completed.read("$.exercises[0].sets[1].repsActual")).isNull();
    }

    @Test
    void invalidActualResultsCannotCompleteASet() {
        Api.Response state = start(user, MONDAY).expect(201);
        for (String body : List.of("{\"weightKgUsed\":-1}", "{\"weightKgUsed\":1001}",
                "{\"weightKgUsed\":10.001}", "{\"repsActual\":-1}", "{\"repsActual\":1001}", "{\"repsActual\":8.5}")) {
            completeWithResult(user, state, body).expect(400);
        }
        completeWithResult(fixtures.createUser(), state, "{\"weightKgUsed\":20,\"repsActual\":8}").expectCode(404, "NOT_FOUND");
        Api.Response unchanged = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat(((Number) unchanged.read("$.exercises[0].setsCompleted")).intValue()).isZero();
    }

    @Test
    void activeRestRejectsActualResultsWithoutPersistingThem() {
        Api.Response state = complete(user, start(user, MONDAY).expect(201)).expect(200);
        completeWithResult(user, state, "{\"weightKgUsed\":20,\"repsActual\":8}").expectCode(422, "REST_NOT_FINISHED");
        Api.Response loaded = api.get(user, "/api/me/workouts/current").expect(200);
        assertThat((Object) loaded.read("$.exercises[0].sets[1].weightKgUsed")).isNull();
        assertThat((Object) loaded.read("$.exercises[0].sets[1].repsActual")).isNull();
    }

    @Test
    void concurrentResultsAreAtomicAndFirstCompletionWins() throws Exception {
        Api.Response state = start(user, MONDAY).expect(201);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            Future<Integer> first = pool.submit(() -> { go.await(); return completeWithResult(user, state,
                    "{\"weightKgUsed\":11,\"repsActual\":3}").status(); });
            Future<Integer> second = pool.submit(() -> { go.await(); return completeWithResult(user, state,
                    "{\"weightKgUsed\":22,\"repsActual\":4}").status(); });
            go.countDown();
            assertThat(first.get()).isEqualTo(200);
            assertThat(second.get()).isEqualTo(200);
            Api.Response loaded = api.get(user, "/api/me/workouts/current").expect(200);
            int weight = ((Number) loaded.read("$.exercises[0].sets[0].weightKgUsed")).intValue();
            int reps = ((Number) loaded.read("$.exercises[0].sets[0].repsActual")).intValue();
            assertThat(List.of(weight, reps)).isIn(List.of(11, 3), List.of(22, 4));
            assertThat(((Number) loaded.read("$.exercises[0].setsCompleted")).intValue()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void invalidPlannedLoadsAreRejectedForGeneralAndCustomSets() {
        String exerciseId = api.get(admin, "/api/admin/plans/" + plan.planId())
                .read("$.sessions[0].sections[0].exercises[0].exerciseId");
        for (String weight : List.of("-1", "1001", "20.001")) {
            String body = """
                    {"exerciseId":"%s","setsCount":1,"reps":10,"toFailure":false,"restSeconds":60,
                    "plannedWeightKg":%s,"customSets":[]}
                    """.formatted(exerciseId, weight);
            api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(), body).expect(400);
            body = """
                    {"exerciseId":"%s","setsCount":1,"reps":10,"toFailure":false,"restSeconds":60,
                    "customSets":[{"setIndex":1,"reps":10,"toFailure":false,"restSeconds":60,"plannedWeightKg":%s}]}
                    """.formatted(exerciseId, weight);
            api.put(admin, "/api/admin/plan-exercises/" + plan.planExerciseIds().getFirst(), body).expect(400);
        }
    }

}
