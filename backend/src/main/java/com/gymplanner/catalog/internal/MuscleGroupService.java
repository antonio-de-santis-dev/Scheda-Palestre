package com.gymplanner.catalog.internal;

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

@Service
class MuscleGroupService extends CatalogService<MuscleGroup> {

    private final MuscleGroupRepository repository;
    private final ExerciseRepository exercises;

    MuscleGroupService(MuscleGroupRepository repository, ExerciseRepository exercises) {
        super(repository, "Muscle group");
        this.repository = repository;
        this.exercises = exercises;
    }

    @Transactional(readOnly = true)
    public Page<MuscleGroup> search(String q, Boolean active, Pageable pageable) {
        return repository.search(Paging.likePattern(q), active, pageable);
    }

    @Transactional
    public MuscleGroup create(String name) {
        return insert(new MuscleGroup(normalize(name)));
    }

    /** Exercise counters of the given groups (one query); missing groups have no exercises. */
    @Transactional(readOnly = true)
    public Map<UUID, ExerciseRepository.GroupCount> exerciseCounts(Collection<UUID> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return exercises.countByGroup(groupIds).stream()
                .collect(Collectors.toMap(ExerciseRepository.GroupCount::getGroupId, Function.identity()));
    }
}
