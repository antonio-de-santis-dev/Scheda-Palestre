package com.gymplanner.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Clamps client supplied paging parameters to safe bounds. */
public final class Paging {

    public static final int MAX_SIZE = 200;

    private Paging() {
    }

    public static PageRequest of(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, MAX_SIZE);
        return PageRequest.of(p, s, sort);
    }

    /** Normalises a free-text search: {@code null} when blank, otherwise trimmed. */
    public static String query(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    /**
     * Lower-case {@code LIKE} pattern ("contains") with {@code \} as escape character. A blank
     * query yields {@code %}, so repository queries never receive a {@code null} parameter.
     */
    public static String likePattern(String q) {
        String normalized = query(q);
        if (normalized == null) {
            return "%";
        }
        String escaped = normalized.toLowerCase(java.util.Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
