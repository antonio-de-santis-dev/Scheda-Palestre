package com.gymplanner.support;

import com.gymplanner.shared.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;

/** Builds plans through the real ADMIN API. */
@TestComponent
public class PlanFactory {

    private final Api api;
    private final TestFixtures fixtures;

    public PlanFactory(Api api, TestFixtures fixtures) {
        this.api = api;
        this.fixtures = fixtures;
    }

    /** Result of {@link #executablePlan}: ids in rotation order. */
    public record BuiltPlan(UUID planId, List<UUID> sessionIds, List<UUID> sectionIds, List<UUID> planExerciseIds) {
    }

    public UUID createPlan(AuthenticatedUser admin, String name) {
        String id = api.post(admin, "/api/admin/plans", "{\"name\":\"" + name + "\"}").expect(201).read("$.id");
        return UUID.fromString(id);
    }

    /**
     * Plan with {@code sessions} sessions ("Giorno 1".."Giorno N"); each has one muscle section
     * with {@code exercisesPerSession} exercises of {@code sets} sets x 10 reps, 60s rest.
     */
    public BuiltPlan executablePlan(AuthenticatedUser admin, String name, int sessions, int exercisesPerSession,
            int sets) {
        UUID planId = createPlan(admin, name);
        List<UUID> sessionIds = new ArrayList<>();
        List<UUID> sectionIds = new ArrayList<>();
        List<UUID> planExerciseIds = new ArrayList<>();
        for (int s = 1; s <= sessions; s++) {
            Api.Response plan = api.post(admin, "/api/admin/plans/" + planId + "/sessions",
                    "{\"title\":\"Giorno " + s + "\"}").expect(201);
            UUID sessionId = UUID.fromString(plan.read("$.sessions[" + (s - 1) + "].id"));
            sessionIds.add(sessionId);
            UUID group = fixtures.createMuscleGroup("Gruppo " + s);
            plan = api.post(admin, "/api/admin/sessions/" + sessionId + "/sections",
                    "{\"muscleGroupId\":\"" + group + "\"}").expect(201);
            UUID sectionId = UUID.fromString(plan.read("$.sessions[" + (s - 1) + "].sections[0].id"));
            sectionIds.add(sectionId);
            for (int e = 1; e <= exercisesPerSession; e++) {
                UUID exercise = fixtures.createExercise("Esercizio " + s + "." + e);
                plan = api.post(admin, "/api/admin/sections/" + sectionId + "/exercises", exerciseJson(exercise, sets, 10,
                        false, 60)).expect(201);
                planExerciseIds.add(UUID.fromString(
                        plan.read("$.sessions[" + (s - 1) + "].sections[0].exercises[" + (e - 1) + "].id")));
            }
        }
        return new BuiltPlan(planId, sessionIds, sectionIds, planExerciseIds);
    }

    public static String exerciseJson(UUID exerciseId, int sets, int reps, boolean toFailure, int rest) {
        return """
                {"exerciseId":"%s","setsCount":%d,"reps":%d,"toFailure":%b,"restSeconds":%d,"customSets":[]}"""
                .formatted(exerciseId, sets, reps, toFailure, rest);
    }
}
