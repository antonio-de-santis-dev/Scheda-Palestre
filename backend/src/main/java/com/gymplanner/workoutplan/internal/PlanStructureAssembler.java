package com.gymplanner.workoutplan.internal;

import com.gymplanner.catalog.api.CatalogItemView;
import com.gymplanner.catalog.api.CatalogLookup;
import com.gymplanner.workoutplan.api.PlanStructure;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds {@link PlanStructure} from the aggregate, resolving catalog names with two bulk
 * queries (no N+1: children are loaded with Hibernate batch fetching).
 */
@Component
class PlanStructureAssembler {

    private final CatalogLookup catalog;

    PlanStructureAssembler(CatalogLookup catalog) {
        this.catalog = catalog;
    }

    PlanStructure assemble(WorkoutPlan plan) {
        Set<UUID> groupIds = new HashSet<>();
        Set<UUID> exerciseIds = new HashSet<>();
        for (PlanSession session : plan.getSessions()) {
            for (MuscleSection section : session.getSections()) {
                groupIds.add(section.getMuscleGroupId());
                for (PlanExercise exercise : section.getExercises()) {
                    exerciseIds.add(exercise.getExerciseId());
                }
            }
        }
        Map<UUID, CatalogItemView> groups = catalog.muscleGroups(groupIds);
        Map<UUID, CatalogItemView> exercises = catalog.exercises(exerciseIds);

        List<PlanStructure.Session> sessions = sorted(plan.getSessions()).stream()
                .map(session -> new PlanStructure.Session(session.getId(), session.getTitle(), session.getPosition(),
                        sorted(session.getSections()).stream()
                                .map(section -> section(section, groups, exercises))
                                .toList()))
                .toList();
        return new PlanStructure(plan.getId(), plan.getName(), plan.getDescription(), plan.getExpiresOn(),
                plan.getCreatedBy(), plan.getCopiedFromPlanId(), plan.getCreatedAt(), plan.getUpdatedAt(),
                plan.getDeletedAt(), plan.getVersion(), isExecutable(plan), sessions);
    }

    private static PlanStructure.Section section(MuscleSection section, Map<UUID, CatalogItemView> groups,
            Map<UUID, CatalogItemView> exercises) {
        CatalogItemView group = groups.get(section.getMuscleGroupId());
        return new PlanStructure.Section(section.getId(), section.getMuscleGroupId(),
                group == null ? "?" : group.name(), group != null && group.active(), section.getPosition(),
                sorted(section.getExercises()).stream().map(e -> exercise(e, exercises)).toList());
    }

    private static PlanStructure.Exercise exercise(PlanExercise e, Map<UUID, CatalogItemView> exercises) {
        CatalogItemView item = exercises.get(e.getExerciseId());
        return new PlanStructure.Exercise(e.getId(), e.getExerciseId(), item == null ? "?" : item.name(),
                item != null && item.active(), e.getPosition(), e.getSetsCount(), e.getReps(), e.isToFailure(),
                e.getRestSeconds(), e.isCustomized(),
                e.effectiveSets().stream()
                        .map(s -> new PlanStructure.Set(s.setIndex(), s.reps(), s.toFailure(), s.restSeconds()))
                        .toList());
    }

    /** In-memory order may be stale right after a reorder in the same transaction. */
    private static <T extends Positioned> List<T> sorted(List<T> items) {
        return items.stream().sorted(java.util.Comparator.comparingInt(Positioned::getPosition)).toList();
    }

    /**
     * Spec 8.5: not deleted, at least one session, every session with at least one exercise,
     * every exercise coherently configured (custom sets complete).
     */
    static boolean isExecutable(WorkoutPlan plan) {
        if (plan.isDeleted() || plan.getSessions().isEmpty()) {
            return false;
        }
        for (PlanSession session : plan.getSessions()) {
            if (session.exerciseCount() == 0) {
                return false;
            }
            for (MuscleSection section : session.getSections()) {
                for (PlanExercise exercise : section.getExercises()) {
                    if (exercise.isCustomized() && exercise.getSets().size() != exercise.getSetsCount()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
