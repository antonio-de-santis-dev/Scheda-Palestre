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

@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class HistoryIntegrationTest {

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
    BuiltPlan plan;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        plan = factory.executablePlan(admin, "Storico", 2, 2, 1);
        api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    @Test
    void historyListsWorkoutsNewestFirstWithSkippedExercises() {
        // Monday: first exercise completed, second skipped.
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        String mondayId = state.read("$.workoutId");
        state = api.post(user, "/api/me/workouts/" + mondayId + "/sets/" + state.read("$.currentSetId") + "/complete",
                null).expect(200);
        api.post(user, "/api/me/workouts/" + mondayId + "/exercises/" + state.read("$.currentExerciseId") + "/skip", null)
                .expect(200);
        // Tuesday: interrupted immediately.
        clock.setDate(MONDAY.plusDays(1));
        String tuesdayId = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY.plusDays(1) + "\"}").expect(201)
                .read("$.workoutId");
        api.post(user, "/api/me/workouts/" + tuesdayId + "/interrupt", null).expect(200);

        Api.Response history = api.get(user, "/api/me/workouts?size=10").expect(200);
        assertThat((Integer) history.read("$.totalElements")).isEqualTo(2);
        assertThat((List<String>) history.read("$.content[*].id")).containsExactly(tuesdayId, mondayId);
        assertThat((List<String>) history.read("$.content[*].status")).containsExactly("INTERRUPTED", "COMPLETED");
        assertThat((Integer) history.read("$.content[1].completedExercises")).isEqualTo(1);
        assertThat((Integer) history.read("$.content[1].skippedExercises")).isEqualTo(1);

        Api.Response detail = api.get(user, "/api/me/workouts/" + mondayId).expect(200);
        assertThat((List<String>) detail.read("$.exercises[*].status")).containsExactly("COMPLETED", "SKIPPED");

        // Paginated.
        Api.Response page = api.get(user, "/api/me/workouts?size=1&page=1").expect(200);
        assertThat((List<String>) page.read("$.content[*].id")).containsExactly(mondayId);
    }

    @Test
    void historyKeepsSnapshotValuesAfterPlanChanges() {
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        String id = state.read("$.workoutId");
        api.post(user, "/api/me/workouts/" + id + "/interrupt", null).expect(200);

        api.put(admin, "/api/admin/plans/" + plan.planId(), "{\"name\":\"Rinominata\",\"version\":%d}"
                .formatted((Integer) api.get(admin, "/api/admin/plans/" + plan.planId()).read("$.version"))).expect(200);
        api.delete(admin, "/api/admin/sessions/" + plan.sessionIds().getFirst()).expect(200);
        api.delete(admin, "/api/admin/plans/" + plan.planId()).expect(204);

        Api.Response detail = api.get(user, "/api/me/workouts/" + id).expect(200);
        assertThat((String) detail.read("$.planName")).isEqualTo("Storico");
        assertThat((String) detail.read("$.sessionTitle")).isEqualTo("Giorno 1");
        assertThat((List<Object>) detail.read("$.exercises")).hasSize(2);
        assertThat((String) api.get(user, "/api/me/workouts").read("$.content[0].planName")).isEqualTo("Storico");
    }

    @Test
    void historyIsPrivate() {
        String id = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201).read("$.workoutId");
        AuthenticatedUser other = fixtures.createUser();
        assertThat((Integer) api.get(other, "/api/me/workouts").expect(200).read("$.totalElements")).isZero();
        api.get(other, "/api/me/workouts/" + id).expectCode(404, "NOT_FOUND");
        api.get(other, "/api/me/workouts/" + UUID.randomUUID()).expectCode(404, "NOT_FOUND");
        api.get(admin, "/api/me/workouts").expectCode(403, "FORBIDDEN");
    }

    @Test
    void profileAllowsOnlyThePhoneToChange() {
        Api.Response profile = api.get(user, "/api/me/profile").expect(200);
        assertThat((String) profile.read("$.username")).isEqualTo(user.username());
        assertThat(profile.body()).doesNotContain("password");

        Api.Response updated = api.put(user, "/api/me/profile",
                "{\"phone\":\"+39 333 0000000\",\"role\":\"ADMIN\",\"username\":\"hacked\",\"email\":\"x@x.test\"}")
                .expect(200);
        assertThat((String) updated.read("$.phone")).isEqualTo("+39 333 0000000");
        assertThat((String) updated.read("$.role")).isEqualTo("USER");
        assertThat((String) updated.read("$.username")).isEqualTo(user.username());

        api.put(user, "/api/me/profile", "{\"phone\":\"abc\"}").expectCode(400, "VALIDATION_ERROR");
        assertThat((Object) api.put(user, "/api/me/profile", "{\"phone\":\"\"}").expect(200).read("$.phone")).isNull();
        // ADMINs can also edit their own phone.
        api.put(admin, "/api/me/profile", "{\"phone\":\"0612345678\"}").expect(200);
    }
}
