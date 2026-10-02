package com.gymplanner.calendar.internal;

import com.gymplanner.assignment.api.ScheduleCopyPort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR 0008: copies only the free days of the user's previous plan; days already used by another
 * active plan are skipped and reported. Runs in the activation transaction, under the user lock.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class ScheduleCopyAdapter implements ScheduleCopyPort {

    private final WeeklyScheduleRepository schedules;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CopyResult copyFreeDays(UUID sourceAssignmentId, UUID targetAssignmentId,
            Collection<UUID> otherActiveAssignmentIds) {
        if (!schedules.findByPlanAssignmentIdOrderByWeekday(targetAssignmentId).isEmpty()) {
            return CopyResult.NONE;
        }
        Set<Integer> occupied = otherActiveAssignmentIds.isEmpty() ? Set.of()
                : schedules.findByPlanAssignmentIdIn(otherActiveAssignmentIds).stream()
                        .map(WeeklySchedule::getWeekday).collect(Collectors.toSet());
        List<Integer> copied = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();
        Set<Integer> source = new TreeSet<>();
        schedules.findByPlanAssignmentIdOrderByWeekday(sourceAssignmentId).forEach(s -> source.add(s.getWeekday()));
        for (Integer day : source) {
            if (occupied.contains(day)) {
                skipped.add(day);
            } else {
                schedules.save(new WeeklySchedule(targetAssignmentId, day));
                copied.add(day);
            }
        }
        schedules.flush();
        return new CopyResult(List.copyOf(copied), List.copyOf(skipped));
    }
}
