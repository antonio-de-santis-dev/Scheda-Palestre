package com.gymplanner.execution.internal;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (path under /api/admin, checked by the security configuration). */
@RestController
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ActivityReportController {

    private final ActivityReportService service;

    @GetMapping("/api/admin/users/{userId}/activity-report")
    ActivityReportService.ActivityReport report(@PathVariable UUID userId) {
        return service.report(userId);
    }
}
