package com.gymplanner.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RecoveryController {
    record Forgot(@NotBlank @Email @Size(max=255) String email) {}
    record Reset(@NotBlank @Size(max=128) String token, @NotBlank @Size(max=128) String newPassword) {}
    private final RecoveryService recovery;
    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgot(@Valid @RequestBody Forgot body, HttpServletRequest request) {
        recovery.request(body.email(), request.getRemoteAddr());
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/reset-password")
    ResponseEntity<Void> reset(@Valid @RequestBody Reset body) {
        recovery.reset(body.token(), body.newPassword());
        return ResponseEntity.noContent().build();
    }
}
