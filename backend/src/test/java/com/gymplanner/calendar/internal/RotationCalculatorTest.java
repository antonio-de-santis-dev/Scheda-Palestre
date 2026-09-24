package com.gymplanner.calendar.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.calendar.api.RotationCalculator;
import java.time.LocalDate;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class RotationCalculatorTest {

    // Monday 5 October 2026.
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final Set<Integer> MON_WED_FRI = Set.of(1, 3, 5);

    @Test
    void specExampleTwoSessionsOnMondayWednesdayFriday() {
        // Spec 10.6: Mon 1 -> Day 1, Wed 3 -> Day 2, Fri 5 -> Day 1, Mon 8 -> Day 2.
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, MONDAY, 0, MONDAY)).isZero();
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, MONDAY, 0, MONDAY.plusDays(2))).isEqualTo(1);
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, MONDAY, 0, MONDAY.plusDays(4))).isZero();
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, MONDAY, 0, MONDAY.plusDays(7))).isEqualTo(1);
    }

    @Test
    void restDaysAndEmptyInputsHaveNoSession() {
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, MONDAY, 0, MONDAY.plusDays(1))).isEqualTo(-1);
        assertThat(RotationCalculator.sessionIndex(Set.of(), 2, MONDAY, 0, MONDAY)).isEqualTo(-1);
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 0, MONDAY, 0, MONDAY)).isEqualTo(-1);
    }

    @Test
    void missedDaysStillConsumeTheirSession() {
        // O-01: rotation is purely calendar based, nothing depends on workouts.
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 3, MONDAY, 0, MONDAY.plusDays(7))).isZero();
    }

    @Test
    void anchorIndexShiftsTheRotation() {
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 3, MONDAY, 2, MONDAY)).isEqualTo(2);
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 3, MONDAY, 2, MONDAY.plusDays(2))).isZero();
    }

    @Test
    void anchorOnARestDayStartsFromTheNextTrainingDay() {
        LocalDate tuesday = MONDAY.plusDays(1);
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, tuesday, 0, MONDAY.plusDays(2))).isZero();
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, tuesday, 0, MONDAY.plusDays(4))).isEqualTo(1);
    }

    @Test
    void datesBeforeTheAnchorRotateBackwards() {
        LocalDate friday = MONDAY.plusDays(4);
        // Anchor Friday with index 0: Wednesday was the previous session (index 1 of 2).
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, friday, 0, MONDAY.plusDays(2))).isEqualTo(1);
        assertThat(RotationCalculator.sessionIndex(MON_WED_FRI, 2, friday, 0, MONDAY)).isZero();
    }

    @Test
    void countUsesWholeWeeksPlusRemainder() {
        assertThat(RotationCalculator.countScheduledDays(MON_WED_FRI, MONDAY, MONDAY.plusDays(7))).isEqualTo(3);
        assertThat(RotationCalculator.countScheduledDays(MON_WED_FRI, MONDAY, MONDAY.plusDays(8))).isEqualTo(4);
        assertThat(RotationCalculator.countScheduledDays(MON_WED_FRI, MONDAY, MONDAY)).isZero();
        // Ten years in constant time: 521 full weeks + Mon..Sat (no Sunday).
        assertThat(RotationCalculator.countScheduledDays(Set.of(7), MONDAY, MONDAY.plusDays(3653))).isEqualTo(521);
    }

    @RepeatedTest(50)
    void matchesTheNaiveDayByDayCount() {
        Random random = new Random();
        Set<Integer> days = new TreeSet<>();
        for (int d = 1; d <= 7; d++) {
            if (random.nextBoolean()) {
                days.add(d);
            }
        }
        LocalDate from = MONDAY.plusDays(random.nextInt(400) - 200);
        LocalDate to = from.plusDays(random.nextInt(900));
        long naive = 0;
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(1)) {
            if (days.contains(d.getDayOfWeek().getValue())) {
                naive++;
            }
        }
        assertThat(RotationCalculator.countScheduledDays(days, from, to)).isEqualTo(naive);
        assertThat(RotationCalculator.offset(days, to, from)).isEqualTo(-naive);
    }
}
