package com.gymplanner.calendar.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.assignment.api.AssignmentQueries;
import com.gymplanner.assignment.api.AssignmentView;
import com.gymplanner.calendar.api.CalendarQueries;
import com.gymplanner.calendar.api.DayPlan;
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
class CalendarIntegrationTest {

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
    @Autowired
    CalendarQueries calendar;
    @Autowired
    AssignmentQueries assignments;

    AuthenticatedUser admin;
    AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        clock.setDate(MONDAY);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private BuiltPlan assign(int sessions) {
        BuiltPlan plan = factory.executablePlan(admin, "Rotazione", sessions, 1, 2);
        api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201);
        return plan;
    }

    private AssignmentView active() {
        return assignments.findActiveForUser(user.id()).orElseThrow();
    }

    private String title(LocalDate date) {
        DayPlan plan = calendar.dayPlan(active(), date);
        return plan.isTraining() ? plan.sessionTitle() : plan.type().name();
    }

    @Test
    void scheduleIsEmptyUntilTheUserChoosesDays() {
        assign(2);
        Api.Response schedule = api.get(user, "/api/me/schedule").expect(200);
        assertThat((List<Integer>) schedule.read("$.weekdays")).isEmpty();
        assertThat(title(MONDAY)).isEqualTo("NO_SCHEDULE");
    }

    @Test
    void chosenDaysProduceTheSpecRotation() {
        assign(2);
        Api.Response saved = api.put(user, "/api/me/schedule", "{\"weekdays\":[5,1,3,3]}").expect(200);
        assertThat((List<Integer>) saved.read("$.weekdays")).containsExactly(1, 3, 5);
        assertThat(title(MONDAY)).isEqualTo("Giorno 1");
        assertThat(title(MONDAY.plusDays(1))).isEqualTo("REST");
        assertThat(title(MONDAY.plusDays(2))).isEqualTo("Giorno 2");
        assertThat(title(MONDAY.plusDays(4))).isEqualTo("Giorno 1");
        assertThat(title(MONDAY.plusDays(7))).isEqualTo("Giorno 2");
    }

    @Test
    void invalidWeekdaysAreRejected() {
        assign(2);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[0,8]}").expectCode(400, "VALIDATION_ERROR");
        api.put(user, "/api/me/schedule", "{\"weekdays\":null}").expectCode(400, "VALIDATION_ERROR");
    }

    @Test
    void withoutActiveAssignmentTheScheduleCannotBeSet() {
        api.get(user, "/api/me/schedule").expect(200);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1]}").expectCode(422, "NO_ACTIVE_ASSIGNMENT");
    }

    @Test
    void changingDaysReanchorsWithoutRewritingThePast() {
        assign(3);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1,3,5]}").expect(200);
        // Mon G1, Wed G2, Fri G3, next Mon G1.
        clock.setDate(MONDAY.plusDays(3)); // Thursday: Mon and Wed consumed, Friday would be G3.
        api.put(user, "/api/me/schedule", "{\"weekdays\":[2,4,6]}").expect(200);
        // Thursday is now a training day and receives the session that was due next.
        assertThat(title(MONDAY.plusDays(3))).isEqualTo("Giorno 3");
        assertThat(title(MONDAY.plusDays(5))).isEqualTo("Giorno 1");
        assertThat(title(MONDAY.plusDays(8))).isEqualTo("Giorno 2");
        AssignmentView view = active();
        assertThat(view.rotationAnchorDate()).isEqualTo(MONDAY.plusDays(3));
        assertThat(view.rotationAnchorIndex()).isEqualTo(2);
    }

    @Test
    void changingDaysOnATrainingDayKeepsTodaysSession() {
        assign(3);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1,3,5]}").expect(200);
        clock.setDate(MONDAY.plusDays(2)); // Wednesday: today is G2.
        api.put(user, "/api/me/schedule", "{\"weekdays\":[3,6]}").expect(200);
        assertThat(title(MONDAY.plusDays(2))).isEqualTo("Giorno 2");
        assertThat(title(MONDAY.plusDays(5))).isEqualTo("Giorno 3");
        assertThat(title(MONDAY.plusDays(9))).isEqualTo("Giorno 1");
    }

    @Test
    void reorderingSessionsKeepsTheNextSession() {
        BuiltPlan plan = assign(3);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1,3,5]}").expect(200);
        clock.setDate(MONDAY.plusDays(1)); // Tuesday; next training Wednesday = G2.
        List<UUID> ids = plan.sessionIds();
        api.put(admin, "/api/admin/plans/" + plan.planId() + "/sessions/order",
                "{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(ids.get(2), ids.get(1), ids.get(0))).expect(200);
        // New order: G3, G2, G1 -> Wednesday still G2, then G1, then G3.
        assertThat(title(MONDAY.plusDays(2))).isEqualTo("Giorno 2");
        assertThat(title(MONDAY.plusDays(4))).isEqualTo("Giorno 1");
        assertThat(title(MONDAY.plusDays(7))).isEqualTo("Giorno 3");
    }

    @Test
    void deletingTheNextSessionContinuesWithTheFollowingPosition() {
        BuiltPlan plan = assign(3);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1,3,5]}").expect(200);
        clock.setDate(MONDAY.plusDays(1)); // next: Wednesday G2
        api.delete(admin, "/api/admin/sessions/" + plan.sessionIds().get(1)).expect(200);
        assertThat(title(MONDAY.plusDays(2))).isEqualTo("Giorno 3");
        assertThat(title(MONDAY.plusDays(4))).isEqualTo("Giorno 1");
    }

    @Test
    void activatingANewPlanCopiesTheDaysOfThePreviousOne() {
        assign(2);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[2,4]}").expect(200);
        BuiltPlan other = factory.executablePlan(admin, "Nuova", 2, 1, 2);
        api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(other.planId(), user.id(), MONDAY)).expect(201);
        assertThat((List<Integer>) api.get(user, "/api/me/schedule").read("$.weekdays")).containsExactly(2, 4);
        assertThat(title(MONDAY.plusDays(1))).isEqualTo("Giorno 1");
    }

    @Test
    void daysAreNotCopiedWhenNotRequested() {
        assign(2);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[2,4]}").expect(200);
        BuiltPlan other = factory.executablePlan(admin, "Nuova", 2, 1, 2);
        api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true,"copySchedule":false}"""
                .formatted(other.planId(), user.id(), MONDAY)).expect(201);
        assertThat((List<Integer>) api.get(user, "/api/me/schedule").read("$.weekdays")).isEmpty();
    }

    @Test
    void datesOutsideTheAssignmentPeriodHaveNoSession() {
        BuiltPlan plan = factory.executablePlan(admin, "Futura", 2, 1, 2);
        api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY.plusDays(7))).expect(201);
        api.put(user, "/api/me/schedule", "{\"weekdays\":[1,3,5]}").expect(200);
        assertThat(title(MONDAY)).isEqualTo("OUT_OF_PERIOD");
        assertThat(title(MONDAY.plusDays(7))).isEqualTo("Giorno 1");
        List<DayPlan> range = calendar.range(active(), MONDAY, MONDAY.plusDays(13));
        assertThat(range).hasSize(14);
        assertThat(range.stream().filter(DayPlan::isTraining).map(DayPlan::sessionTitle).toList())
                .containsExactly("Giorno 1", "Giorno 2", "Giorno 1");
    }

    @Test
    void adminCannotUseTheUserSchedule() {
        api.get(admin, "/api/me/schedule").expectCode(403, "FORBIDDEN");
    }
}
