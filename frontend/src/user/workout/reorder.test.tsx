import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

describe('workout execution order', () => {
  it('saves the order, changes current exercise, preserves partial sets/rest and reloads it', async () => {
    const now = new Date();
    let state = workoutState({ executionVersion: 3, currentSetId: 's-2',
      serverTime: now.toISOString(), restEndsAt: new Date(now.getTime() + 60000).toISOString(),
      restSeconds: 60, nextAction: 'WAIT_FOR_REST' });
    state.exercises[0]!.setsCompleted = 1;
    state.exercises[0]!.sets[0]!.completedAt = now.toISOString();
    let body: unknown;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.post('*/api/me/workouts/:id/exercises/reorder', async ({ request }) => {
        body = await request.json();
        state = { ...state, executionVersion: 4, currentExerciseId: 'e-2', currentSetId: 's-3',
          exercises: [{ ...state.exercises[1]!, position: 1, status: 'IN_PROGRESS' },
            { ...state.exercises[0]!, position: 2, status: 'TODO' }] };
        return HttpResponse.json(state);
      }),
    );
    const view = renderApp('/app/workout/w-1');
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Sposta su Trazioni' }));
    expect(body).toEqual({ exerciseIds: ['e-2', 'e-1'], expectedVersion: 3 });
    expect(await screen.findByRole('region', { name: 'Trazioni' })).toBeInTheDocument();
    expect(screen.getByRole('timer')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fine serie' })).toHaveAttribute('aria-disabled', 'true');
    expect(screen.queryByText('Recupero terminato')).not.toBeInTheDocument();
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[0]).toHaveTextContent('Trazioni');
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[1]).toHaveTextContent('1/2 serie');
    view.unmount();
    renderApp('/app/workout/w-1');
    expect(await screen.findByRole('region', { name: 'Trazioni' })).toBeInTheDocument();
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[0]).toHaveTextContent('Trazioni');
  });

  it('resyncs stale changes without pretending the order was saved', async () => {
    let state = workoutState();
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.post('*/api/me/workouts/:id/exercises/reorder', () => {
        state = { ...state, executionVersion: 1, currentSetId: 's-2' };
        return problem(409, 'WORKOUT_STATE_CHANGED');
      }),
    );
    const { client } = renderApp('/app/workout/w-1');
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Sposta su Trazioni' }));
    expect(await screen.findByText('La schermata è stata aggiornata con lo stato più recente.')).toBeInTheDocument();
    await waitFor(() => expect(client.getQueryData<{ executionVersion: number }>(['me', 'workout', 'w-1'])?.executionVersion).toBe(1));
    expect(screen.queryByText(/Ordine salvato/)).not.toBeInTheDocument();
  });

  it('locks all conflicting actions during saving and does not offer reorder after finishing', async () => {
    let resolve!: () => void;
    const response = new Promise<void>((done) => { resolve = done; });
    let requests = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/exercises/reorder', async () => {
        requests++; await response;
        return HttpResponse.json(workoutState({ status: 'INTERRUPTED', currentExerciseId: null, currentSetId: null, nextAction: 'FINISHED' }));
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Sposta su Trazioni' }));
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Salta esercizio' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Interrompi' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Sposta su Trazioni' }));
    expect(requests).toBe(1);
    resolve();
    await screen.findByRole('heading', { name: 'Allenamento interrotto' });
    expect(screen.queryByRole('button', { name: /Sposta/ })).not.toBeInTheDocument();
  });
});
