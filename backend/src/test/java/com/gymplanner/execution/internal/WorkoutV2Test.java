package com.gymplanner.execution.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gymplanner.shared.error.BusinessRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkoutV2Test {
    private static final Instant START = Instant.parse("2026-10-05T23:50:00Z");

    private Workout workout() {
        return new Workout(UUID.randomUUID(), UUID.randomUUID(), null, LocalDate.of(2026, 10, 5), START, "Scheda", "A");
    }

    @Test
    void elapsedTimeIncludesMidnightAndFreezesForCompletedAndInterruptedWorkouts() {
        for (boolean interrupted : new boolean[] { false, true }) {
            Workout w = workout();
            assertThat(w.durationSeconds(START.minusSeconds(10))).isZero();
            assertThat(w.durationSeconds(START.plusSeconds(3700))).isEqualTo(3700);
            if (interrupted) w.interrupt(START.plusSeconds(3700));
            else w.complete(START.plusSeconds(3700));
            assertThat(WorkoutStateMapper.toState(w, START.plusSeconds(9000)).durationSeconds()).isEqualTo(3700);
            assertThat(WorkoutDtos.WorkoutSummary.of(w).durationSeconds()).isEqualTo(3700);
        }
        assertThat(WorkoutDtos.WorkoutSummary.of(workout()).durationSeconds()).isNull();
    }

    @Test
    void pausedRecoverySurvivesTimePassingAndResumesAtTheExactRemainingTime() {
        Workout w = workout();
        w.startRest(START, 60);
        w.changeRest(WorkoutDtos.RestAction.PAUSE, null, START.plusMillis(15_500));
        var state = WorkoutStateMapper.toState(w, START.plusSeconds(200));
        assertThat(state.restPaused()).isTrue();
        assertThat(state.restEndsAt()).isNull();
        assertThat(state.restRemainingSeconds()).isEqualTo(45);
        assertThat(state.nextAction()).isEqualTo(WorkoutDtos.NextAction.WAIT_FOR_REST);
        w.changeRest(WorkoutDtos.RestAction.EXTEND, 30, START.plusSeconds(200));
        assertThat(w.remainingRestMillis(START.plusSeconds(500))).isEqualTo(74_500);
        w.changeRest(WorkoutDtos.RestAction.RESUME, null, START.plusSeconds(500));
        assertThat(w.getRestEndsAt()).isEqualTo(START.plusMillis(574_500));
        assertThat(w.remainingRestMillis(START.plusSeconds(510))).isEqualTo(64_500);
        assertThat(w.getRestVersion()).isEqualTo(4);
    }

    @Test
    void skipUnblocksTheNextSetAndANewSetStartsANewRecovery() {
        Workout w = workout();
        w.startRest(START, 60);
        w.changeRest(WorkoutDtos.RestAction.EXTEND, 30, START.plusSeconds(10));
        assertThat(w.getRestEndsAt()).isEqualTo(START.plusSeconds(90));
        w.changeRest(WorkoutDtos.RestAction.SKIP, null, START.plusSeconds(10));
        assertThat(WorkoutStateMapper.toState(w, START.plusSeconds(10)).nextAction()).isEqualTo(WorkoutDtos.NextAction.COMPLETE_SET);
        w.startRest(START.plusSeconds(20), 90);
        assertThat(w.remainingRestMillis(START.plusSeconds(20))).isEqualTo(90_000);
        w.interrupt(START.plusSeconds(21));
        assertThat(w.remainingRestMillis(START.plusSeconds(21))).isZero();
        assertThat(w.isRestPaused()).isFalse();
    }

    @Test
    void expiredRecoveryCannotBeReopenedAndExtensionsHaveABound() {
        Workout w = workout();
        w.startRest(START, 60);
        assertThatThrownBy(() -> w.changeRest(WorkoutDtos.RestAction.PAUSE, null, START.plusSeconds(60)))
                .isInstanceOf(BusinessRuleException.class);
        w.startRest(START, 3600);
        assertThatThrownBy(() -> w.changeRest(WorkoutDtos.RestAction.EXTEND, 30, START))
                .isInstanceOf(BusinessRuleException.class);
    }
}
