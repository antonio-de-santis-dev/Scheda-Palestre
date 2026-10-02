package com.gymplanner.execution.internal;
import com.gymplanner.shared.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me/push")
@RequiredArgsConstructor(access=AccessLevel.PACKAGE)
class PushController {
    record Config(boolean enabled, String publicKey) {}
    record Unsubscribe(String endpoint) {}
    private final PushProperties properties;
    private final PushService service;
    @GetMapping("/config") Config config() { return new Config(properties.configured(), properties.configured() ? properties.getPublicKey() : null); }
    @PostMapping("/subscribe") ResponseEntity<Void> subscribe(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody PushService.Subscription body) {
        service.subscribe(user, body); return ResponseEntity.noContent().build();
    }
    @PostMapping("/unsubscribe") ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody Unsubscribe body) {
        service.unsubscribe(user, body.endpoint()); return ResponseEntity.noContent().build();
    }
}
