package com.gymplanner.calendar.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.MutableClock;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.TestFixtures;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Task 3: missed, skipped and moved training days keep the rotation order. */
@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class ScheduleChangesIntegrationTest {

    /** Monday. Training on Monday, Wednesday and Friday with two sessions. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    AuthenticatedUser admin;
    AuthenticatedUser user;
    UUID assignmentId;

    @BeforeEach
    void setUp() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        assignmentId = assign(user, MONDAY, "[1,3,5]");
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private UUID assign(AuthenticatedUser who, LocalDate start, String days) {
        var plan = factory.executablePlan(admin, "Slittamento " + UUID.randomUUID(), 2, 1, 1);
        UUID id = UUID.fromString(api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), who.id(), start)).expect(201).read("$[0].id"));
        api.put(who, "/api/me/assignments/" + id + "/schedule", "{\"weekdays\":" + days + "}").expect(200);
        return id;
    }

    private Map<String, Object> day(LocalDate date) {
        List<Map<String, Object>> days = api.get(user, "/api/me/calendar?from=" + date + "&to=" + date).expect(200).read("$");
        return days.getFirst();
    }

    private String session(LocalDate date) {
        Map<String, Object> d = day(date);
        return d.get("type") + ":" + d.get("sessionTitle");
    }

    private String base() {
        return "/api/me/assignments/" + assignmentId + "/schedule-changes";
    }

    private int events() {
        return jdbc.queryForObject("select count(*) from schedule_events where plan_assignment_id = ?", Integer.class, assignmentId);
    }

    @Test
    void aMissedDayIsRescheduledAutomaticallyOnceAndShiftsTheFollowingSessions() {
        assertThat(session(MONDAY)).isEqualTo("TRAINING:Giorno 1");
        assertThat(session(MONDAY.plusDays(2))).isEqualTo("TRAINING:Giorno 2");
        clock.setDate(MONDAY.plusDays(1)); // Monday ended without a workout

        assertThat((String) api.get(user, "/api/me/today").expect(200).read("$.status")).isEqualTo("REST_DAY");
        Map<String, Object> monday = day(MONDAY);
        assertThat(monday.get("type")).isEqualTo("SKIPPED");
        Map<String, Object> skip = (Map<String, Object>) monday.get("skip");
        assertThat(skip.get("kind")).isEqualTo("MISSED");
        assertThat(skip.get("sessionTitle")).isEqualTo("Giorno 1");
        assertThat(skip.get("landsOn")).isEqualTo(MONDAY.plusDays(2).toString());
        Map<String, Object> wednesday = day(MONDAY.plusDays(2));
        assertThat(wednesday.get("sessionTitle")).isEqualTo("Giorno 1");
        assertThat(wednesday.get("rescheduledFrom")).isEqualTo(MONDAY.toString());
        assertThat(session(MONDAY.plusDays(4))).isEqualTo("TRAINING:Giorno 2");
        assertThat(session(MONDAY.plusDays(7))).isEqualTo("TRAINING:Giorno 1");

        // Refreshes and repeated calls never reschedule the same day twice.
        for (int i = 0; i < 3; i++) api.get(user, "/api/me/today").expect(200);
        api.get(user, "/api/me/schedules").expect(200);
        assertThat(events()).isOne();

        // The rescheduled session is trained on Wednesday; the missed Monday stays in the calendar.
        clock.setDate(MONDAY.plusDays(2));
        Api.Response workout = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY.plusDays(2) + "\"}").expect(201);
        assertThat((String) workout.read("$.sessionTitle")).isEqualTo("Giorno 1");
        clock.setDate(MONDAY.plusDays(3));
        assertThat(day(MONDAY).get("type")).isEqualTo("SKIPPED");
        assertThat(((Map<String, Object>) day(MONDAY.plusDays(2)).get("workout")).get("sessionTitle")).isEqualTo("Giorno 1");
        assertThat(events()).isOne(); // a day with a workout is never marked as missed
    }

    @Test
    void theUserSkipsTodayAfterAPreviewAndCanRestoreThePreviousPlanning() {
        Api.Response options = api.get(user, base() + "/options?date=" + MONDAY).expect(200);
        assertThat((Boolean) options.read("$.canSkip")).isTrue();
        assertThat((List<String>) options.read("$.skipPreview[*].date"))
                .startsWith(MONDAY.toString(), MONDAY.plusDays(2).toString(), MONDAY.plusDays(4).toString());
        assertThat((String) options.read("$.skipPreview[0].before")).isEqualTo("Giorno 1");
        assertThat((Object) options.read("$.skipPreview[0].after")).isNull();
        assertThat((String) options.read("$.skipPreview[1].after")).isEqualTo("Giorno 1");
        assertThat((String) options.read("$.skipPreview[2].after")).isEqualTo("Giorno 2");

        Api.Response skipped = api.post(user, base(), "{\"date\":\"" + MONDAY + "\"}").expect(201);
        assertThat((String) skipped.read("$.state")).isEqualTo("SKIPPED");
        assertThat((String) api.get(user, "/api/me/today").expect(200).read("$.status")).isEqualTo("SKIPPED_DAY");
        assertThat((String) api.get(user, "/api/me/today").expect(200).read("$.skip.landsOn")).isEqualTo(MONDAY.plusDays(2).toString());
        api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expectCode(422, "NOT_A_TRAINING_DAY");
        // The same day cannot be skipped twice.
        api.post(user, base(), "{\"date\":\"" + MONDAY + "\"}").expectCode(422, "SCHEDULE_DATE_NOT_ALLOWED");

        String eventId = skipped.read("$.eventId");
        Api.Response restored = api.delete(user, base() + "/" + eventId).expect(200);
        assertThat((String) restored.read("$.state")).isEqualTo("PLANNED");
        assertThat(events()).isZero();
        assertThat(session(MONDAY.plusDays(2))).isEqualTo("TRAINING:Giorno 2");
    }

    @Test
    void undoingAPastMissedDayAcceptsTheLossWithoutBeingRescheduledAgain() {
        clock.setDate(MONDAY.plusDays(1));
        String eventId = api.get(user, base() + "/options?date=" + MONDAY).expect(200).read("$.eventId");
        Api.Response options = api.get(user, base() + "/options?date=" + MONDAY).expect(200);
        assertThat((String) options.read("$.undoPreview[1].before")).isEqualTo("Giorno 1");
        assertThat((String) options.read("$.undoPreview[1].after")).isEqualTo("Giorno 2");
        api.delete(user, base() + "/" + eventId).expect(200);
        for (int i = 0; i < 2; i++) api.get(user, "/api/me/today").expect(200);
        assertThat(jdbc.queryForObject("select count(*) from schedule_events where plan_assignment_id = ? and cancelled_at is null",
                Integer.class, assignmentId)).isZero();
        assertThat(session(MONDAY)).isEqualTo("TRAINING:Giorno 1");
        assertThat(session(MONDAY.plusDays(2))).isEqualTo("TRAINING:Giorno 2");
        // It can be marked as skipped again, on purpose.
        api.post(user, base(), "{\"date\":\"" + MONDAY + "\"}").expect(201);
        assertThat(session(MONDAY.plusDays(2))).isEqualTo("TRAINING:Giorno 1");
    }

    @Test
    void aSessionMovedToAFreeDayIsTrainedThereAndTheChangeLocksOnceTrained() {
        Api.Response options = api.get(user, base() + "/options?date=" + MONDAY).expect(200);
        assertThat((List<String>) options.read("$.moveTargets")).containsExactly(MONDAY.plusDays(1).toString());
        api.post(user, base(), "{\"date\":\"" + MONDAY + "\",\"targetDate\":\"" + MONDAY.plusDays(3) + "\"}")
                .expectCode(422, "SCHEDULE_TARGET_NOT_ALLOWED");
        Api.Response moved = api.post(user, base(), "{\"date\":\"" + MONDAY + "\",\"targetDate\":\"" + MONDAY.plusDays(1) + "\"}")
                .expect(201);
        assertThat((String) moved.read("$.state")).isEqualTo("MOVED");
        assertThat((String) moved.read("$.landsOn")).isEqualTo(MONDAY.plusDays(1).toString());
        assertThat(session(MONDAY.plusDays(1))).isEqualTo("TRAINING:Giorno 1");
        assertThat(session(MONDAY.plusDays(2))).isEqualTo("TRAINING:Giorno 2");

        clock.setDate(MONDAY.plusDays(1));
        Api.Response workout = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY.plusDays(1) + "\"}").expect(201);
        assertThat((String) workout.read("$.sessionTitle")).isEqualTo("Giorno 1");
        String eventId = moved.read("$.eventId");
        api.delete(user, base() + "/" + eventId).expectCode(409, "SCHEDULE_CHANGE_LOCKED");
    }

    @Test
    void movesAvoidTheDaysOfAnotherActivePlanAndChangingDaysDropsFutureChanges() {
        assign(user, MONDAY, "[2]"); // Tuesday belongs to another plan
        Api.Response options = api.get(user, base() + "/options?date=" + MONDAY).expect(200);
        assertThat((Boolean) options.read("$.canMove")).isFalse();
        api.post(user, base(), "{\"date\":\"" + MONDAY.plusDays(4) + "\"}").expect(201);
        api.put(user, "/api/me/assignments/" + assignmentId + "/schedule", "{\"weekdays\":[1,3,4]}").expect(200);
        assertThat(events()).isZero();
    }

    @Test
    void otherUsersAndAdminsCannotReadOrChangeTheSchedule() {
        AuthenticatedUser other = fixtures.createUser();
        api.get(other, base() + "/options?date=" + MONDAY).expect(404);
        api.post(other, base(), "{\"date\":\"" + MONDAY + "\"}").expect(404);
        api.get(admin, base() + "/options?date=" + MONDAY).expect(403);
        api.get(null, base()).expect(401);
    }

    @Test
    void worksAcrossTheEndOfTheYear() {
        LocalDate monday = LocalDate.of(2026, 12, 28);
        clock.setDate(monday);
        user = fixtures.createUser();
        assignmentId = assign(user, monday, "[1,3,5]");
        clock.setDate(LocalDate.of(2026, 12, 31)); // Monday and Wednesday ended without workouts
        assertThat(session(LocalDate.of(2027, 1, 1))).isEqualTo("TRAINING:Giorno 1");
        assertThat(session(LocalDate.of(2027, 1, 4))).isEqualTo("TRAINING:Giorno 2");
        assertThat(day(LocalDate.of(2026, 12, 30)).get("type")).isEqualTo("SKIPPED");
        assertThat(events()).isEqualTo(2);
    }
}
