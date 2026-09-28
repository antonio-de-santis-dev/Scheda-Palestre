package com.gymplanner.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ExerciseRepository extends CatalogRepository<Exercise> {

    @Query("""
            select e from Exercise e
            where lower(e.name) like :pattern escape '\\'
              and (:active is null or e.active = :active)
              and (:groupId is null or e.muscleGroupId = :groupId)
            """)
    Page<Exercise> search(@Param("pattern") String pattern, @Param("active") Boolean active,
            @Param("groupId") UUID groupId, Pageable pageable);

    /** Per-group counters for the master list, in one query. */
    @Query("""
            select e.muscleGroupId as groupId, count(e) as total,
                   sum(case when e.active = true then 1 else 0 end) as active
            from Exercise e where e.muscleGroupId in :groupIds group by e.muscleGroupId
            """)
    List<GroupCount> countByGroup(@Param("groupIds") Collection<UUID> groupIds);

    interface GroupCount {
        UUID getGroupId();

        long getTotal();

        long getActive();
    }
}
