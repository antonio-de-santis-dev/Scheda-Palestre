import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';
import type { RestRequest } from './api';

function activeRest() {
  const now = new Date();
  const state = workoutState({ currentSetId: 's-2', restVersion: 1, executionVersion: 1,
    restEndsAt: new Date(now.getTime() + 60_000).toISOString(), serverTime: now.toISOString(),
    restSeconds: 60, restRemainingMillis: 60_000, nextAction: 'WAIT_FOR_REST' });
  state.exercises[0]!.setsCompleted = 1;
  state.exercises[0]!.sets[0]!.completedAt = now.toISOString();
  return state;
}

describe('persistent recovery controls', () => {
  it('pauses, adds 30 seconds, reloads, resumes and skips only after confirmation', async () => {
    let state = activeRest();
    const requests: RestRequest[] = [];
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.post('*/api/me/workouts/:id/rest', async ({ request }) => {
        const body = await request.json() as RestRequest;
        requests.push(body);
        state = { ...state, executionVersion: state.executionVersion + 1, restVersion: state.restVersion + 1,
          serverTime: new Date().toISOString() };
        if (body.action === 'PAUSE') state = { ...state, restPaused: true, restEndsAt: null, restRemainingMillis: 40_123 };
        if (body.action === 'EXTEND') state = { ...state, restRemainingMillis: 70_123, restSeconds: 90 };
        if (body.action === 'RESUME') state = { ...state, restPaused: false, restEndsAt: new Date(Date.now() + 70_123).toISOString() };
        if (body.action === 'SKIP') state = { ...state, restPaused: false, restEndsAt: null, restRemainingMillis: 0,
          restSeconds: null, nextAction: 'COMPLETE_SET' };
        return HttpResponse.json(state);
      }),
    );
    const user = userEvent.setup();
    let view = renderApp('/app/workout/w-1');
    await user.click(await screen.findByRole('button', { name: 'Pausa recupero' }));
    expect(await screen.findByRole('button', { name: 'Riprendi recupero' })).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveTextContent('0:41');
    expect(screen.getByRole('button', { name: 'Fine serie' })).toHaveAttribute('aria-disabled', 'true');
    await user.click(screen.getByRole('button', { name: '30 secondi' }));
    await waitFor(() => expect(screen.getByRole('timer')).toHaveTextContent('1:11'));
    view.unmount();
    view = renderApp('/app/workout/w-1');
    await screen.findByRole('button', { name: 'Riprendi recupero' });
    expect(screen.getByRole('timer')).toHaveTextContent('1:11');
    expect(screen.getByRole('list')).toHaveTextContent('1/2 serie');
    await user.click(screen.getByRole('button', { name: 'Riprendi recupero' }));
    await screen.findByRole('button', { name: 'Pausa recupero' });
    await user.click(screen.getByRole('button', { name: 'Salta recupero' }));
    const dialog = screen.getByRole('dialog', { name: 'Saltare il recupero?' });
    expect(requests).toHaveLength(3);
    await user.click(within(dialog).getByRole('button', { name: 'Annulla' }));
    expect(requests).toHaveLength(3);
    await user.click(screen.getByRole('button', { name: 'Salta recupero' }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Salta recupero' }));
    await waitFor(() => expect(screen.queryByRole('timer')).not.toBeInTheDocument());
    expect(screen.getByRole('button', { name: 'Fine serie' })).not.toHaveAttribute('aria-disabled');
    expect(requests).toEqual([
      { action: 'PAUSE', expectedVersion: 1, expectedExecutionVersion: 1 },
      { action: 'EXTEND', expectedVersion: 2, expectedExecutionVersion: 2 },
      { action: 'RESUME', expectedVersion: 3, expectedExecutionVersion: 3 },
      { action: 'SKIP', expectedVersion: 4, expectedExecutionVersion: 4 },
    ]);
    view.unmount();
    renderApp('/app/workout/w-1');
    await screen.findByRole('region', { name: 'Panca' });
    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fine serie' })).not.toHaveAttribute('aria-disabled');
  });

  it('blocks conflicting actions and duplicate clicks while saving', async () => {
    let resolve!: () => void;
    const gate = new Promise<void>((done) => { resolve = done; });
    let calls = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(activeRest())),
      http.post('*/api/me/workouts/:id/rest', async () => {
        calls++; await gate;
        return HttpResponse.json(activeRest());
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    const add = await screen.findByRole('button', { name: '30 secondi' });
    await user.click(add);
    expect(add).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Pausa recupero' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Salta esercizio' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Interrompi' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Trascina Trazioni' })).toBeDisabled();
    await user.click(add);
    expect(calls).toBe(1);
    resolve();
    await waitFor(() => expect(add).not.toBeDisabled());
  });

  it('keeps the confirmation tied to the selected rest and resyncs stale requests', async () => {
    let state = activeRest();
    let body: unknown;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.post('*/api/me/workouts/:id/rest', async ({ request }) => {
        body = await request.json();
        return problem(409, 'REST_STATE_CHANGED');
      }),
    );
    const { client } = renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salta recupero' }));
    state = { ...state, restVersion: 2, executionVersion: 2, restPaused: true,
      restEndsAt: null, restRemainingMillis: 35_000 };
    act(() => client.setQueryData(['me', 'workout', 'w-1'], { ...state, receivedAt: Date.now() }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Salta recupero' }));
    expect(await screen.findByText('La schermata è stata aggiornata con lo stato più recente.')).toBeInTheDocument();
    expect(body).toEqual({ action: 'SKIP', expectedVersion: 1, expectedExecutionVersion: 1 });
    expect(screen.getByRole('timer')).toHaveTextContent('0:35');
    expect(screen.getByRole('button', { name: 'Riprendi recupero' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fine serie' })).toHaveAttribute('aria-disabled', 'true');
  });
});
