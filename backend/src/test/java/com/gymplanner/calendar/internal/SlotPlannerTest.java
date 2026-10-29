package com.gymplanner.calendar.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gymplanner.calendar.api.RotationCalculator;
import com.gymplanner.calendar.internal.SlotPlanner.Change;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/** Task 3: rotation and rescheduling algorithm. */
class SlotPlannerTest {

    /** Monday 5 October 2026. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);
    private static final Set<Integer> MON_WED_FRI = Set.of(1, 3, 5);

    private static SlotPlanner planner(Set<Integer> days, int sessions, LocalDate anchor, List<Change> changes) {
        return new SlotPlanner(days, sessions, anchor, 0, changes);
    }

    private static String session(SlotPlanner planner, LocalDate date) {
        int index = planner.sessionIndex(date);
        return index < 0 ? "-" : "G" + (index + 1);
    }

    @Test
    void withoutEventsItMatchesTheOriginalRotation() {
        Random random = new Random(42);
        for (int run = 0; run < 200; run++) {
            Set<Integer> days = new TreeSet<>();
            for (int d = 1; d <= 7; d++) if (random.nextBoolean()) days.add(d);
            if (days.isEmpty()) days.add(1 + random.nextInt(7));
            int sessions = 1 + random.nextInt(5);
            int anchorIndex = random.nextInt(sessions);
            LocalDate anchor = MONDAY.plusDays(random.nextInt(400) - 200);
            SlotPlanner planner = new SlotPlanner(days, sessions, anchor, anchorIndex, List.of());
            for (int i = -60; i < 60; i++) {
                LocalDate date = anchor.plusDays(i);
                assertThat(planner.sessionIndex(date))
                        .isEqualTo(RotationCalculator.sessionIndex(days, sessions, anchor, anchorIndex, date));
            }
        }
    }

    @Test
    void aSkippedMondayMovesDayOneToWednesdayAndShiftsTheRestByOneSlot() {
        SlotPlanner before = planner(MON_WED_FRI, 2, MONDAY, List.of());
        assertThat(List.of(session(before, MONDAY), session(before, MONDAY.plusDays(2)), session(before, MONDAY.plusDays(4))))
                .containsExactly("G1", "G2", "G1");

        SlotPlanner after = planner(MON_WED_FRI, 2, MONDAY, List.of(new Change(MONDAY, null)));
        assertThat(session(after, MONDAY)).isEqualTo("-");
        assertThat(after.indexAt(MONDAY)).isZero(); // the skipped day still knows its session
        assertThat(List.of(session(after, MONDAY.plusDays(2)), session(after, MONDAY.plusDays(4)),
                session(after, MONDAY.plusDays(7)), session(after, MONDAY.plusDays(9))))
                .containsExactly("G1", "G2", "G1", "G2");
        assertThat(after.landsOn(MONDAY)).isEqualTo(MONDAY.plusDays(2));
        assertThat(after.rescheduledFrom(MONDAY.plusDays(2))).isEqualTo(MONDAY);
        assertThat(after.rescheduledFrom(MONDAY.plusDays(4))).isNull();
    }

    @Test
    void consecutiveMissedDaysKeepTheSameSessionWithoutDuplicatesOrGaps() {
        SlotPlanner planner = planner(MON_WED_FRI, 3, MONDAY,
                List.of(new Change(MONDAY, null), new Change(MONDAY.plusDays(2), null)));
        assertThat(session(planner, MONDAY.plusDays(4))).isEqualTo("G1");
        assertThat(session(planner, MONDAY.plusDays(7))).isEqualTo("G2");
        assertThat(session(planner, MONDAY.plusDays(9))).isEqualTo("G3");
        assertThat(planner.indexAt(MONDAY.plusDays(2))).isZero();
        assertThat(planner.rescheduledFrom(MONDAY.plusDays(4))).isEqualTo(MONDAY);
        assertThat(planner.landsOn(MONDAY)).isEqualTo(MONDAY.plusDays(4));
    }

    @Test
    void aMovedSessionGoesToTheFreeTargetDayAndNothingElseChanges() {
        LocalDate tuesday = MONDAY.plusDays(1);
        SlotPlanner planner = planner(MON_WED_FRI, 2, MONDAY, List.of(new Change(MONDAY, tuesday)));
        assertThat(session(planner, MONDAY)).isEqualTo("-");
        assertThat(session(planner, tuesday)).isEqualTo("G1");
        assertThat(session(planner, MONDAY.plusDays(2))).isEqualTo("G2");
        assertThat(session(planner, MONDAY.plusDays(4))).isEqualTo("G1");
        assertThat(planner.isExtra(tuesday)).isTrue();
        assertThat(planner.landsOn(MONDAY)).isEqualTo(tuesday);
        assertThat(planner.rescheduledFrom(tuesday)).isEqualTo(MONDAY);
    }

    @Test
    void worksAcrossMonthAndYearBoundariesAndBeforeTheAnchor() {
        LocalDate anchor = LocalDate.of(2026, 12, 28); // Monday
        LocalDate wednesday = LocalDate.of(2026, 12, 30);
        SlotPlanner planner = planner(MON_WED_FRI, 2, anchor, List.of(new Change(wednesday, null)));
        assertThat(session(planner, anchor)).isEqualTo("G1");
        assertThat(session(planner, LocalDate.of(2027, 1, 1))).isEqualTo("G2");
        assertThat(session(planner, LocalDate.of(2027, 1, 4))).isEqualTo("G1");
        assertThat(planner.landsOn(wednesday)).isEqualTo(LocalDate.of(2027, 1, 1));
        // February of a later year, far from the anchor: closed-form count plus one event.
        assertThat(planner.slotsBetween(anchor, LocalDate.of(2027, 3, 1)))
                .isEqualTo(RotationCalculator.countScheduledDays(MON_WED_FRI, anchor, LocalDate.of(2027, 3, 1)) - 1);
        // Dates before the anchor are defined backwards.
        assertThat(session(planner, LocalDate.of(2026, 12, 25))).isEqualTo("G2");
    }

    @Test
    void navigationHelpersFindTheSurroundingSlots() {
        SlotPlanner planner = planner(MON_WED_FRI, 2, MONDAY, List.of(new Change(MONDAY.plusDays(2), null)));
        assertThat(planner.nextSlot(MONDAY.plusDays(1))).isEqualTo(MONDAY.plusDays(4));
        assertThat(planner.previousSlot(MONDAY.plusDays(4))).isEqualTo(MONDAY);
        assertThat(planner.wasSlot(MONDAY.plusDays(2))).isTrue();
        assertThat(planner.isSlot(MONDAY.plusDays(2))).isFalse();
    }
}
