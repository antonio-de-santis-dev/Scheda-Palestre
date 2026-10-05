import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

vi.mock('canvas-confetti', () => ({ default: vi.fn(() => Promise.resolve()) }));

describe('actual set results', () => {
  it('records comma decimals and actual repetitions, then clears the fields for the next set', async () => {
    let body: unknown;
    let setId: unknown;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', async ({ request, params }) => {
        body = await request.json(); setId = params.setId;
        return HttpResponse.json(workoutState({ currentSetId: 's-2' }));
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Peso usato (kg)'), '32,75');
    await user.type(screen.getByLabelText('Ripetizioni effettive'), '8');
    await user.click(screen.getByRole('button', { name: 'Fine serie' }));
    await waitFor(() => expect(body).toEqual({ weightKgUsed: 32.75, repsActual: 8 }));
    expect(setId).toBe('s-1');
    await waitFor(() => expect(screen.getByLabelText('Peso usato (kg)')).toHaveValue(''));
    expect(screen.getByLabelText('Ripetizioni effettive')).toHaveValue('');
  });

  it.each([
    { weight: '', reps: '', expected: { weightKgUsed: null, repsActual: null } },
    { weight: '0', reps: '0', expected: { weightKgUsed: 0, repsActual: 0 } },
    { weight: '', reps: '7', expected: { weightKgUsed: null, repsActual: 7 } },
  ])('preserves missing values and explicit zero ($weight/$reps)', async ({ weight, reps, expected }) => {
    let body: unknown;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState({ currentExerciseId: 'e-2', currentSetId: 's-3' }))),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', async ({ request }) => {
        body = await request.json(); return HttpResponse.json(workoutState());
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await screen.findByLabelText('Peso usato (kg)');
    if (weight) await user.type(screen.getByLabelText('Peso usato (kg)'), weight);
    if (reps) await user.type(screen.getByLabelText('Ripetizioni effettive'), reps);
    await user.click(screen.getByRole('button', { name: 'Fine serie' }));
    await waitFor(() => expect(body).toEqual(expected));
  });

  it('rejects invalid results before sending a completion', async () => {
    let calls = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', () => { calls++; return HttpResponse.json(workoutState()); }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    const weight = await screen.findByLabelText('Peso usato (kg)');
    const reps = screen.getByLabelText('Ripetizioni effettive');
    for (const [kg, count] of [['-1', '7.5'], ['1.234', '-1'], ['1001', '1001']]) {
      await user.clear(weight); await user.type(weight, kg!);
      await user.clear(reps); await user.type(reps, count!);
      await user.click(screen.getByRole('button', { name: 'Fine serie' }));
      expect(weight).toHaveAttribute('aria-invalid', 'true');
      expect(reps).toHaveAttribute('aria-invalid', 'true');
    }
    expect(calls).toBe(0);
  });

  it('retries the same captured results after a transport failure', async () => {
    const bodies: unknown[] = [];
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', async ({ request }) => {
        bodies.push(await request.json());
        return bodies.length === 1 ? HttpResponse.error() : HttpResponse.json(workoutState({ currentSetId: 's-2' }));
      }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Peso usato (kg)'), '40');
    await user.type(screen.getByLabelText('Ripetizioni effettive'), '9');
    await user.click(screen.getByRole('button', { name: 'Fine serie' }));
    expect(screen.getByLabelText('Peso usato (kg)')).toBeDisabled();
    await waitFor(() => expect(bodies).toHaveLength(2), { timeout: 2500 });
    expect(bodies).toEqual([{ weightKgUsed: 40, repsActual: 9 }, { weightKgUsed: 40, repsActual: 9 }]);
  });

  it('resynchronizes a conflicting completed set without attempting to overwrite it again', async () => {
    let calls = 0, fetches = 0;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState({ currentSetId: ++fetches === 1 ? 's-1' : 's-2' }))),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', () => { calls++; return problem(409, 'SET_RESULTS_CHANGED'); }),
    );
    renderApp('/app/workout/w-1');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Ripetizioni effettive'), '8');
    await user.click(screen.getByRole('button', { name: 'Fine serie' }));
    await waitFor(() => expect(screen.getByLabelText('Ripetizioni effettive')).toHaveValue(''));
    expect(fetches).toBeGreaterThan(1);
    expect(calls).toBe(1);
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
