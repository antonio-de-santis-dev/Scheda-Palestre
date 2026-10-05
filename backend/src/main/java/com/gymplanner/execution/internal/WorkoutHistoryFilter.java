package com.gymplanner.execution.internal;

import com.gymplanner.shared.error.BadRequestException;
import com.gymplanner.shared.web.Paging;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Calendar dates and historical snapshot names; ownership is always part of the query. */
record WorkoutHistoryFilter(LocalDate from, LocalDate to, WorkoutStatus status, String q) {
    WorkoutHistoryFilter {
        q = Paging.query(q);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("INVALID_HISTORY_FILTER", "Start date must not follow end date");
        }
        if (q != null && q.length() > 100) {
            throw new BadRequestException("INVALID_HISTORY_FILTER", "Workout name must not exceed 100 characters");
        }
    }

    record SqlCriteria(String where, List<Object> args) {
    }

    SqlCriteria sqlOwnedBy(UUID userId) {
        var args = new ArrayList<Object>();
        args.add(userId);
        StringBuilder where = new StringBuilder("user_id = ?");
        if (from != null) { where.append(" and scheduled_date >= ?"); args.add(from); }
        if (to != null) { where.append(" and scheduled_date <= ?"); args.add(to); }
        if (status != null) { where.append(" and status = ?"); args.add(status.name()); }
        if (q != null) {
            where.append(" and (lower(plan_name_snapshot) like ? escape '\\' or lower(session_title_snapshot) like ? escape '\\')");
            args.add(Paging.likePattern(q)); args.add(Paging.likePattern(q));
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
            if (q != null) {
                String pattern = Paging.likePattern(q);
                predicates.add(cb.or(cb.like(cb.lower(root.get("planNameSnapshot")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sessionTitleSnapshot")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
