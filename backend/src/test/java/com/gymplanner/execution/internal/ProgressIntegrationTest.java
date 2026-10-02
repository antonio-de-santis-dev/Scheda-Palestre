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
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@SuppressWarnings({"unchecked", "cast"})
class ProgressIntegrationTest {
    static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    @Autowired Api api;
    @Autowired TestFixtures fixtures;
    @Autowired PlanFactory factory;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    AuthenticatedUser admin;
    AuthenticatedUser user;
    PlanFactory.BuiltPlan plan;
    String assignment;

    @BeforeEach
    void setup() {
        clock.setDate(MONDAY);
        admin = fixtures.createAdmin();
        user = fixtures.createUser();
        plan = factory.executablePlan(admin, "Progressi", 1, 1, 3);
        assignment = api.post(admin, "/api/admin/assignments", """
            {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}
            """.formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id");
        api.put(user, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
    }
    @AfterEach void reset() { clock.reset(); }
    Api.Response start(LocalDate date) {
        clock.setDate(date);
        return api.post(user, "/api/me/workouts", "{\"date\":\"" + date + "\"}").expect(201);
    }
    Api.Response complete(Api.Response state, String json) {
        clock.advance(Duration.ofSeconds(60));
        return api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/sets/" + state.read("$.currentSetId") + "/complete", json).expect(200);
    }
    String url(String range) { return "/api/me/progress?" + range; }

    @Test
    void computesRealVolumeCoverageAndIndependentRecordsWithoutPlannedFallback() {
        Api.Response state = start(MONDAY);
        state = complete(state, "{\"weightKgUsed\":20.5,\"repsActual\":8}");
        state = complete(state, "{\"weightKgUsed\":30,\"repsActual\":null}");
        complete(state, null);
        Api.Response report = api.get(user, url("from=2026-10-05&to=2026-10-05")).expect(200);
        assertThat((Integer) report.read("$.days[0].completed")).isEqualTo(1);
        assertThat((Integer) report.read("$.days[0].durationSeconds")).isEqualTo(180);
        assertThat((Integer) report.read("$.exerciseDays[0].completedSets")).isEqualTo(3);
        assertThat((Integer) report.read("$.exerciseDays[0].volumeSets")).isEqualTo(1);
        assertThat(((Number) report.read("$.exerciseDays[0].volumeKg")).doubleValue()).isEqualTo(164);
        assertThat(((Number) report.read("$.records[0].maxWeightKg")).doubleValue()).isEqualTo(30);
        assertThat((Integer) report.read("$.records[0].maxReps")).isEqualTo(8);
        assertThat((String) report.read("$.records[0].key")).startsWith("catalog:");
    }

    @Test
    void includesPartialInterruptedAndActiveSetsButOnlyClosedWorkoutDuration() {
        Api.Response state = complete(start(MONDAY), "{\"weightKgUsed\":0,\"repsActual\":0}");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        complete(start(MONDAY.plusDays(1)), "{\"weightKgUsed\":10,\"repsActual\":4}");
        Api.Response report = api.get(user, url("from=2026-10-05&to=2026-10-06")).expect(200);
        assertThat((Integer) report.read("$.days[0].interrupted")).isEqualTo(1);
        assertThat((Integer) report.read("$.days[1].inProgress")).isEqualTo(1);
        assertThat((Integer) report.read("$.days[1].durationSeconds")).isZero();
        assertThat((Integer) report.read("$.days[1].closedWorkouts")).isZero();
        assertThat(((Number) report.read("$.exerciseDays[0].volumeKg")).doubleValue()).isZero();
        assertThat((Integer) report.read("$.exerciseDays[0].volumeSets")).isEqualTo(1);
        assertThat(((Number) report.read("$.exerciseDays[1].volumeKg")).doubleValue()).isEqualTo(40);
        assertThat((List<String>) report.read("$.exerciseDays[*].key")).hasSize(2).allMatch(k -> k.equals(report.read("$.records[0].key")));
    }

    @Test
    void dateLimitsAreInclusiveAndRecordsIgnoreSelectedPeriod() {
        Api.Response state = complete(start(MONDAY), "{\"weightKgUsed\":25,\"repsActual\":7}");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        Api.Response report = api.get(user, url("from=2026-10-06&to=2026-10-06")).expect(200);
        assertThat((List<Object>) report.read("$.days")).isEmpty();
        assertThat((List<Object>) report.read("$.exerciseDays")).isEmpty();
        assertThat((List<Object>) report.read("$.records")).hasSize(1);
        assertThat((String) api.get(user, "/api/me/progress").expect(200).read("$.from")).isEqualTo(MONDAY.minusDays(83).toString());
        api.get(user, url("from=2026-10-06&to=2026-10-05")).expectCode(400, "VALIDATION_ERROR");
        api.get(user, url("from=2025-10-04&to=2026-10-05")).expectCode(400, "VALIDATION_ERROR");
        api.get(user, url("from=bad")).expect(400);
    }

    @Test
    void missingMeasurementsStayNullAndUncompletedSetsNeverBecomeRecords() {
        Api.Response state = complete(start(MONDAY), null);
        Api.Response report = api.get(user, "/api/me/progress").expect(200);
        assertThat((Integer) report.read("$.exerciseDays[0].volumeSets")).isZero();
        assertThat((Object) report.read("$.exerciseDays[0].volumeKg")).isNull();
        assertThat((Object) report.read("$.exerciseDays[0].maxWeightKg")).isNull();
        assertThat((Object) report.read("$.exerciseDays[0].maxReps")).isNull();
        assertThat((List<Object>) report.read("$.records")).isEmpty();
        assertThat((Integer) report.read("$.exerciseDays[0].completedSets")).isEqualTo(1);
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
    }

    @Test
    void oldIdentitiesRemainSeparateAndSurvivePlanDeletion() {
        Api.Response state = complete(start(MONDAY), "{\"weightKgUsed\":15,\"repsActual\":6}");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        jdbc.update("update workout_exercises set catalog_exercise_id = null, legacy_exercise_id = plan_exercise_id where workout_id = ?", java.util.UUID.fromString(state.read("$.workoutId")));
        state = complete(start(MONDAY.plusDays(1)), "{\"weightKgUsed\":20,\"repsActual\":5}");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        api.delete(admin, "/api/admin/sessions/" + plan.sessionIds().getFirst()).expect(200);
        Api.Response report = api.get(user, "/api/me/progress").expect(200);
        assertThat((List<Object>) report.read("$.records")).hasSize(2);
        assertThat((List<String>) report.read("$.records[*].key")).anyMatch(k -> k.startsWith("catalog:")).anyMatch(k -> k.startsWith("legacy:"));
        assertThat((List<Object>) report.read("$.exerciseDays")).hasSize(2);
    }

    @Test
    void catalogIdentityCombinesTheSameExerciseAcrossDuplicatedPlans() {
        Api.Response state = complete(start(MONDAY), "{\"weightKgUsed\":15,\"repsActual\":6}");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        api.put(user, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1]}").expect(200);
        String copiedPlan = api.post(admin, "/api/admin/plans/" + plan.planId() + "/duplicate", null).expect(201).read("$.id");
        String copiedAssignment = api.post(admin, "/api/admin/assignments", """
            {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}
            """.formatted(copiedPlan, user.id(), MONDAY.plusDays(1))).expect(201).read("$[0].id");
        api.put(user, "/api/me/assignments/" + copiedAssignment + "/schedule", "{\"weekdays\":[2]}").expect(200);
        complete(start(MONDAY.plusDays(1)), "{\"weightKgUsed\":20,\"repsActual\":4}");
        Api.Response report = api.get(user, "/api/me/progress").expect(200);
        assertThat((List<Object>) report.read("$.records")).hasSize(1);
        assertThat(((Number) report.read("$.records[0].maxWeightKg")).doubleValue()).isEqualTo(20);
        assertThat((Integer) report.read("$.records[0].maxReps")).isEqualTo(6);
        assertThat((List<String>) report.read("$.exerciseDays[*].key")).hasSize(2).allMatch(k -> k.equals(report.read("$.records[0].key")));
    }

    @Test
    void reportsArePrivateAndHistoryFiltersPreservePagingAndSnapshotSearch() {
        Api.Response state = complete(start(MONDAY), "{\"weightKgUsed\":10,\"repsActual\":3}");
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        state = start(MONDAY.plusDays(1));
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
        AuthenticatedUser other = fixtures.createUser();
        Api.Response empty = api.get(other, "/api/me/progress").expect(200);
        assertThat((List<Object>) empty.read("$.days")).isEmpty();
        assertThat((List<Object>) empty.read("$.records")).isEmpty();
        api.get(admin, "/api/me/progress").expectCode(403, "FORBIDDEN");
        assertThat((Integer) api.get(user, "/api/me/workouts?from=2026-10-05&to=2026-10-05&status=INTERRUPTED&search=PROGRESSI").expect(200).read("$.totalElements")).isEqualTo(1);
        assertThat((Integer) api.get(user, "/api/me/workouts?status=COMPLETED").expect(200).read("$.totalElements")).isZero();
        assertThat((Integer) api.get(user, "/api/me/workouts?search=%25").expect(200).read("$.totalElements")).isZero();
        assertThat((String) api.get(user, "/api/me/workouts?search=giorno&size=1&page=1").expect(200).read("$.content[0].scheduledDate")).isEqualTo(MONDAY.toString());
        api.get(user, "/api/me/workouts?from=2026-10-06&to=2026-10-05").expectCode(400, "VALIDATION_ERROR");
    }
}
