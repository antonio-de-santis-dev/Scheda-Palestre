package com.gymplanner.identity.internal;

import com.gymplanner.shared.error.ApiException;
import org.springframework.http.HttpStatus;

class RecoveryUnavailableException extends ApiException {
    RecoveryUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RECOVERY_UNAVAILABLE", "Password recovery is unavailable", java.util.List.of());
    }
}
