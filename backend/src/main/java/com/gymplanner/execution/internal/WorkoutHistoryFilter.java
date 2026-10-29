package com.gymplanner.execution.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.web.Paging;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Calendar dates, historical snapshot names and (Task 4) the plan selected in the history;
 * ownership is always part of the query.
 *
 * @param q             case-insensitive text matched literally in plan, session or exercise names
 * @param assignmentIds assignments of the selected plan; null = every plan, empty = none
 */
record WorkoutHistoryFilter(LocalDate from, LocalDate to, WorkoutStatus status, String q, List<UUID> assignmentIds) {
    WorkoutHistoryFilter {
        q = Paging.query(q);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("INVALID_HISTORY_FILTER", "Start date must not follow end date");
        }
        if (q != null && q.length() > 100) {
            throw new BadRequestException("INVALID_HISTORY_FILTER", "Workout name must not exceed 100 characters");
        }
        assignmentIds = assignmentIds == null ? null : List.copyOf(assignmentIds);
    }

    WorkoutHistoryFilter(LocalDate from, LocalDate to, WorkoutStatus status, String q) {
        this(from, to, status, q, null);
    }

    WorkoutHistoryFilter forAssignments(Collection<UUID> ids) {
        return new WorkoutHistoryFilter(from, to, status, q, List.copyOf(ids));
    }

    record SqlCriteria(String where, List<Object> args) {
    }

    /** WHERE clause on the {@code workouts} table (unaliased), built from constant fragments only. */
    SqlCriteria sqlOwnedBy(UUID userId) {
        var args = new ArrayList<Object>();
        args.add(userId);
        StringBuilder where = new StringBuilder("user_id = ?");
        if (from != null) { where.append(" and scheduled_date >= ?"); args.add(from); }
        if (to != null) { where.append(" and scheduled_date <= ?"); args.add(to); }
        if (status != null) { where.append(" and status = ?"); args.add(status.name()); }
        if (assignmentIds != null) {
            if (assignmentIds.isEmpty()) {
                where.append(" and false");
            } else {
                where.append(" and plan_assignment_id in (").append("?,".repeat(assignmentIds.size() - 1)).append("?)");
                args.addAll(assignmentIds);
            }
        }
        if (q != null) {
            String pattern = Paging.likePattern(q);
            where.append(" and (lower(plan_name_snapshot) like ? escape '\\' or lower(session_title_snapshot) like ? escape '\\'")
                    .append(" or exists (select 1 from workout_exercises we where we.workout_id = workouts.id")
                    .append(" and lower(we.exercise_name_snapshot) like ? escape '\\'))");
            args.add(pattern); args.add(pattern); args.add(pattern);
        }
        return new SqlCriteria(where.toString(), List.copyOf(args));
    }

    Specification<Workout> ownedBy(UUID userId) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDate"), to));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (assignmentIds != null) {
                predicates.add(assignmentIds.isEmpty() ? cb.disjunction() : root.get("planAssignmentId").in(assignmentIds));
            }
            if (q != null) {
                String pattern = Paging.likePattern(q);
                var exercises = query.subquery(Integer.class);
                var exercise = exercises.from(WorkoutExercise.class);
                exercises.select(cb.literal(1)).where(cb.equal(exercise.get("workout"), root),
                        cb.like(cb.lower(exercise.get("exerciseNameSnapshot")), pattern, '\\'));
                predicates.add(cb.or(cb.like(cb.lower(root.get("planNameSnapshot")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sessionTitleSnapshot")), pattern, '\\'), cb.exists(exercises)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
