package com.gymplanner.catalog.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/** Queries shared by both catalog entities ({@code #{#entityName}} resolves to the concrete type). */
@NoRepositoryBean
interface CatalogRepository<T extends CatalogItem> extends JpaRepository<T, UUID> {

    @Query("""
            select c from #{#entityName} c
            where lower(c.name) like :pattern escape '\\'
              and (:active is null or c.active = :active)
            """)
    Page<T> search(@Param("pattern") String pattern, @Param("active") Boolean active, Pageable pageable);

    @Query("select count(c) > 0 from #{#entityName} c where lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
    boolean existsName(@Param("name") String name, @Param("excludeId") UUID excludeId);

    List<T> findByIdIn(Collection<UUID> ids);
}

interface MuscleGroupRepository extends CatalogRepository<MuscleGroup> {
}

interface ExerciseRepository extends CatalogRepository<Exercise> {
}
