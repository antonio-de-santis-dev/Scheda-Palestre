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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

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
        plan = factory.executablePlan(admin, "Storico", 2, 2, 1);
        String assignmentId = api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(plan.planId(), user.id(), MONDAY)).expect(201).read("$[0].id");
        api.put(user, "/api/me/assignments/" + assignmentId + "/schedule", "{\"weekdays\":[1,2,3,4,5,6,7]}").expect(200);
    }

    @AfterEach
    void tearDown() {
        clock.reset();
    }

    private Api.Response startAt(LocalDate day) {
        clock.setDate(day);
        return api.post(user, "/api/me/workouts", "{\"date\":\"" + day + "\"}").expect(201);
    }

    private void interrupt(Api.Response state) {
        api.post(user, "/api/me/workouts/" + state.read("$.workoutId") + "/interrupt", null).expect(200);
    }

    @Test
    void catalogIdentitySurvivesRenameAndRemovalOfThePlanEntry() {
        UUID entryId = plan.planExerciseIds().getFirst();
        UUID catalogId = jdbc.queryForObject("select exercise_id from plan_exercises where id = ?", UUID.class, entryId);
        Api.Response initial = startAt(MONDAY);
        String workoutId = initial.read("$.workoutId");
        String snapshotName = initial.read("$.exercises[0].exerciseName");
        assertThat((String) initial.read("$.exercises[0].identity.source")).isEqualTo("CATALOG");
        assertThat((String) initial.read("$.exercises[0].identity.id")).isEqualTo(catalogId.toString());
        interrupt(initial);
        jdbc.update("update exercises set name = 'Nome nuovo' where id = ?", catalogId);
        jdbc.update("delete from plan_exercises where id = ?", entryId);
        jdbc.update("delete from exercises where id = ?", catalogId);
        Api.Response history = api.get(user, "/api/me/workouts/" + workoutId).expect(200);
        assertThat((String) history.read("$.exercises[0].exerciseName")).isEqualTo(snapshotName);
        assertThat((String) history.read("$.exercises[0].identity.id")).isEqualTo(catalogId.toString());
        assertThat(jdbc.queryForObject("select catalog_exercise_id from workout_exercises where id = ?", UUID.class,
                UUID.fromString(history.read("$.exercises[0].id")))).isEqualTo(catalogId);
    }

    @Test
    void sameCatalogExerciseAcrossDifferentPlanEntriesKeepsTheSameIdentity() {
        UUID a = plan.planExerciseIds().getFirst(), b = plan.planExerciseIds().get(2);
        UUID catalogId = jdbc.queryForObject("select exercise_id from plan_exercises where id = ?", UUID.class, a);
        // A second occurrence in the same section is allowed and has a different plan-entry UUID.
        UUID section = jdbc.queryForObject("select muscle_section_id from plan_exercises where id = ?", UUID.class, a);
        jdbc.update("update plan_exercises set muscle_section_id = ?, position = 3, exercise_id = ? where id = ?", section, catalogId, b);
        Api.Response state = startAt(MONDAY);
        assertThat((List<String>) state.read("$.exercises[*].identity.id"))
                .containsExactly(catalogId.toString(),
                        jdbc.queryForObject("select exercise_id from plan_exercises where id = ?", UUID.class, plan.planExerciseIds().get(1)).toString(),
                        catalogId.toString());
        assertThat((List<String>) state.read("$.exercises[*].id")).doesNotHaveDuplicates();
        assertThat((List<String>) state.read("$.exercises[*].identity.source")).containsOnly("CATALOG");
    }

    @Test
    void duplicatedPlansKeepCatalogIdentityEvenWithDifferentEntryAndSnapshotIds() {
        Api.Response original = startAt(MONDAY);
        Api.Response copy = api.post(admin, "/api/admin/plans/" + plan.planId() + "/duplicate", null).expect(201);
        AuthenticatedUser other = fixtures.createUser();
        String assignment = api.post(admin, "/api/admin/assignments", """
                {"planId":"%s","userIds":["%s"],"startDate":"%s","activate":true}"""
                .formatted(copy.read("$.id"), other.id(), MONDAY)).expect(201).read("$[0].id");
        api.put(other, "/api/me/assignments/" + assignment + "/schedule", "{\"weekdays\":[1]}").expect(200);
        Api.Response second = api.post(other, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201);
        assertThat((String) copy.read("$.sessions[0].sections[0].exercises[0].id"))
                .isNotEqualTo(plan.planExerciseIds().getFirst().toString());
        assertThat((String) second.read("$.exercises[0].id")).isNotEqualTo(original.read("$.exercises[0].id"));
        assertThat((Map<String, Object>) second.read("$.exercises[0].identity"))
                .isEqualTo(original.read("$.exercises[0].identity"));
        api.get(other, "/api/me/workouts/" + original.read("$.workoutId")).expect(404);
    }

    @Test
    void equalNamesInDifferentGroupsDoNotShareAnIdentity() {
        UUID a = plan.planExerciseIds().getFirst(), b = plan.planExerciseIds().get(2);
        UUID catalogA = jdbc.queryForObject("select exercise_id from plan_exercises where id = ?", UUID.class, a);
        UUID catalogB = jdbc.queryForObject("select exercise_id from plan_exercises where id = ?", UUID.class, b);
        jdbc.update("update exercises set name = 'Omonimo' where id in (?, ?)", catalogA, catalogB);
        Api.Response monday = startAt(MONDAY); interrupt(monday);
        Api.Response tuesday = startAt(MONDAY.plusDays(1));
        assertThat((String) monday.read("$.exercises[0].exerciseName")).isEqualTo(tuesday.read("$.exercises[0].exerciseName"));
        assertThat((String) monday.read("$.exercises[0].identity.id")).isEqualTo(catalogA.toString());
        assertThat((String) tuesday.read("$.exercises[0].identity.id")).isEqualTo(catalogB.toString());
        assertThat(catalogA).isNotEqualTo(catalogB);
    }

    @Test
    void legacyIdentityStaysSeparateFromCatalogAndSurvivesDeletingThePlanEntry() {
        Api.Response state = startAt(MONDAY);
        String id = state.read("$.workoutId");
        UUID snapshotId = UUID.fromString(state.read("$.exercises[0].id"));
        UUID entryId = plan.planExerciseIds().getFirst();
        // Simulate an old snapshot: its saved legacy UUID is not a catalog identity.
        jdbc.update("update workout_exercises set catalog_exercise_id = null where id = ?", snapshotId);
        interrupt(state);
        Api.Response legacy = api.get(user, "/api/me/workouts/" + id).expect(200);
        assertThat((String) legacy.read("$.exercises[0].identity.source")).isEqualTo("LEGACY");
        assertThat((String) legacy.read("$.exercises[0].identity.id")).isEqualTo(entryId.toString());
        jdbc.update("delete from plan_exercises where id = ?", entryId);
        Api.Response reopened = api.get(user, "/api/me/workouts/" + id).expect(200);
        assertThat((Map<String, Object>) reopened.read("$.exercises[0].identity")).isEqualTo(legacy.read("$.exercises[0].identity"));
        // A legacy snapshot whose original reference is already lost keeps its own UUID.
        jdbc.update("update workout_exercises set legacy_exercise_id = null where id = ?", snapshotId);
        reopened = api.get(user, "/api/me/workouts/" + id).expect(200);
        assertThat((String) reopened.read("$.exercises[0].identity.id")).isEqualTo(snapshotId.toString());
        assertThat((String) reopened.read("$.exercises[0].identity.source")).isEqualTo("LEGACY");
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
        assertThat((Integer) api.get(user, "/api/me/workouts?q=storICO").expect(200).read("$.totalElements")).isEqualTo(1);
        assertThat((Integer) api.get(user, "/api/me/workouts?q=Rinominata").expect(200).read("$.totalElements")).isZero();
        assertThat((Integer) api.get(user, "/api/me/workouts", Map.of("q", "Giorno 1")).expect(200).read("$.totalElements")).isEqualTo(1);
    }

    @Test
    void historyIsPrivate() {
        String id = api.post(user, "/api/me/workouts", "{\"date\":\"" + MONDAY + "\"}").expect(201).read("$.workoutId");
        AuthenticatedUser other = fixtures.createUser();
        assertThat((Integer) api.get(other, "/api/me/workouts").expect(200).read("$.totalElements")).isZero();
        api.get(other, "/api/me/workouts/" + id).expectCode(404, "NOT_FOUND");
        api.get(other, "/api/me/workouts/" + UUID.randomUUID()).expectCode(404, "NOT_FOUND");
        api.get(admin, "/api/me/workouts").expectCode(403, "FORBIDDEN");
        assertThat((Integer) api.get(other, "/api/me/workouts?from=2026-10-05&to=2026-10-05&status=IN_PROGRESS&q=Storico").expect(200).read("$.totalElements")).isZero();
    }

    private Api.Response start(LocalDate date) {
        clock.setDate(date);
        return api.post(user, "/api/me/workouts", "{\"date\":\"" + date + "\"}").expect(201);
    }

    @Test
    void historyCombinesInclusiveCalendarDatesStatusNameAndPagination() {
        Api.Response monday = start(MONDAY);
        String mondayId = monday.read("$.workoutId");
        monday = api.post(user, "/api/me/workouts/" + mondayId + "/sets/" + monday.read("$.currentSetId") + "/complete", null).expect(200);
        api.post(user, "/api/me/workouts/" + mondayId + "/exercises/" + monday.read("$.currentExerciseId") + "/skip", null).expect(200);
        String tuesdayId = start(MONDAY.plusDays(1)).read("$.workoutId");
        api.post(user, "/api/me/workouts/" + tuesdayId + "/interrupt", null).expect(200);
        String wednesdayId = start(MONDAY.plusDays(2)).read("$.workoutId");
        Api.Response range = api.get(user, "/api/me/workouts?from=2026-10-05&to=2026-10-06&size=1").expect(200);
        assertThat((Integer) range.read("$.totalElements")).isEqualTo(2);
        assertThat((Integer) range.read("$.totalPages")).isEqualTo(2);
        assertThat((List<String>) range.read("$.content[*].id")).containsExactly(tuesdayId);
        assertThat((List<String>) api.get(user, "/api/me/workouts?from=2026-10-05&to=2026-10-06&size=1&page=1").expect(200).read("$.content[*].id")).containsExactly(mondayId);
        assertThat((List<String>) api.get(user, "/api/me/workouts?from=2026-10-05&to=2026-10-05&status=COMPLETED&q=stoRICO").expect(200).read("$.content[*].id")).containsExactly(mondayId);
        assertThat((List<String>) api.get(user, "/api/me/workouts?to=2026-10-05").expect(200).read("$.content[*].id")).containsExactly(mondayId);
        assertThat((List<String>) api.get(user, "/api/me/workouts?from=2026-10-07&status=IN_PROGRESS").expect(200).read("$.content[*].id")).containsExactly(wednesdayId);
        assertThat((List<String>) api.get(user, "/api/me/workouts?status=INTERRUPTED").expect(200).read("$.content[*].id")).containsExactly(tuesdayId);
        assertThat((Integer) api.get(user, "/api/me/workouts?from=2026-10-07&status=COMPLETED").expect(200).read("$.totalElements")).isZero();
    }

    @Test
    void nameSearchTreatsWildcardsAndBackslashAsLiteralText() {
        String id = start(MONDAY).read("$.workoutId");
        jdbc.update("update workouts set plan_name_snapshot = ? where id = ?", "Carico 100%_\\ pronto", UUID.fromString(id));
        api.post(user, "/api/me/workouts/" + id + "/interrupt", null).expect(200);
        start(MONDAY.plusDays(1));
        for (String q : List.of("100%_\\", "%", "_", "\\", "  CARICO  ")) {
            assertThat((List<String>) api.get(user, "/api/me/workouts", Map.of("q", q)).expect(200).read("$.content[*].id")).containsExactly(id);
        }
        assertThat((Integer) api.get(user, "/api/me/workouts?q=100X").expect(200).read("$.totalElements")).isZero();
        assertThat((Integer) api.get(user, "/api/me/workouts", Map.of("q", "  ")).expect(200).read("$.totalElements")).isEqualTo(2);
    }

    @Test
    void invalidHistoryFiltersAreRejected() {
        api.get(user, "/api/me/workouts?from=2026-10-06&to=2026-10-05").expectCode(400, "INVALID_HISTORY_FILTER");
        api.get(user, "/api/me/workouts?q=" + "x".repeat(101)).expectCode(400, "INVALID_HISTORY_FILTER");
        api.get(user, "/api/me/workouts?from=not-a-date").expect(400);
        api.get(user, "/api/me/workouts?from=2026-02-30").expect(400);
        api.get(user, "/api/me/workouts?status=FAILED").expect(400);
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
