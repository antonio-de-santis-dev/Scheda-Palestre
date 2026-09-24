package com.gymplanner.workoutplan.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response bodies of {@code /api/admin/plans/**} and structure endpoints. */
final class PlanDtos {

    private PlanDtos() {
    }

    record CreatePlanRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description,
            LocalDate expiresOn) {
    }

    /** {@code version} enables optimistic locking on shared plans (spec 8.5 / 21). */
    record UpdatePlanRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description,
            LocalDate expiresOn,
            @NotNull Long version) {
    }

    record SessionRequest(@NotBlank @Size(max = 60) String title) {
    }

    record SectionRequest(@NotNull UUID muscleGroupId) {
    }

    record OrderRequest(@NotNull @Size(max = 500) List<@NotNull UUID> ids) {
    }

    record SetRequest(
            @Min(1) @Max(20) int setIndex,
            @Min(0) @Max(100) int reps,
            boolean toFailure,
            @Min(0) @Max(600) int restSeconds) {
    }

    record PlanExerciseRequest(
            @NotNull UUID exerciseId,
            @Min(1) @Max(20) int setsCount,
            @Min(0) @Max(100) int reps,
            boolean toFailure,
            @Min(0) @Max(600) int restSeconds,
            @Valid @Size(max = 20) List<SetRequest> customSets) {
    }

    record PlanListItem(UUID id, String name, String description, LocalDate expiresOn, int sessionCount,
            boolean executable, UUID copiedFromPlanId, Instant createdAt, Instant updatedAt, Instant deletedAt) {
    }
}
