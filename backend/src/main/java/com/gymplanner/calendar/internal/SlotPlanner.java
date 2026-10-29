package com.gymplanner.calendar.internal;

import com.gymplanner.calendar.api.RotationCalculator;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Rotation over <em>effective slots</em> (Task 3), a pure function of the data.
 *
 * <p>A slot is a chosen weekday, or the target day of a moved session, that is not excluded by
 * an active schedule event (missed, skipped or moved away). Sessions are assigned to slots in
 * rotation order: an excluded day consumes nothing, so its session goes to the next slot and the
 * following sessions shift by exactly one slot. Without events the result is identical to
 * {@link RotationCalculator}: the closed-form count of chosen weekdays is only corrected by the
 * (few) events inside the interval.
 */
final class SlotPlanner {

    /** Upper bound of the day-by-day searches (next/previous slot). */
    static final int SEARCH_DAYS = 120;

    /** An active event: {@code target} is non-null only for a moved session. */
    record Change(LocalDate original, LocalDate target) {
    }

    private final Set<Integer> weekdays;
    private final int sessionCount;
    private final LocalDate anchor;
    private final int anchorIndex;
    private final NavigableSet<LocalDate> excluded = new TreeSet<>();
    private final Map<LocalDate, LocalDate> targetByOriginal = new TreeMap<>();
    private final NavigableSet<LocalDate> extras = new TreeSet<>();

    SlotPlanner(Set<Integer> weekdays, int sessionCount, LocalDate anchor, int anchorIndex, Collection<Change> changes) {
        this.weekdays = weekdays;
        this.sessionCount = sessionCount;
        this.anchor = anchor;
        this.anchorIndex = anchorIndex;
        for (Change change : changes) {
            excluded.add(change.original());
            if (change.target() != null) {
                targetByOriginal.put(change.original(), change.target());
                extras.add(change.target());
            }
        }
    }

    boolean isChosenWeekday(LocalDate date) {
        return RotationCalculator.isScheduled(weekdays, date);
    }

    boolean isExcluded(LocalDate date) {
        return excluded.contains(date);
    }

    /** The day was a slot before its event: a chosen weekday or the target of a move. */
    boolean wasSlot(LocalDate date) {
        return isChosenWeekday(date) || extras.contains(date);
    }

    boolean isSlot(LocalDate date) {
        return !excluded.contains(date) && wasSlot(date);
    }

    boolean isExtra(LocalDate date) {
        return extras.contains(date) && !isChosenWeekday(date);
    }

    /** Slots in [from, toExclusive). */
    long slotsBetween(LocalDate from, LocalDate toExclusive) {
        if (!toExclusive.isAfter(from)) {
            return 0;
        }
        long count = RotationCalculator.countScheduledDays(weekdays, from, toExclusive);
        for (LocalDate day : excluded.subSet(from, true, toExclusive, false)) {
            if (isChosenWeekday(day)) count--;
        }
        for (LocalDate day : extras.subSet(from, true, toExclusive, false)) {
            if (!isChosenWeekday(day) && !excluded.contains(day)) count++;
        }
        return count;
    }

    /** Index the date would receive if it were a slot (used for events and previews). */
    int indexAt(LocalDate date) {
        if (sessionCount <= 0) {
            return -1;
        }
        long k = date.isBefore(anchor) ? -slotsBetween(date, anchor) : slotsBetween(anchor, date);
        return (int) Math.floorMod(anchorIndex + k, (long) sessionCount);
    }

    /** 0-based session of the date, or -1 when the date is not a slot. */
    int sessionIndex(LocalDate date) {
        if (sessionCount <= 0 || weekdays.isEmpty() || !isSlot(date)) {
            return -1;
        }
        return indexAt(date);
    }

    /** First excluded day of the run that this slot recovers, or null (the slot is not shifted). */
    LocalDate rescheduledFrom(LocalDate date) {
        if (!isSlot(date)) {
            return null;
        }
        LocalDate origin = null;
        for (int i = 1; i <= SEARCH_DAYS; i++) {
            LocalDate day = date.minusDays(i);
            if (isSlot(day)) {
                break;
            }
            if (excluded.contains(day) && wasSlot(day)) {
                origin = day;
            }
        }
        return origin;
    }

    /** Where the session of an excluded day is trained: the move target or the next slot. */
    LocalDate landsOn(LocalDate original) {
        LocalDate target = targetByOriginal.get(original);
        if (target != null && isSlot(target)) {
            return target;
        }
        return nextSlot(original.plusDays(1));
    }

    /** First slot on or after {@code from}, or null within {@link #SEARCH_DAYS}. */
    LocalDate nextSlot(LocalDate from) {
        for (int i = 0; i < SEARCH_DAYS; i++) {
            LocalDate day = from.plusDays(i);
            if (isSlot(day)) {
                return day;
            }
        }
        return null;
    }

    /** Last slot strictly before {@code before}, or null within {@link #SEARCH_DAYS}. */
    LocalDate previousSlot(LocalDate before) {
        for (int i = 1; i <= SEARCH_DAYS; i++) {
            LocalDate day = before.minusDays(i);
            if (isSlot(day)) {
                return day;
            }
        }
        return null;
    }
}
