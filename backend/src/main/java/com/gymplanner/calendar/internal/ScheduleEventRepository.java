package com.gymplanner.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScheduleEventRepository extends JpaRepository<ScheduleEvent, UUID> {

    List<ScheduleEvent> findByPlanAssignmentIdIn(Collection<UUID> planAssignmentIds);

    List<ScheduleEvent> findByPlanAssignmentIdOrderByOriginalDateDesc(UUID planAssignmentId);

    Optional<ScheduleEvent> findByPlanAssignmentIdAndOriginalDate(UUID planAssignmentId, java.time.LocalDate originalDate);

    Optional<ScheduleEvent> findByIdAndPlanAssignmentId(UUID id, UUID planAssignmentId);
}
