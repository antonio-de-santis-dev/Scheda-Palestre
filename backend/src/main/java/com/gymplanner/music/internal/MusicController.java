package com.gymplanner.music.internal;

import com.gymplanner.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Personal YouTube links of the USER; the owner always comes from the session. */
@RestController
@RequestMapping("/api/me/music")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class MusicController {

    record Request(@NotBlank @Size(max = 120) String title, @NotBlank @Size(max = 2048) String url) {
    }

    private final MusicService service;

    @GetMapping
    List<MusicService.Entry> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MusicService.Entry add(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody Request body) {
        return service.add(user.id(), body.title(), body.url());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
    }
}
