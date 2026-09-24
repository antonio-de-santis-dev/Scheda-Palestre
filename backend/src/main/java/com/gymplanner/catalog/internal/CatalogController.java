package com.gymplanner.catalog.internal;

import com.gymplanner.shared.web.PageResponse;
import com.gymplanner.shared.web.Paging;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shared REST shape of both catalogs (spec 13.3). */
abstract class CatalogController<T extends CatalogItem> {

    private static final Sort SORT = Sort.by("name");

    private final CatalogService<T> service;
    private final String basePath;

    protected CatalogController(CatalogService<T> service, String basePath) {
        this.service = service;
        this.basePath = basePath;
    }

    @GetMapping
    PageResponse<CatalogItemResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return PageResponse.of(service.search(q, active, Paging.of(page, size, SORT)), CatalogItemResponse::of);
    }

    @PostMapping
    ResponseEntity<CatalogItemResponse> create(@Valid @RequestBody CatalogItemRequest body) {
        T item = service.create(body.name());
        return ResponseEntity.created(URI.create(basePath + "/" + item.getId())).body(CatalogItemResponse.of(item));
    }

    @PutMapping("/{id}")
    CatalogItemResponse rename(@PathVariable UUID id, @Valid @RequestBody CatalogItemRequest body) {
        return CatalogItemResponse.of(service.rename(id, body.name()));
    }

    @PostMapping("/{id}/activate")
    CatalogItemResponse activate(@PathVariable UUID id) {
        return CatalogItemResponse.of(service.setActive(id, true));
    }

    @PostMapping("/{id}/deactivate")
    CatalogItemResponse deactivate(@PathVariable UUID id) {
        return CatalogItemResponse.of(service.setActive(id, false));
    }
}
