package com.gymplanner.assignment.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Port implemented by the calendar module (dependency inversion: assignment must not depend on
 * calendar). Used when an ADMIN activates a plan with {@code copySchedule = true}.
 */
public interface ScheduleCopyPort {

    /**
     * Copies to {@code targetAssignmentId} the weekdays of {@code sourceAssignmentId} that are not
     * used by {@code otherActiveAssignmentIds} (the other active plans of the same user); occupied
     * days are never copied (ADR 0008). Must run inside the caller's transaction, after the user lock.
     */
    CopyResult copyFreeDays(UUID sourceAssignmentId, UUID targetAssignmentId, Collection<UUID> otherActiveAssignmentIds);

    record CopyResult(List<Integer> copied, List<Integer> skipped) {

        public static final CopyResult NONE = new CopyResult(List.of(), List.of());
    }
}
