import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { problem, server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import { workoutState } from '../../test/workoutFixtures';
import { remainingRestMs } from './useRestTimer';

afterEach(() => {
  vi.useRealTimers();
});

describe('remainingRestMs', () => {
  it('uses server instants corrected by the clock offset', () => {
    // Server is 10 s ahead of the client: the client clock must not shorten the rest.
    const receivedAt = Date.parse('2026-10-05T08:00:00Z');
    const serverTime = '2026-10-05T08:00:10Z';
    const restEndsAt = '2026-10-05T08:01:10Z';
    expect(remainingRestMs(restEndsAt, serverTime, receivedAt, receivedAt)).toBe(60_000);
    expect(remainingRestMs(restEndsAt, serverTime, receivedAt, receivedAt + 45_000)).toBe(15_000);
    expect(remainingRestMs(restEndsAt, serverTime, receivedAt, receivedAt + 120_000)).toBe(0);
  });
});

describe('workout screen', () => {
  it('shows the current exercise with set X of N, MAX values and the main action', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
    );
    renderApp('/app/workout/w-1');
    const current = await screen.findByRole('region', { name: 'Panca' });
    expect(within(current).getByText('1/2')).toBeInTheDocument();
    expect(within(current).getByText('10')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeInTheDocument();
    const list = screen.getByRole('list');
    expect(within(list).getByText('In corso')).toBeInTheDocument();
    expect(within(list).getByText('Da fare')).toBeInTheDocument();
    expect(within(list).getByText(/MAX/)).toBeInTheDocument();
  });

  it('completes the precise set and starts the rest timer from server instants', async () => {
    let calledUrl = '';
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', ({ request }) => {
        calledUrl = new URL(request.url).pathname;
        const serverTime = new Date();
        const state = workoutState({
          currentSetId: 's-2',
          restEndsAt: new Date(serverTime.getTime() + 60_000).toISOString(),
          restSeconds: 60,
          serverTime: serverTime.toISOString(),
          nextAction: 'WAIT_FOR_REST',
        });
        state.exercises[0]!.setsCompleted = 1;
        state.exercises[0]!.sets[0]!.completedAt = serverTime.toISOString();
        return HttpResponse.json(state);
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Fine serie' }));
    const timer = await screen.findByRole('timer');
    expect(calledUrl).toBe('/api/me/workouts/w-1/sets/s-1/complete');
    expect(timer).toHaveTextContent(/1:00|0:59/);
    // The next set can still be completed during the rest (O-06).
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeEnabled();
    expect(screen.getByText('2/2')).toBeInTheDocument();
  });

  it('counts down and re-aligns with the server when the page becomes visible', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const start = Date.now();
    let fetches = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => {
        fetches++;
        return HttpResponse.json(
          workoutState({
            restEndsAt: new Date(start + 30_000).toISOString(),
            restSeconds: 30,
            serverTime: new Date(Date.now()).toISOString(),
            nextAction: 'WAIT_FOR_REST',
          }),
        );
      }),
    );
    renderApp('/app/workout/w-1');
    const timer = await screen.findByRole('timer');
    expect(timer).toHaveTextContent(/0:(30|29)/);
    await act(async () => {
      vi.advanceTimersByTime(10_000);
    });
    expect(screen.getByRole('timer')).toHaveTextContent(/0:(20|19)/);

    const before = fetches;
    act(() => {
      document.dispatchEvent(new Event('visibilitychange'));
    });
    await waitFor(() => expect(fetches).toBeGreaterThan(before));
  });

  it('asks confirmation before skipping and shows the skipped state', async () => {
    let skippedUrl = '';
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/exercises/:exerciseId/skip', ({ request }) => {
        skippedUrl = new URL(request.url).pathname;
        const state = workoutState({ currentExerciseId: 'e-2', currentSetId: 's-3' });
        state.exercises[0]!.status = 'SKIPPED';
        state.exercises[1]!.status = 'IN_PROGRESS';
        return HttpResponse.json(state);
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Salta esercizio' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Salta esercizio' }));
    await waitFor(() => expect(skippedUrl).toBe('/api/me/workouts/w-1/exercises/e-1/skip'));
    expect(await screen.findByText('Saltato')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Trazioni' })).toBeInTheDocument();
  });

  it('retries Fine serie after a network failure (idempotent action)', async () => {
    let attempts = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', () => {
        attempts++;
        if (attempts === 1) {
          return HttpResponse.error();
        }
        return HttpResponse.json(workoutState({ currentSetId: 's-2' }));
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Fine serie' }));
    await waitFor(() => expect(screen.getByText('2/2')).toBeInTheDocument(), { timeout: 4000 });
    expect(attempts).toBe(2);
  });

  it('re-syncs when the set is no longer the current one', async () => {
    let fetches = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => {
        fetches++;
        return HttpResponse.json(fetches === 1 ? workoutState() : workoutState({ currentSetId: 's-2' }));
      }),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', () => problem(422, 'SET_NOT_CURRENT')),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Fine serie' }));
    expect(await screen.findByText('La schermata è stata aggiornata con lo stato più recente.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('2/2')).toBeInTheDocument());
  });

  it('shows the summary when the workout is completed', async () => {
    const done = workoutState({ status: 'COMPLETED', currentExerciseId: null, currentSetId: null, nextAction: 'FINISHED', finishedAt: '2026-10-05T09:00:00Z' });
    done.exercises[0]!.status = 'COMPLETED';
    done.exercises[1]!.status = 'SKIPPED';
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(done)),
    );
    renderApp('/app/workout/w-1');
    expect(await screen.findByRole('heading', { name: 'Allenamento completato!' })).toBeInTheDocument();
    expect(screen.getByText('1 esercizi completati, 1 saltati su 2.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Fine serie' })).not.toBeInTheDocument();
  });
});
