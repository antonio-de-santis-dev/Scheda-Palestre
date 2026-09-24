package com.gymplanner.calendar.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface WeeklyScheduleRepository extends JpaRepository<WeeklySchedule, UUID> {

    List<WeeklySchedule> findByPlanAssignmentIdOrderByWeekday(UUID planAssignmentId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from WeeklySchedule w where w.planAssignmentId = :assignmentId")
    int deleteByAssignment(@Param("assignmentId") UUID assignmentId);
}
