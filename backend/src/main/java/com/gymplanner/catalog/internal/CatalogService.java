package com.gymplanner.catalog.internal;

import com.gymplanner.catalog.api.CatalogItemView;
import com.gymplanner.shared.error.BusinessRuleException;
import com.gymplanner.shared.error.ConflictException;
import com.gymplanner.shared.error.FieldViolation;
import com.gymplanner.shared.error.NotFoundException;
import com.gymplanner.shared.web.Paging;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic management of a catalog (US-04, US-05). Names are unique case-insensitively; items
 * are deactivated instead of deleted.
 */
abstract class CatalogService<T extends CatalogItem> {

    private final CatalogRepository<T> repository;
    private final String resourceName;
    private final Function<String, T> factory;

    protected CatalogService(CatalogRepository<T> repository, String resourceName, Function<String, T> factory) {
        this.repository = repository;
        this.resourceName = resourceName;
        this.factory = factory;
    }

    @Transactional(readOnly = true)
    public Page<T> search(String q, Boolean active, Pageable pageable) {
        return repository.search(Paging.likePattern(q), active, pageable);
    }

    @Transactional(readOnly = true)
    public T get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException(resourceName));
    }

    @Transactional
    public T create(String name) {
        String normalized = normalize(name);
        ensureUnique(normalized, null);
        return saveUnique(factory.apply(normalized));
    }

    @Transactional
    public T rename(UUID id, String name) {
        T item = get(id);
        String normalized = normalize(name);
        ensureUnique(normalized, id);
        item.rename(normalized);
        return saveUnique(item);
    }

    @Transactional
    public T setActive(UUID id, boolean active) {
        T item = get(id);
        item.setActive(active);
        return item;
    }

    @Transactional(readOnly = true)
    public Map<UUID, CatalogItemView> views(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return repository.findByIdIn(ids).stream()
                .map(CatalogService::view)
                .collect(Collectors.toMap(CatalogItemView::id, Function.identity()));
    }

    @Transactional(readOnly = true)
    public CatalogItemView requireSelectable(UUID id) {
        T item = get(id);
        if (!item.isActive()) {
            throw new BusinessRuleException("CATALOG_ITEM_INACTIVE",
                    resourceName + " is deactivated and cannot be used in new configurations");
        }
        return view(item);
    }

    static CatalogItemView view(CatalogItem item) {
        return new CatalogItemView(item.getId(), item.getName(), item.isActive());
    }

    private T saveUnique(T item) {
        try {
            return repository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            // Concurrent insert of the same name between the check and the flush.
            throw nameTaken();
        }
    }

    private void ensureUnique(String name, UUID excludeId) {
        if (repository.existsName(name, excludeId)) {
            throw nameTaken();
        }
    }

    private ConflictException nameTaken() {
        return new ConflictException("NAME_TAKEN", resourceName + " name already in use",
                List.of(new FieldViolation("name", "Name is already in use")));
    }

    /** Trims and collapses internal whitespace so "Panca  piana" and "Panca piana" collide. */
    static String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }
}
