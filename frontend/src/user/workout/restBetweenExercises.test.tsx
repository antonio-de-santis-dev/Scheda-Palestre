import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

/** Task 1: the first exercise is completed and the recovery before "Trazioni" is running. */
function betweenExercises() {
  const now = new Date();
  const state = workoutState({ resultEntrySetId: 's-2', currentExerciseId: 'e-2', currentSetId: 's-3',
    restVersion: 2, executionVersion: 2, restKind: 'BETWEEN_EXERCISES',
    restEndsAt: new Date(now.getTime() + 150_000).toISOString(), serverTime: now.toISOString(),
    restSeconds: 150, restRemainingMillis: 150_000, nextAction: 'WAIT_FOR_REST' });
  const [first, second] = state.exercises;
  first!.status = 'COMPLETED';
  first!.setsCompleted = 2;
  first!.sets.forEach((set) => { set.completedAt = now.toISOString(); });
  second!.status = 'IN_PROGRESS';
  return state;
}

describe('recovery between exercises', () => {
  it('shows the completed and the next exercise with a single countdown', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(betweenExercises())),
    );
    renderApp('/app/workout/w-1');
    const transition = await screen.findByText('Appena concluso:');
    expect(transition.parentElement).toHaveTextContent('Appena concluso: Panca');
    expect(transition.parentElement).toHaveTextContent('Prossimo esercizio: Trazioni');
    const timers = screen.getAllByRole('timer');
    expect(timers).toHaveLength(1);
    expect(timers[0]).toHaveAccessibleName(/^Recupero tra esercizi: 2:(30|29) rimanenti$/);
    expect(transition.parentElement).toHaveTextContent(/^Recupero tra esercizi/);
    expect(screen.getByRole('button', { name: 'Salta recupero' })).toBeEnabled();
    expect(screen.getByRole('button', { name: /Fine serie/ })).toHaveAttribute('aria-disabled', 'true');
  });

  it('keeps the ordinary label for the recovery between two sets', async () => {
    const now = new Date();
    const state = workoutState({ resultEntrySetId: 's-1', currentSetId: 's-2', restKind: 'BETWEEN_SETS',
      restEndsAt: new Date(now.getTime() + 60_000).toISOString(), serverTime: now.toISOString(),
      restSeconds: 60, restRemainingMillis: 60_000, nextAction: 'WAIT_FOR_REST', restVersion: 1, executionVersion: 1 });
    state.exercises[0]!.sets[0]!.completedAt = now.toISOString();
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
    );
    renderApp('/app/workout/w-1');
    expect(await screen.findByRole('timer')).toHaveAccessibleName(/^Recupero: /);
    expect(screen.queryByText('Appena concluso:')).not.toBeInTheDocument();
  });
});
