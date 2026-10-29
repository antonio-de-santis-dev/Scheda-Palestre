import { screen, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

describe('last weight per set (Task 5)', () => {
  it('shows the weight of the same set last time, without filling the field', async () => {
    const now = new Date();
    // Set 1 completed, recovery running: set 2 is the current one, set 1 results can be entered.
    const state = workoutState({ resultEntrySetId: 's-1', currentSetId: 's-2', restVersion: 1, executionVersion: 1,
      restEndsAt: new Date(now.getTime() + 60_000).toISOString(), serverTime: now.toISOString(),
      restSeconds: 60, restRemainingMillis: 60_000, nextAction: 'WAIT_FOR_REST', restKind: 'BETWEEN_SETS' });
    state.exercises[0]!.setsCompleted = 1;
    state.exercises[0]!.sets[0]!.completedAt = now.toISOString();
    let requests = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.get('*/api/me/workouts/:id/previous-weights', () => {
        requests++;
        return HttpResponse.json([
          { exerciseId: 'e-1', setIndex: 1, weightKgUsed: 20, workoutId: 'w-0', scheduledDate: '2026-10-01' },
          { exerciseId: 'e-1', setIndex: 2, weightKgUsed: 22.5, workoutId: 'w-0', scheduledDate: '2026-10-01' },
        ]);
      }),
    );
    renderApp('/app/workout/w-1');
    expect(await screen.findByText('Ultima volta — Serie 2: 22,5 kg')).toBeInTheDocument();
    const form = screen.getByRole('form', { name: 'Risultati della serie appena svolta' });
    const weight = within(form).getByLabelText(/Peso usato/);
    expect(weight).toHaveValue('');
    expect(within(form).getByText('Ultima volta — Serie 1: 20 kg')).toBeInTheDocument();
    expect(requests).toBe(1); // one request for the whole workout, not one per set
  });

  it('says when there is no previous weight', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.get('*/api/me/workouts/:id/previous-weights', () => HttpResponse.json([])),
    );
    renderApp('/app/workout/w-1');
    expect(await screen.findByText('Nessun peso precedente')).toBeInTheDocument();
  });
});
