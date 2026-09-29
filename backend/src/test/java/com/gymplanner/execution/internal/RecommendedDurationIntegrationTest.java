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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * ADR 0009: the recommended duration ends the day <em>after</em> {@code expires_on}, in the gym's
 * time zone, and never blocks the plan.
 */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class RecommendedDurationIntegrationTest {

    /** Monday. */
    private static final LocalDate EXPIRES_ON = LocalDate.of(2026, 1, 22);

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
    UUID assignmentId;

    @BeforeEach
    void setUp() {
        clock.setDate(EXPIRES_ON.minusWeeks(3));
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        plan = factory.executablePlan(admin, "Durata", 1, 1, 1);
        jdbc.update("update workout_plans set duration_weeks = ? where id = ?", 3, plan.planId());
        assignmentId = UUID.fromString(api.post(admin, "/api/admin/assignments",
                "{\"planId\":\"%s\",\"userIds\":[\"%s\"],\"startDate\":\"%s\",\"activate\":true}"
                        .formatted(plan.planId(), user.id(), EXPIRES_ON.minusWeeks(3))).expect(201).read("$[0].id"));
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private boolean endedForUser() {
        Api.Response mine = api.get(user, "/api/me/assignments").expect(200);
        assertThat((String) mine.read("$[0].planExpiresOn")).isEqualTo(EXPIRES_ON.toString());
        return mine.read("$[0].recommendedDurationEnded");
    }

    private List<String> endedUsersForAdmin() {
        return api.get(admin, "/api/admin/assignments/recommended-duration-ended").expect(200).read("$[*].userId");
    }

    @Test
    void oneWeekWarningStartsOnJanuaryFifteenth() {
        clock.setDate(LocalDate.of(2026, 1, 14));
        assertThat(endedForUser()).isFalse();
        assertThat((List<Object>) api.get(user, "/api/me/today").read("$.recommendedDurationEnded")).isEmpty();
        clock.setDate(LocalDate.of(2026, 1, 15));
        assertThat(endedForUser()).isFalse();
        assertThat((Boolean) api.get(user, "/api/me/assignments").read("$[0].recommendedDurationWarning")).isTrue();
        assertThat((Boolean) api.get(user, "/api/me/today").read("$.recommendedDurationEnded[0].ended")).isFalse();
        assertThat(endedUsersForAdmin()).contains(user.id().toString());
        clock.setDate(EXPIRES_ON);
        assertThat(endedForUser()).isFalse();
    }

    @Test
    void fromTheDayAfterTheNoticeAppearsButTheplanStaysUsable() {
        clock.setDate(EXPIRES_ON.plusDays(1));
        assertThat(endedForUser()).isTrue();
        Api.Response today = api.get(user, "/api/me/today").expect(200);
        assertThat((String) today.read("$.recommendedDurationEnded[0].planName")).startsWith("Durata");
        assertThat((String) today.read("$.recommendedDurationEnded[0].expiresOn")).isEqualTo(EXPIRES_ON.toString());
        Api.Response ended = api.get(admin, "/api/admin/assignments/recommended-duration-ended").expect(200);
        List<String> ids = ended.read("$[?(@.userId == '" + user.id() + "')].assignmentId");
        assertThat(ids).containsExactly(assignmentId.toString());

        // Days can still be chosen and workouts started and completed.
        api.put(user, "/api/me/assignments/" + assignmentId + "/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
        Api.Response state = api.post(user, "/api/me/workouts", "{\"date\":\"" + EXPIRES_ON.plusDays(1) + "\"}").expect(201);
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId")
                + "/complete", null).expect(200);
        assertThat((String) api.get(user, "/api/me/workouts/" + state.read("$.workoutId")).read("$.status"))
                .isEqualTo("COMPLETED");
    }

    @Test
    void withoutExpiryThereIsNeverANotice() {
        jdbc.update("update workout_plans set duration_weeks = null where id = ?", plan.planId());
        clock.setDate(EXPIRES_ON.plusYears(1));
        Api.Response mine = api.get(user, "/api/me/assignments").expect(200);
        assertThat((Object) mine.read("$[0].planExpiresOn")).isNull();
        assertThat((Boolean) mine.read("$[0].recommendedDurationEnded")).isFalse();
        assertThat(endedUsersForAdmin()).doesNotContain(user.id().toString());
    }

    @Test
    void closedAssignmentsAreNotReported() {
        clock.setDate(EXPIRES_ON.plusDays(1));
        api.post(admin, "/api/admin/assignments/" + assignmentId + "/close", null).expect(200);
        assertThat(endedForUser()).isFalse();
        assertThat(endedUsersForAdmin()).doesNotContain(user.id().toString());
    }
}
