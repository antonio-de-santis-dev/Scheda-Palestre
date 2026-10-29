package com.gymplanner.workoutplan.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class PlanSessionQueriesTest {

    private final WorkoutPlanRepository repository = mock(WorkoutPlanRepository.class);
    private final PlanService service = new PlanService(repository, mock(PlanStructureAssembler.class),
            mock(ApplicationEventPublisher.class), Clock.systemUTC());

    @Test
    void loadsSeveralPlansOnceAndKeepsEmptyPlansAndSessionOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID empty = UUID.randomUUID();
        var row1 = row(first, "Giorno 1", 1);
        var row2 = row(first, "Giorno 2", 2);
        var row3 = row(second, "Sessione unica", 1);
        when(repository.findSessionRows(Set.of(first, second, empty))).thenReturn(List.of(row1, row2, row3));

        var result = service.sessionsInOrder(List.of(first, second, first, empty));

        assertThat(result.get(first)).extracting(s -> s.title()).containsExactly("Giorno 1", "Giorno 2");
        assertThat(result.get(second)).extracting(s -> s.title()).containsExactly("Sessione unica");
        assertThat(result.get(empty)).isEmpty();
        verify(repository).findSessionRows(Set.of(first, second, empty));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyRequestDoesNotAccessTheDatabase() {
        assertThat(service.sessionsInOrder(List.<UUID>of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private static WorkoutPlanRepository.SessionRow row(UUID planId, String title, int position) {
        var row = mock(WorkoutPlanRepository.SessionRow.class);
        when(row.getPlanId()).thenReturn(planId);
        when(row.getId()).thenReturn(UUID.randomUUID());
        when(row.getTitle()).thenReturn(title);
        when(row.getPosition()).thenReturn(position);
        return row;
    }
}
