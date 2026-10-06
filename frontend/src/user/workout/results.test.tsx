import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';
import type { RecordSetResultsRequest } from './api';

vi.mock('canvas-confetti', () => ({ default: vi.fn(() => Promise.resolve()) }));
afterEach(() => vi.useRealTimers());
function recovery(seconds = 60) {
  const now = new Date();
  const state = workoutState({ currentSetId: 's-2', resultEntrySetId: 's-1',
    executionVersion: 1, restVersion: 1, nextAction: 'WAIT_FOR_REST',
    serverTime: now.toISOString(), restEndsAt: new Date(now.getTime() + seconds * 1000).toISOString(), restSeconds: seconds });
  state.exercises[0]!.setsCompleted = 1;
  state.exercises[0]!.sets[0]!.completedAt = now.toISOString();
  return state;
}
function setup(state = recovery()) {
  server.use(http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
    http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)));
  return state;
}
describe('actual set results during recovery', () => {
  it('hides inputs during execution and opens them for the series just completed', async () => {
    setup(workoutState()); const after = recovery(); let completeBody: unknown; let target: unknown; let body: unknown;
    server.use(http.post('*/api/me/workouts/:id/sets/:setId/complete', async ({ request }) => {
      completeBody = await request.json(); return HttpResponse.json(after);
    }), http.post('*/api/me/workouts/:id/sets/:setId/results', async ({ request, params }) => {
      target = params.setId; body = await request.json(); return HttpResponse.json(after);
    }));
    renderApp('/app/workout/w-1'); const user = userEvent.setup();
    await screen.findByRole('button', { name: 'Fine serie' });
    expect(screen.queryByLabelText('Peso usato (kg)')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Ripetizioni effettive')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Fine serie' }));
    await user.type(await screen.findByLabelText('Peso usato (kg)'), '32,75');
    await user.type(screen.getByLabelText('Ripetizioni effettive'), '8');
    expect(screen.getByRole('form')).toHaveTextContent('Serie appena svolta: Panca · 1');
    await user.click(screen.getByRole('button', { name: 'Salva risultati' }));
    await waitFor(() => expect(target).toBe('s-1'));
    expect(completeBody).toEqual({ weightKgUsed: null, repsActual: null });
    expect(body).toEqual({ results: { weightKgUsed: 32.75, repsActual: 8 }, expectedExecutionVersion: 1, expectedRestVersion: 1 });
  });

  it.each([
    { weight: '', reps: '', expected: { weightKgUsed: null, repsActual: null } },
    { weight: '0', reps: '0', expected: { weightKgUsed: 0, repsActual: 0 } },
    { weight: '', reps: '7', expected: { weightKgUsed: null, repsActual: 7 } },
  ])('preserves missing and zero results ($weight/$reps)', async ({ weight, reps, expected }) => {
    const state = setup(); let body: RecordSetResultsRequest | undefined;
    server.use(http.post('*/api/me/workouts/:id/sets/:setId/results', async ({ request }) => {
      body = await request.json() as RecordSetResultsRequest; return HttpResponse.json(state);
    }));
    renderApp('/app/workout/w-1'); const user = userEvent.setup();
    await screen.findByLabelText('Peso usato (kg)');
    if (weight) await user.type(screen.getByLabelText('Peso usato (kg)'), weight);
    if (reps) await user.type(screen.getByLabelText('Ripetizioni effettive'), reps);
    await user.click(screen.getByRole('button', { name: 'Salva risultati' }));
    await waitFor(() => expect(body?.results).toEqual(expected));
  });

  it('rejects invalid input and hides the fields when recovery naturally expires', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true }); setup(recovery(5)); let calls = 0;
    server.use(http.post('*/api/me/workouts/:id/sets/:setId/results', () => { calls++; return HttpResponse.json(recovery()); }));
    renderApp('/app/workout/w-1'); const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const weight = await screen.findByLabelText('Peso usato (kg)');
    await user.type(weight, '1.234'); await user.type(screen.getByLabelText('Ripetizioni effettive'), '7.5');
    await user.click(screen.getByRole('button', { name: 'Salva risultati' }));
    expect(weight).toHaveAttribute('aria-invalid', 'true'); expect(calls).toBe(0);
    await act(async () => { vi.advanceTimersByTime(6000); });
    await waitFor(() => expect(screen.queryByLabelText('Peso usato (kg)')).not.toBeInTheDocument());
    expect(screen.queryByLabelText('Ripetizioni effettive')).not.toBeInTheDocument();
  });

  it('restores saved values after refresh and keeps a retry bound to the previous series', async () => {
    const state = recovery(); state.exercises[0]!.sets[0]!.weightKgUsed = 40; state.exercises[0]!.sets[0]!.repsActual = 9;
    setup(state); const bodies: unknown[] = [];
    server.use(http.post('*/api/me/workouts/:id/sets/:setId/results', async ({ request, params }) => {
      bodies.push({ id: params.setId, body: await request.json() });
      return bodies.length === 1 ? HttpResponse.error() : HttpResponse.json(state);
    }));
    const view = renderApp('/app/workout/w-1'); const user = userEvent.setup();
    expect(await screen.findByLabelText('Peso usato (kg)')).toHaveValue('40');
    expect(screen.getByLabelText('Ripetizioni effettive')).toHaveValue('9');
    await user.click(screen.getByRole('button', { name: 'Salva risultati' }));
    await waitFor(() => expect(bodies).toHaveLength(2), { timeout: 2500 });
    expect(bodies[0]).toEqual(bodies[1]); expect(bodies[0]).toMatchObject({ id: 's-1' });
    view.unmount(); renderApp('/app/workout/w-1');
    expect(await screen.findByLabelText('Peso usato (kg)')).toHaveValue('40');
  });

  it('resyncs rejected stale results and never submits them for the next series', async () => {
    const state = setup(); let calls = 0;
    server.use(http.post('*/api/me/workouts/:id/sets/:setId/results', () => {
      calls++; state.resultEntrySetId = null; state.restEndsAt = null; state.nextAction = 'COMPLETE_SET';
      return problem(409, 'SET_RESULTS_WINDOW_CLOSED');
    }));
    renderApp('/app/workout/w-1'); const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Ripetizioni effettive'), '8');
    await user.click(screen.getByRole('button', { name: 'Salva risultati' }));
    await waitFor(() => expect(screen.queryByLabelText('Ripetizioni effettive')).not.toBeInTheDocument());
    expect(calls).toBe(1);
  });

  it('allows results for the final completed series during the final recovery', async () => {
    const state = recovery(); state.status = 'COMPLETED'; state.currentExerciseId = null; state.currentSetId = null;
    state.finalResultEndsAt = new Date(Date.now() + 60000).toISOString(); state.restEndsAt = null;
    setup(state); renderApp('/app/workout/w-1');
    expect(await screen.findByRole('timer', { name: 'Recupero finale' })).toBeInTheDocument();
    expect(screen.getByLabelText('Peso usato (kg)')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Fine serie' })).not.toBeInTheDocument();
  });

  it('shows saved results, explicit zero, missing values and unperformed sets separately in history', async () => {
    const done = workoutState({ status: 'INTERRUPTED', currentSetId: null, currentExerciseId: null });
    const sets = done.exercises[0]!.sets;
    sets[0] = { ...sets[0]!, completedAt: '2026-10-05T08:10:00Z', repsActual: 8, weightKgUsed: 32.75 };
    sets[1] = { ...sets[1]!, completedAt: '2026-10-05T08:12:00Z', repsActual: 0, weightKgUsed: 0 };
    const missing = done.exercises[1]!.sets[0]!;
    missing.completedAt = '2026-10-05T08:14:00Z';
    done.exercises[1]!.sets.push({ ...missing, id: 's-4', setIndex: 2, completedAt: null });
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(done)),
    );
    const view = renderApp('/app/history/w-1');
    const panca = await screen.findByRole('table', { name: 'Serie di Panca' });
    const row = within(panca).getAllByRole('row')[1]!;
    expect(within(row).getAllByRole('cell').map((cell) => cell.textContent)).toEqual(['10', '8', '32,75', '1:00', '✓ Completata']);
    expect(within(panca).getAllByText('0')).toHaveLength(2);
    const trazioni = screen.getByRole('table', { name: 'Serie di Trazioni' });
    expect(within(trazioni).getAllByText('Non registrato')).toHaveLength(1);
    expect(within(trazioni).getAllByText('Non registrate')).toHaveLength(1);
    expect(within(trazioni).getByText('— Non svolta')).toBeInTheDocument();
    view.unmount();
    renderApp('/app/history/w-1');
    expect(await screen.findByText('32,75')).toBeInTheDocument();
  });
});
