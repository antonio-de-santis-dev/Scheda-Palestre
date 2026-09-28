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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** ADR 0010: the report contains only recorded data. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class ActivityReportIntegrationTest {

    /** Monday. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired
    Api api;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    PlanFactory factory;
    @Autowired
    MutableClock clock;

    AuthenticatedUser admin;
    AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private Api.Response report(AuthenticatedUser who) {
        return api.get(admin, "/api/admin/users/" + who.id() + "/activity-report");
    }

    private String assign(BuiltPlan plan, String days) {
        String id = api.post(admin, "/api/admin/assignments",
                "{\"planId\":\"%s\",\"userIds\":[\"%s\"],\"startDate\":\"%s\",\"activate\":true}"
                        .formatted(plan.planId(), user.id(), MONDAY.minusDays(14))).expect(201).read("$[0].id");
        api.put(user, "/api/me/assignments/" + id + "/schedule", "{\"weekdays\":" + days + "}").expect(200);
        return id;
    }

    /** Completes every set of the workout of {@code date}. */
    private void completeWorkout(LocalDate date) {
        clock.setDate(date);
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + date + "\"}").expect(201);
        while ("IN_PROGRESS".equals(state.read("$.status"))) {
            state = api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/"
                    + state.read("$.currentSetId") + "/complete", null).expect(200);
        }
    }

    @Test
    void userWithoutActivityHasEmptyButWellFormedReport() {
        Api.Response r = report(user).expect(200);
        assertThat((Integer) r.read("$.totals.workoutsCompleted")).isZero();
        assertThat((Object) r.read("$.totals.lastWorkoutDate")).isNull();
        assertThat((List<Object>) r.read("$.plans")).isEmpty();
        assertThat((List<Object>) r.read("$.weeks")).hasSize(ActivityReportService.WEEKS);
        assertThat((List<Integer>) r.read("$.weeks[*].workoutsCompleted")).containsOnly(0);
    }

    @Test
    void activityOnTwoPlansWithAWorkoutInProgress() {
        BuiltPlan strength = factory.executablePlan(admin, "Forza", 2, 2, 2);
        BuiltPlan cardio = factory.executablePlan(admin, "Cardio", 1, 1, 3);
        String strengthId = assign(strength, "[1]");
        String cardioId = assign(cardio, "[2]");

        completeWorkout(MONDAY.minusDays(7));          // Forza: Giorno 1, 4 sets
        completeWorkout(MONDAY.minusDays(6));          // Cardio: 3 sets
        clock.setDate(MONDAY);
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        state = api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId")
                + "/complete", null).expect(200);        // Forza in progress: 1 set done
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/exercises/"
                + state.read("$.currentExerciseId") + "/skip", null).expect(200);

        Api.Response r = report(user).expect(200);
        assertThat((Integer) r.read("$.totals.workoutsCompleted")).isEqualTo(2);
        assertThat((Integer) r.read("$.totals.workoutsInProgress")).isEqualTo(1);
        assertThat((Integer) r.read("$.totals.setsCompleted")).isEqualTo(4 + 3 + 1);
        assertThat((Integer) r.read("$.totals.exercisesSkipped")).isEqualTo(1);
        assertThat((String) r.read("$.totals.lastWorkoutDate")).isEqualTo(MONDAY.toString());

        List<String> forza = r.read("$.plans[?(@.assignmentId == '" + strengthId + "')].planName");
        assertThat(forza).singleElement().asString().startsWith("Forza");
        assertThat((List<Integer>) r.read("$.plans[?(@.assignmentId == '" + strengthId + "')].workoutsCompleted"))
                .containsExactly(1);
        assertThat((List<Integer>) r.read("$.plans[?(@.assignmentId == '" + strengthId + "')].workoutsInProgress"))
                .containsExactly(1);
        assertThat((List<Integer>) r.read("$.plans[?(@.assignmentId == '" + strengthId + "')].sessionsInPlan"))
                .containsExactly(2);
        assertThat((List<Integer>) r.read("$.plans[?(@.assignmentId == '" + strengthId + "')].distinctSessionsCompleted"))
                .containsExactly(1);
        assertThat((List<List<Integer>>) r.read("$.plans[?(@.assignmentId == '" + cardioId + "')].weekdays"))
                .containsExactly(List.of(2));
        assertThat((List<Integer>) r.read("$.plans[?(@.assignmentId == '" + cardioId + "')].setsCompleted"))
                .containsExactly(3);

        // Weekly trend: the last week holds the in-progress workout's set, the previous one the rest.
        List<Integer> sets = r.read("$.weeks[*].setsCompleted");
        assertThat(sets.getLast()).isEqualTo(1);
        assertThat(sets.get(sets.size() - 2)).isEqualTo(7);
        assertThat((String) r.read("$.weeks[-1].weekStart")).isEqualTo(MONDAY.toString());
    }

    @Test
    void reportIsAdminOnlyAndUnknownUsersAre404() {
        api.get(admin, "/api/admin/users/" + UUID.randomUUID() + "/activity-report").expectCode(404, "NOT_FOUND");
        api.get(user, "/api/admin/users/" + user.id() + "/activity-report").expectCode(403, "FORBIDDEN");
    }
}
