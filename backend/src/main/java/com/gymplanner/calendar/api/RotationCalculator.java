package com.gymplanner.calendar.api;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Pure implementation of the session rotation (spec 10.6).
 *
 * <p>For a date {@code d}: {@code k} = number of scheduled days between the anchor {@code a}
 * (inclusive) and {@code d} (exclusive); session = {@code S[(i0 + k) mod N]}. Dates before the
 * anchor use a negative {@code k} (scheduled days in [d, a)), so the rotation is also defined
 * backwards. The count uses whole weeks times the number of chosen weekdays plus the remainder:
 * no day-by-day iteration over long intervals.
 */
public final class RotationCalculator {

    private RotationCalculator() {
    }

    /** Scheduled days in [from, toExclusive); 0 when the interval is empty. */
    public static long countScheduledDays(Set<Integer> weekdays, LocalDate from, LocalDate toExclusive) {
        if (weekdays.isEmpty() || !toExclusive.isAfter(from)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(from, toExclusive);
        long count = (days / 7) * weekdays.size();
        int remainder = (int) (days % 7);
        int startDow = from.getDayOfWeek().getValue();
        for (int i = 0; i < remainder; i++) {
            int dow = ((startDow - 1 + i) % 7) + 1;
            if (weekdays.contains(dow)) {
                count++;
            }
        }
        return count;
    }

    /** Signed number of scheduled days between the anchor and the date (see class doc). */
    public static long offset(Set<Integer> weekdays, LocalDate anchor, LocalDate date) {
        if (!date.isBefore(anchor)) {
            return countScheduledDays(weekdays, anchor, date);
        }
        return -countScheduledDays(weekdays, date, anchor);
    }

    public static boolean isScheduled(Set<Integer> weekdays, LocalDate date) {
        return weekdays.contains(date.getDayOfWeek().getValue());
    }

    /**
     * Index (0-based) of the session planned on {@code date}, or -1 when the date is a rest day
     * or there is nothing to rotate.
     */
    public static int sessionIndex(Set<Integer> weekdays, int sessionCount, LocalDate anchor, int anchorIndex,
            LocalDate date) {
        if (sessionCount <= 0 || weekdays.isEmpty() || !isScheduled(weekdays, date)) {
            return -1;
        }
        long k = offset(weekdays, anchor, date);
        return (int) Math.floorMod(anchorIndex + k, (long) sessionCount);
    }

    /** Validates ISO weekdays 1 (Monday) to 7 (Sunday). */
    public static boolean isValidWeekday(int weekday) {
        return weekday >= DayOfWeek.MONDAY.getValue() && weekday <= DayOfWeek.SUNDAY.getValue();
    }
}
