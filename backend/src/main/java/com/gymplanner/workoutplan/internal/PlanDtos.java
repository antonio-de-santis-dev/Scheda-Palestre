package com.gymplanner.workoutplan.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Request/response bodies of {@code /api/admin/plans/**} and structure endpoints. */
final class PlanDtos {

    private PlanDtos() {
    }

    record CreatePlanRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description,
            @Min(1) @Max(520) Integer durationWeeks) {
    }

    /** {@code version} enables optimistic locking on shared plans (spec 8.5 / 21). */
    record UpdatePlanRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description,
            @Min(1) @Max(520) Integer durationWeeks,
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
            @Valid @Size(max = 20) List<@NotNull SetRequest> customSets,
            /** Recovery before the next exercise, 0-1200 s (0 = none); null = last set's rest. */
            @Min(0) @Max(1200) Integer restAfterSeconds) {
    }

    /** {@code sessionTitles}: names chosen by the ADMIN, in rotation order (Task 2). */
    record PlanListItem(UUID id, String name, String description, Integer durationWeeks, int sessionCount,
            boolean executable, UUID copiedFromPlanId, Instant createdAt, Instant updatedAt, Instant deletedAt,
            java.util.List<String> sessionTitles) {
    }
}
