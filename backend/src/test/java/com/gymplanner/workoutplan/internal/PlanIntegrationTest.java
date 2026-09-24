package com.gymplanner.workoutplan.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.support.Api;
import com.gymplanner.support.IntegrationTest;
import com.gymplanner.support.PlanFactory;
import com.gymplanner.support.PlanFactory.BuiltPlan;
import com.gymplanner.support.TestFixtures;
import com.gymplanner.workoutplan.api.WorkoutPlanEvents;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@Import(PlanIntegrationTest.EventRecorder.class)
class PlanIntegrationTest {

    @TestConfiguration
    static class EventRecorder {
        final List<Object> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(WorkoutPlanEvents.PlanSessionsChanged event) {
            events.add(event);
        }

        @EventListener
        void on(WorkoutPlanEvents.PlanDeleted event) {
            events.add(event);
        }
    }

    @Autowired
    Api api;
    @Autowired
    TestFixtures fixtures;
    @Autowired
    PlanFactory factory;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EventRecorder recorder;

    AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        admin = fixtures.createAdmin();
    }

    @Test
    void emptyPlanExistsButIsNotExecutable() {
        Api.Response plan = api.post(admin, "/api/admin/plans",
                "{\"name\":\"Principianti\",\"description\":\"  \",\"expiresOn\":\"2026-12-31\"}").expect(201);
        assertThat((Boolean) plan.read("$.executable")).isFalse();
        assertThat((Object) plan.read("$.description")).isNull();
        assertThat((String) plan.read("$.expiresOn")).isEqualTo("2026-12-31");
        assertThat((String) plan.read("$.createdBy")).isEqualTo(admin.id().toString());
        assertThat(plan.body()).doesNotContain("userId");
    }

    @Test
    void buildsCompleteExecutablePlanWithOrderedStructure() {
        BuiltPlan built = factory.executablePlan(admin, "Full body", 2, 2, 3);
        Api.Response plan = api.get(admin, "/api/admin/plans/" + built.planId()).expect(200);
        assertThat((Boolean) plan.read("$.executable")).isTrue();
        assertThat((List<String>) plan.read("$.sessions[*].title")).containsExactly("Giorno 1", "Giorno 2");
        assertThat((List<Integer>) plan.read("$.sessions[0].sections[0].exercises[*].position")).containsExactly(1, 2);
        assertThat((List<Object>) plan.read("$.sessions[0].sections[0].exercises[0].sets")).hasSize(3);
        assertThat((Boolean) plan.read("$.sessions[0].sections[0].exercises[0].customized")).isFalse();
        assertThat((String) plan.read("$.sessions[0].sections[0].muscleGroupName")).startsWith("Gruppo 1");
    }

    @Test
    void sessionWithoutExercisesMakesPlanNotExecutable() {
        BuiltPlan built = factory.executablePlan(admin, "Parziale", 1, 1, 3);
        Api.Response plan = api.post(admin, "/api/admin/plans/" + built.planId() + "/sessions", "{\"title\":\"Vuota\"}")
                .expect(201);
        assertThat((Boolean) plan.read("$.executable")).isFalse();
    }

    @Test
    void reorderingIsAtomicAndValidatesTheIdSet() {
        BuiltPlan built = factory.executablePlan(admin, "Ordine", 3, 1, 2);
        recorder.events.clear();
        List<UUID> ids = built.sessionIds();
        String json = "{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(ids.get(2), ids.get(0), ids.get(1));
        Api.Response plan = api.put(admin, "/api/admin/plans/" + built.planId() + "/sessions/order", json).expect(200);
        assertThat((List<String>) plan.read("$.sessions[*].title")).containsExactly("Giorno 3", "Giorno 1", "Giorno 2");
        assertThat((List<Integer>) plan.read("$.sessions[*].position")).containsExactly(1, 2, 3);
        assertThat(recorder.events).hasSize(1).first().isInstanceOf(WorkoutPlanEvents.PlanSessionsChanged.class);

        // Reload from the database: positions persisted.
        plan = api.get(admin, "/api/admin/plans/" + built.planId()).expect(200);
        assertThat((List<String>) plan.read("$.sessions[*].title")).containsExactly("Giorno 3", "Giorno 1", "Giorno 2");

        String incomplete = "{\"ids\":[\"%s\",\"%s\"]}".formatted(ids.get(0), ids.get(1));
        api.put(admin, "/api/admin/plans/" + built.planId() + "/sessions/order", incomplete)
                .expectCode(422, "INVALID_ORDER");
        String duplicated = "{\"ids\":[\"%s\",\"%s\",\"%s\"]}".formatted(ids.get(0), ids.get(0), ids.get(1));
        api.put(admin, "/api/admin/plans/" + built.planId() + "/sessions/order", duplicated)
                .expectCode(422, "INVALID_ORDER");
    }

    @Test
    void deletingASessionRenumbersTheOthers() {
        BuiltPlan built = factory.executablePlan(admin, "Tre giorni", 3, 1, 2);
        Api.Response plan = api.delete(admin, "/api/admin/sessions/" + built.sessionIds().get(0)).expect(200);
        assertThat((List<String>) plan.read("$.sessions[*].title")).containsExactly("Giorno 2", "Giorno 3");
        assertThat((List<Integer>) plan.read("$.sessions[*].position")).containsExactly(1, 2);
    }

    @Test
    void sectionsAreUniquePerMuscleGroupAndReorderable() {
        BuiltPlan built = factory.executablePlan(admin, "Sezioni", 1, 1, 2);
        UUID sessionId = built.sessionIds().get(0);
        UUID group = fixtures.createMuscleGroup("Spalle");
        Api.Response plan = api.post(admin, "/api/admin/sessions/" + sessionId + "/sections",
                "{\"muscleGroupId\":\"" + group + "\"}").expect(201);
        String second = plan.read("$.sessions[0].sections[1].id");
        api.post(admin, "/api/admin/sessions/" + sessionId + "/sections", "{\"muscleGroupId\":\"" + group + "\"}")
                .expectCode(409, "MUSCLE_GROUP_ALREADY_IN_SESSION");
        plan = api.put(admin, "/api/admin/sessions/" + sessionId + "/sections/order",
                "{\"ids\":[\"%s\",\"%s\"]}".formatted(second, built.sectionIds().get(0))).expect(200);
        assertThat((String) plan.read("$.sessions[0].sections[0].id")).isEqualTo(second);
        plan = api.delete(admin, "/api/admin/sections/" + second).expect(200);
        assertThat((List<Object>) plan.read("$.sessions[0].sections")).hasSize(1);
        assertThat((Integer) plan.read("$.sessions[0].sections[0].position")).isEqualTo(1);
    }

    @Test
    void failureExerciseShowsMaxAndInvalidRepsAreRejected() {
        BuiltPlan built = factory.executablePlan(admin, "Cedimento", 1, 1, 2);
        UUID sectionId = built.sectionIds().get(0);
        UUID exercise = fixtures.createExercise("Trazioni");
        Api.Response plan = api.post(admin, "/api/admin/sections/" + sectionId + "/exercises",
                PlanFactory.exerciseJson(exercise, 3, 0, true, 120)).expect(201);
        assertThat((Boolean) plan.read("$.sessions[0].sections[0].exercises[1].toFailure")).isTrue();
        assertThat((Integer) plan.read("$.sessions[0].sections[0].exercises[1].reps")).isZero();

        api.post(admin, "/api/admin/sections/" + sectionId + "/exercises",
                PlanFactory.exerciseJson(exercise, 3, 8, true, 120)).expectCode(400, "VALIDATION_ERROR");
        api.post(admin, "/api/admin/sections/" + sectionId + "/exercises",
                PlanFactory.exerciseJson(exercise, 21, 8, false, 120)).expectCode(400, "VALIDATION_ERROR");
        api.post(admin, "/api/admin/sections/" + sectionId + "/exercises",
                PlanFactory.exerciseJson(exercise, 3, 8, false, 601)).expectCode(400, "VALIDATION_ERROR");
    }

    @Test
    void customSetsAreAllOrNothingAndReplacedAtomically() {
        BuiltPlan built = factory.executablePlan(admin, "Personalizzata", 1, 1, 3);
        UUID planExerciseId = built.planExerciseIds().get(0);
        String exerciseId = api.get(admin, "/api/admin/plans/" + built.planId()).read(
                "$.sessions[0].sections[0].exercises[0].exerciseId");
        String custom = """
                {"exerciseId":"%s","setsCount":3,"reps":10,"toFailure":false,"restSeconds":90,
                 "customSets":[{"setIndex":1,"reps":12,"toFailure":false,"restSeconds":60},
                               {"setIndex":2,"reps":10,"toFailure":false,"restSeconds":90},
                               {"setIndex":3,"reps":0,"toFailure":true,"restSeconds":0}]}""".formatted(exerciseId);
        Api.Response plan = api.put(admin, "/api/admin/plan-exercises/" + planExerciseId, custom).expect(200);
        assertThat((Boolean) plan.read("$.sessions[0].sections[0].exercises[0].customized")).isTrue();
        assertThat((List<Integer>) plan.read("$.sessions[0].sections[0].exercises[0].sets[*].reps"))
                .containsExactly(12, 10, 0);
        assertThat(jdbc.queryForObject("select count(*) from plan_sets where plan_exercise_id = ?", Integer.class,
                planExerciseId)).isEqualTo(3);

        // Replacing with the same indexes again works (deferred unique constraint).
        api.put(admin, "/api/admin/plan-exercises/" + planExerciseId, custom.replace("\"reps\":12", "\"reps\":15"))
                .expect(200);

        // Reducing the number of sets without matching custom sets is rejected; nothing changes.
        String partial = custom.replace("\"setsCount\":3", "\"setsCount\":2");
        api.put(admin, "/api/admin/plan-exercises/" + planExerciseId, partial).expectCode(400, "INVALID_CUSTOM_SETS");
        assertThat(jdbc.queryForObject("select count(*) from plan_sets where plan_exercise_id = ?", Integer.class,
                planExerciseId)).isEqualTo(3);

        // Back to general values: rows removed.
        api.put(admin, "/api/admin/plan-exercises/" + planExerciseId,
                PlanFactory.exerciseJson(UUID.fromString(exerciseId), 2, 8, false, 60)).expect(200);
        assertThat(jdbc.queryForObject("select count(*) from plan_sets where plan_exercise_id = ?", Integer.class,
                planExerciseId)).isZero();
    }

    @Test
    void inactiveCatalogItemsCannotBeAddedButStayVisible() {
        BuiltPlan built = factory.executablePlan(admin, "Catalogo", 1, 1, 2);
        String exerciseId = api.get(admin, "/api/admin/plans/" + built.planId())
                .read("$.sessions[0].sections[0].exercises[0].exerciseId");
        api.post(admin, "/api/admin/exercises/" + exerciseId + "/deactivate", null).expect(200);
        Api.Response plan = api.get(admin, "/api/admin/plans/" + built.planId()).expect(200);
        assertThat((Boolean) plan.read("$.sessions[0].sections[0].exercises[0].exerciseActive")).isFalse();
        assertThat((Boolean) plan.read("$.executable")).isTrue();
        // Editing the existing configuration keeping the same exercise is allowed.
        api.put(admin, "/api/admin/plan-exercises/" + built.planExerciseIds().get(0),
                PlanFactory.exerciseJson(UUID.fromString(exerciseId), 4, 6, false, 90)).expect(200);
        api.post(admin, "/api/admin/sections/" + built.sectionIds().get(0) + "/exercises",
                PlanFactory.exerciseJson(UUID.fromString(exerciseId), 3, 8, false, 60))
                .expectCode(422, "CATALOG_ITEM_INACTIVE");
    }

    @Test
    void metadataUpdateUsesOptimisticLocking() {
        UUID planId = factory.createPlan(admin, "Versionata");
        Api.Response plan = api.get(admin, "/api/admin/plans/" + planId);
        int version = plan.read("$.version");
        api.put(admin, "/api/admin/plans/" + planId,
                "{\"name\":\"Nuovo nome\",\"version\":%d}".formatted(version)).expect(200);
        api.put(admin, "/api/admin/plans/" + planId,
                "{\"name\":\"Conflitto\",\"version\":%d}".formatted(version)).expectCode(409, "CONCURRENT_MODIFICATION");
    }

    @Test
    void deleteIsLogicalRestorableAndBlocksEditing() {
        BuiltPlan built = factory.executablePlan(admin, "Da eliminare", 1, 1, 2);
        recorder.events.clear();
        api.delete(admin, "/api/admin/plans/" + built.planId()).expect(204);
        assertThat(recorder.events).containsExactly(new WorkoutPlanEvents.PlanDeleted(built.planId()));
        assertThat(jdbc.queryForObject("select count(*) from workout_plans where id = ?", Integer.class,
                built.planId())).isEqualTo(1);

        Api.Response deleted = api.get(admin, "/api/admin/plans?deleted=true&q=Da eliminare").expect(200);
        assertThat((List<String>) deleted.read("$.content[*].id")).contains(built.planId().toString());
        Api.Response active = api.get(admin, "/api/admin/plans?q=Da eliminare").expect(200);
        assertThat((List<String>) active.read("$.content[*].id")).doesNotContain(built.planId().toString());

        api.post(admin, "/api/admin/plans/" + built.planId() + "/sessions", "{\"title\":\"X\"}")
                .expectCode(422, "PLAN_DELETED");
        Api.Response restored = api.post(admin, "/api/admin/plans/" + built.planId() + "/restore", null).expect(200);
        assertThat((Object) restored.read("$.deletedAt")).isNull();
        assertThat((Boolean) restored.read("$.executable")).isTrue();
    }

    @Test
    void duplicateIsADeepCopyWithProvenance() {
        BuiltPlan built = factory.executablePlan(admin, "Originale", 2, 2, 3);
        String exerciseId = api.get(admin, "/api/admin/plans/" + built.planId())
                .read("$.sessions[0].sections[0].exercises[0].exerciseId");
        api.put(admin, "/api/admin/plan-exercises/" + built.planExerciseIds().get(0), """
                {"exerciseId":"%s","setsCount":2,"reps":10,"toFailure":false,"restSeconds":90,
                 "customSets":[{"setIndex":1,"reps":12,"toFailure":false,"restSeconds":60},
                               {"setIndex":2,"reps":0,"toFailure":true,"restSeconds":0}]}""".formatted(exerciseId))
                .expect(200);

        Api.Response copy = api.post(admin, "/api/admin/plans/" + built.planId() + "/duplicate", null).expect(201);
        assertThat((String) copy.read("$.name")).isEqualTo("Originale (copia)");
        assertThat((String) copy.read("$.copiedFromPlanId")).isEqualTo(built.planId().toString());
        assertThat((List<String>) copy.read("$.sessions[*].title")).containsExactly("Giorno 1", "Giorno 2");
        assertThat((String) copy.read("$.sessions[0].id")).isNotEqualTo(built.sessionIds().get(0).toString());
        assertThat((List<Integer>) copy.read("$.sessions[0].sections[0].exercises[0].sets[*].reps"))
                .containsExactly(12, 0);
        assertThat((Boolean) copy.read("$.sessions[0].sections[0].exercises[0].customized")).isTrue();
        assertThat((List<Object>) copy.read("$.sessions[1].sections[0].exercises")).hasSize(2);

        // Editing the copy leaves the original untouched.
        String copySession = copy.read("$.sessions[0].id");
        api.delete(admin, "/api/admin/sessions/" + copySession).expect(200);
        assertThat((List<Object>) api.get(admin, "/api/admin/plans/" + built.planId()).read("$.sessions")).hasSize(2);
    }

    @Test
    void usersCannotAccessAdminPlanEndpoints() {
        AuthenticatedUser user = fixtures.createUser();
        UUID planId = factory.createPlan(admin, "Privata");
        api.get(user, "/api/admin/plans").expectCode(403, "FORBIDDEN");
        api.get(user, "/api/admin/plans/" + planId).expectCode(403, "FORBIDDEN");
        api.post(user, "/api/admin/plans", "{\"name\":\"x\"}").expectCode(403, "FORBIDDEN");
    }

    @Test
    void unknownElementsReturn404() {
        api.get(admin, "/api/admin/plans/" + UUID.randomUUID()).expectCode(404, "NOT_FOUND");
        api.delete(admin, "/api/admin/sessions/" + UUID.randomUUID()).expectCode(404, "NOT_FOUND");
        api.put(admin, "/api/admin/plan-exercises/" + UUID.randomUUID(),
                PlanFactory.exerciseJson(UUID.randomUUID(), 3, 10, false, 60)).expectCode(404, "NOT_FOUND");
    }
}
