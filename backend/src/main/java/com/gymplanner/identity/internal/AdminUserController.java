package com.gymplanner.identity.internal;

import com.gymplanner.identity.api.UserRole;
import com.gymplanner.identity.internal.AdminUserDtos.UserRequest;
import com.gymplanner.identity.internal.AdminUserDtos.UserResponse;
import com.gymplanner.identity.internal.AdminUserDtos.UserWithPasswordResponse;
import com.gymplanner.shared.security.AuthenticatedUser;
import com.gymplanner.shared.web.PageResponse;
import com.gymplanner.shared.web.Paging;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

    private static final Sort SORT = Sort.by("lastName", "firstName", "username");

    private final AdminUserService service;
    private final Clock clock;

    AdminUserController(AdminUserService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    PageResponse<UserResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) UserRole role, @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        var now = clock.instant();
        return PageResponse.of(service.search(q, role, active, Paging.of(page, size, SORT)),
                u -> UserResponse.of(u, now));
    }

    @PostMapping
    ResponseEntity<UserWithPasswordResponse> create(@Valid @RequestBody UserRequest body) {
        var created = service.create(body);
        return ResponseEntity.created(URI.create("/api/admin/users/" + created.user().getId()))
                .body(new UserWithPasswordResponse(UserResponse.of(created.user(), clock.instant()),
                        created.temporaryPassword()));
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable UUID id) {
        return UserResponse.of(service.get(id), clock.instant());
    }

    @PutMapping("/{id}")
    UserResponse update(@PathVariable UUID id, @Valid @RequestBody UserRequest body) {
        return UserResponse.of(service.update(id, body), clock.instant());
    }

    @PostMapping("/{id}/activate")
    UserResponse activate(@PathVariable UUID id) {
        return UserResponse.of(service.activate(id), clock.instant());
    }

    @PostMapping("/{id}/deactivate")
    UserResponse deactivate(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser admin) {
        return UserResponse.of(service.deactivate(id, admin.id()), clock.instant());
    }

    @PostMapping("/{id}/reset-password")
    UserWithPasswordResponse resetPassword(@PathVariable UUID id) {
        var reset = service.resetPassword(id);
        return new UserWithPasswordResponse(UserResponse.of(reset.user(), clock.instant()), reset.temporaryPassword());
    }
}
