package com.gymplanner.catalog.internal;

import com.gymplanner.catalog.api.ExerciseView;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.web.Paging;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.FieldViolation;
import java.util.List;

/** Exercises always belong to one muscle group (ADR 0007). */
@Service
class ExerciseService extends CatalogService<Exercise> {

    private final ExerciseRepository repository;
    private final MuscleGroupService groups;

    ExerciseService(ExerciseRepository repository, MuscleGroupService groups) {
        super(repository, "Exercise");
        this.repository = repository;
        this.groups = groups;
    }

    @Transactional(readOnly = true)
    public Page<Exercise> search(String q, Boolean active, UUID muscleGroupId, Pageable pageable) {
        return repository.search(Paging.likePattern(q), active, muscleGroupId, pageable);
    }

    /** A new exercise can only be created in an active group. */
    @Transactional
    public Exercise create(String name, UUID muscleGroupId) {
        groups.requireSelectable(muscleGroupId);

        String normalized = normalize(name);
        ensureUniqueInGroup(muscleGroupId, normalized, null);

        return saveUnique(new Exercise(normalized, muscleGroupId));
    }

    /**
     * Renames and optionally moves the exercise to another (active) group. Plans that already use
     * it under the previous group are not changed: they keep a tolerated "historic" mismatch.
     */
    @Transactional
    public Exercise update(UUID id, String name, UUID muscleGroupId) {
        Exercise exercise = get(id);

        UUID targetGroupId = muscleGroupId != null
                ? muscleGroupId
                : exercise.getMuscleGroupId();

        if (!targetGroupId.equals(exercise.getMuscleGroupId())) {
            groups.requireSelectable(targetGroupId);
        }

        String normalized = normalize(name);
        ensureUniqueInGroup(targetGroupId, normalized, id);

        exercise.rename(normalized);
        exercise.moveTo(targetGroupId);

        return saveUnique(exercise);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ExerciseView> exerciseViews(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdIn(ids).stream()
                .map(ExerciseService::exerciseView)
                .collect(Collectors.toMap(ExerciseView::id, Function.identity()));
    }

    @Transactional(readOnly = true)
    public ExerciseView requireSelectable(UUID id, UUID muscleGroupId) {
        requireSelectable(id);
        Exercise exercise = get(id);
        if (!exercise.getMuscleGroupId().equals(muscleGroupId)) {
            String groupName = groups.get(muscleGroupId).getName();
            throw new BusinessRuleException("EXERCISE_GROUP_MISMATCH",
                    "The exercise does not belong to the muscle group of the section")
                    .with("exerciseId", exercise.getId())
                    .with("exerciseName", exercise.getName())
                    .with("muscleGroupId", muscleGroupId)
                    .with("muscleGroupName", groupName)
                    .with("exerciseMuscleGroupId", exercise.getMuscleGroupId());
        }
        return exerciseView(exercise);
    }

    static ExerciseView exerciseView(Exercise e) {
        return new ExerciseView(e.getId(), e.getName(), e.isActive(), e.getMuscleGroupId());
    }

    @Override
    @Transactional
    public Exercise rename(UUID id, String name) {
        return update(id, name, null);
    }

    private void ensureUniqueInGroup(
            UUID muscleGroupId,
            String name,
            UUID excludeId) {

        if (repository.existsNameInGroup(
                muscleGroupId, name, excludeId)) {

            throw new ConflictException(
                    "NAME_TAKEN",
                    "Exercise name already in use in this muscle group",
                    List.of(new FieldViolation(
                            "name",
                            "Il nome è già in uso in questo gruppo muscolare")));
        }
    }
}
