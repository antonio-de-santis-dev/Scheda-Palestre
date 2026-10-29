package com.gymplanner.calendar.internal;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ScheduleTrackingRepository extends JpaRepository<ScheduleTracking, UUID> {

    List<ScheduleTracking> findByPlanAssignmentIdIn(Collection<UUID> planAssignmentIds);
}
