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
import java.util.Arrays;
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
import org.springframework.jdbc.core.JdbcTemplate;

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
    @Autowired
    JdbcTemplate jdbc;

    AuthenticatedUser admin;
    AuthenticatedUser user;
    /** The assignment created by the last {@link #assign} call. */
    UUID assignmentId;

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
        return assign(sessions, "Rotazione", MONDAY, "");
    }

    private BuiltPlan assign(int sessions, String name, LocalDate start, String extra) {
        BuiltPlan plan = factory.executablePlan(admin, name, sessions, 1, 2);
        Api.Response created = api.post(admin, "/api/admin/assignments",
                "{\"planId\":\"%s\",\"userIds\":[\"%s\"],\"startDate\":\"%s\",\"activate\":true%s}"
                        .formatted(plan.planId(), user.id(), start, extra)).expect(201);
        lastResponse = created;
        assignmentId = UUID.fromString(created.read("$[0].id"));
        return plan;
    }

    Api.Response lastResponse;

    private static String scheduleUrl(UUID id) {
        return "/api/me/assignments/" + id + "/schedule";
    }

    private String scheduleUrl() {
        return scheduleUrl(assignmentId);
    }

    private static String days(Integer... weekdays) {
        return "{\"weekdays\":" + Arrays.toString(weekdays) + "}";
    }

    private void close(UUID id) {
        api.post(admin, "/api/admin/assignments/" + id + "/close", null).expect(200);
    }

    private AssignmentView active() {
        return assignments.find(assignmentId).orElseThrow();
    }

    private String title(LocalDate date) {
        DayPlan plan = calendar.dayPlan(active(), date);
        return plan.isTraining() ? plan.sessionTitle() : plan.type().name();
    }

    @Test
    void scheduleIsEmptyUntilTheUserChoosesDays() {
        assign(2);
        Api.Response schedules = api.get(user, "/api/me/schedules").expect(200);
        assertThat((List<Integer>) schedules.read("$[0].weekdays")).isEmpty();
        assertThat((String) schedules.read("$[0].planName")).startsWith("Rotazione");
        assertThat((String) schedules.read("$[0].assignmentId")).isEqualTo(assignmentId.toString());
        assertThat(title(MONDAY)).isEqualTo("NO_SCHEDULE");
    }

    @Test
    void chosenDaysProduceTheSpecRotation() {
        assign(2);
        Api.Response saved = api.put(user, scheduleUrl(), days(5, 1, 3, 3)).expect(200);
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
        api.put(user, scheduleUrl(), days(0, 8)).expectCode(400, "VALIDATION_ERROR");
        api.put(user, scheduleUrl(), "{\"weekdays\":null}").expectCode(400, "VALIDATION_ERROR");
    }

    @Test
    void onlyOwnActivePlansCanReceiveDays() {
        assertThat((List<Object>) api.get(user, "/api/me/schedules").expect(200).read("$")).isEmpty();
        assign(2);
        // Another user's assignment is indistinguishable from a missing one.
        AuthenticatedUser other = fixtures.createUser();
        api.put(other, scheduleUrl(), days(1)).expectCode(404, "NOT_FOUND");
        api.put(user, scheduleUrl(UUID.randomUUID()), days(1)).expectCode(404, "NOT_FOUND");
        // A closed plan keeps its history but its days cannot change.
        close(assignmentId);
        api.put(user, scheduleUrl(), days(1)).expectCode(422, "ASSIGNMENT_NOT_ACTIVE");
    }

    @Test
    void twoActivePlansCannotShareAWeekday() {
        assign(2, "Forza", MONDAY, "");
        UUID strength = assignmentId;
        api.put(user, scheduleUrl(strength), days(1, 5)).expect(200);
        assign(2, "Cardio", MONDAY, "");
        UUID cardio = assignmentId;

        Api.Response conflict = api.put(user, scheduleUrl(cardio), days(2, 5, 1)).expectCode(409, "SCHEDULE_DAY_CONFLICT");
        assertThat((List<Integer>) conflict.read("$.conflicts[*].weekday")).containsExactly(1, 5);
        assertThat((List<String>) conflict.read("$.conflicts[*].assignmentId")).containsOnly(strength.toString());
        assertThat((String) conflict.read("$.conflicts[0].planName")).startsWith("Forza");
        assertThat((List<Integer>) api.get(user, "/api/me/schedules").read("$[1].weekdays")).isEmpty();

        // Free days are accepted; the list shows which plan owns each day.
        api.put(user, scheduleUrl(cardio), days(2, 4)).expect(200);
        Api.Response all = api.get(user, "/api/me/schedules").expect(200);
        assertThat((List<Integer>) all.read("$[0].weekdays")).containsExactly(1, 5);
        assertThat((List<Integer>) all.read("$[1].weekdays")).containsExactly(2, 4);
        // Re-saving the same days of a plan is not a conflict with itself.
        api.put(user, scheduleUrl(strength), days(1, 5)).expect(200);
    }

    @Test
    void rotationIsIndependentForEachPlanAndClosingOneLeavesTheOther() {
        assign(2, "Forza", MONDAY, "");
        UUID strength = assignmentId;
        api.put(user, scheduleUrl(strength), days(1, 3)).expect(200);
        assign(3, "Cardio", MONDAY, "");
        UUID cardio = assignmentId;
        api.put(user, scheduleUrl(cardio), days(2, 4)).expect(200);

        // Strength: Mon G1, Wed G2, next Mon G1. Cardio: Tue G1, Thu G2, next Tue G3.
        assertThat(calendar.dayPlan(assignments.find(strength).orElseThrow(), MONDAY.plusDays(7)).sessionTitle())
                .isEqualTo("Giorno 1");
        assertThat(calendar.dayPlan(assignments.find(cardio).orElseThrow(), MONDAY.plusDays(8)).sessionTitle())
                .isEqualTo("Giorno 3");

        close(strength);
        AssignmentView stillActive = assignments.find(cardio).orElseThrow();
        assertThat(stillActive.active()).isTrue();
        assertThat(calendar.dayPlan(stillActive, MONDAY.plusDays(8)).sessionTitle()).isEqualTo("Giorno 3");
        // The freed days can now be used by the remaining plan.
        api.put(user, scheduleUrl(cardio), days(1, 2, 3, 4)).expect(200);
    }

    @Test
    void concurrentRequestsCannotBothTakeTheSameDay() throws Exception {
        assign(2, "Forza", MONDAY, "");
        UUID strength = assignmentId;
        assign(2, "Cardio", MONDAY, "");
        UUID cardio = assignmentId;

        for (int round = 0; round < 5; round++) {
            api.put(user, scheduleUrl(strength), days()).expect(200);
            api.put(user, scheduleUrl(cardio), days()).expect(200);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            Callable<Integer> first = () -> {
                go.await();
                return api.put(user, scheduleUrl(strength), days(3)).status();
            };
            Callable<Integer> second = () -> {
                go.await();
                return api.put(user, scheduleUrl(cardio), days(3)).status();
            };
            List<Future<Integer>> results = List.of(pool.submit(first), pool.submit(second));
            go.countDown();
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());
            pool.shutdown();
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("select count(*) from weekly_schedules w "
                    + "join plan_assignments a on a.id = w.plan_assignment_id "
                    + "where a.user_id = ? and a.active and w.weekday = 3", Integer.class, user.id())).isEqualTo(1);
        }
    }

    @Test
    void changingDaysReanchorsWithoutRewritingThePast() {
        assign(3);
        api.put(user, scheduleUrl(), days(1, 3, 5)).expect(200);
        // Mon G1, Wed G2, Fri G3, next Mon G1.
        clock.setDate(MONDAY.plusDays(3)); // Thursday: Mon and Wed consumed, Friday would be G3.
        api.put(user, scheduleUrl(), days(2, 4, 6)).expect(200);
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
        api.put(user, scheduleUrl(), days(1, 3, 5)).expect(200);
        clock.setDate(MONDAY.plusDays(2)); // Wednesday: today is G2.
        api.put(user, scheduleUrl(), days(3, 6)).expect(200);
        assertThat(title(MONDAY.plusDays(2))).isEqualTo("Giorno 2");
        assertThat(title(MONDAY.plusDays(5))).isEqualTo("Giorno 3");
        assertThat(title(MONDAY.plusDays(9))).isEqualTo("Giorno 1");
    }

    @Test
    void reorderingSessionsKeepsTheNextSession() {
        BuiltPlan plan = assign(3);
        api.put(user, scheduleUrl(), days(1, 3, 5)).expect(200);
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
        api.put(user, scheduleUrl(), days(1, 3, 5)).expect(200);
        clock.setDate(MONDAY.plusDays(1)); // next: Wednesday G2
        api.delete(admin, "/api/admin/sessions/" + plan.sessionIds().get(1)).expect(200);
        assertThat(title(MONDAY.plusDays(2))).isEqualTo("Giorno 3");
        assertThat(title(MONDAY.plusDays(4))).isEqualTo("Giorno 1");
    }

    @Test
    void daysAreCopiedOnlyOnRequestAndOnlyWhenFree() {
        assign(2, "Vecchia", MONDAY, "");
        UUID old = assignmentId;
        api.put(user, scheduleUrl(old), days(2, 4, 6)).expect(200);
        assign(2, "Parallela", MONDAY, "");
        UUID parallel = assignmentId;
        api.put(user, scheduleUrl(parallel), days(1)).expect(200);
        close(old);
        api.put(user, scheduleUrl(parallel), days(1, 4)).expect(200);

        // Default: nothing is copied.
        assign(2, "Senza copia", MONDAY, "");
        assertThat((List<Integer>) api.get(user, "/api/me/schedules").read("$[1].weekdays")).isEmpty();
        assertThat((List<Integer>) lastResponse.read("$[0].copiedWeekdays")).isEmpty();
        close(assignmentId);
        // Make "Vecchia" the most recently created closed plan again.
        jdbc.update("update plan_assignments set created_at = now() + interval '1 hour' where id = ?", old);

        // Requested: the free days of the last closed plan are copied, the occupied ones reported.
        assign(2, "Con copia", MONDAY, ",\"copySchedule\":true");
        assertThat((List<Integer>) lastResponse.read("$[0].copiedWeekdays")).containsExactly(2, 6);
        assertThat((List<Integer>) lastResponse.read("$[0].skippedWeekdays")).containsExactly(4);
        Api.Response all = api.get(user, "/api/me/schedules").expect(200);
        assertThat((List<Integer>) all.read("$[1].weekdays")).containsExactly(2, 6);
        assertThat((List<Integer>) all.read("$[0].weekdays")).containsExactly(1, 4);
    }

    @Test
    void datesOutsideTheAssignmentPeriodHaveNoSession() {
        assign(2, "Futura", MONDAY.plusDays(7), "");
        api.put(user, scheduleUrl(), days(1, 3, 5)).expect(200);
        assertThat(title(MONDAY)).isEqualTo("OUT_OF_PERIOD");
        assertThat(title(MONDAY.plusDays(7))).isEqualTo("Giorno 1");
        List<DayPlan> range = calendar.range(active(), MONDAY, MONDAY.plusDays(13));
        assertThat(range).hasSize(14);
        assertThat(range.stream().filter(DayPlan::isTraining).map(DayPlan::sessionTitle).toList())
                .containsExactly("Giorno 1", "Giorno 2", "Giorno 1");
    }

    @Test
    void adminCannotUseTheUserSchedule() {
        api.get(admin, "/api/me/schedules").expectCode(403, "FORBIDDEN");
    }
}
