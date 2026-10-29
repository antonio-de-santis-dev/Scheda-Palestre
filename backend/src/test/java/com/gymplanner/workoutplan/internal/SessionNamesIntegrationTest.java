package com.gymplanner.workoutplan.internal;

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
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 2: the session name chosen by the ADMIN is stored and used everywhere. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class SessionNamesIntegrationTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    @Test
    void namesAreStoredListedInRotationOrderAndSnapshottedInTheWorkout() {
        String name = "Nomi " + UUID.randomUUID();
        BuiltPlan plan = factory.executablePlan(admin, name, 2, 1, 1);
        api.put(admin, "/api/admin/sessions/" + plan.sessionIds().get(0), "{\"title\":\"  Parte superiore  \"}").expect(200);
        api.put(admin, "/api/admin/sessions/" + plan.sessionIds().get(1), "{\"title\":\"Gambe\"}").expect(200);
        api.put(admin, "/api/admin/plans/" + plan.planId() + "/sessions/order",
                "{\"ids\":[\"" + plan.sessionIds().get(1) + "\",\"" + plan.sessionIds().get(0) + "\"]}").expect(200);
        assertThat(jdbc.queryForObject("select title from plan_sessions where id = ?", String.class, plan.sessionIds().get(0)))
                .isEqualTo("Parte superiore");

        Api.Response list = api.get(admin, "/api/admin/plans?q=" + name).expect(200);
        assertThat((List<String>) list.read("$.content[0].sessionTitles")).containsExactly("Gambe", "Parte superiore");

        AuthenticatedUser user = fixtures.createUser();
        UUID assignment = UUID.fromString(api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id"));
        api.put(user, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
        assertThat((String) api.get(user, "/api/me/today").expect(200).read("$.session.title")).isEqualTo("Gambe");
        Api.Response workout = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        assertThat((String) workout.read("$.sessionTitle")).isEqualTo("Gambe");
        // Renaming later never rewrites the history snapshot.
        api.put(admin, "/api/admin/sessions/" + plan.sessionIds().get(1), "{\"title\":\"Gambe e glutei\"}").expect(200);
        assertThat((String) api.get(user, "/api/me/workouts/" + workout.read("$.workoutId")).expect(200).read("$.sessionTitle"))
                .isEqualTo("Gambe");
    }

    @Test
    void blankOrTooLongNamesAreRejected() {
        UUID planId = factory.createPlan(admin, "Validazione " + UUID.randomUUID());
        api.post(admin, "/api/admin/plans/" + planId + "/sessions", "{\"title\":\"   \"}").expectCode(400, "VALIDATION_ERROR");
        api.post(admin, "/api/admin/plans/" + planId + "/sessions", "{\"title\":\"" + "x".repeat(61) + "\"}")
                .expectCode(400, "VALIDATION_ERROR");
    }
}
