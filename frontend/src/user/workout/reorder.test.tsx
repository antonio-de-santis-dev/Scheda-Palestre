import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { normalUser, renderApp } from '../../test/render';
import { problem, server } from '../../test/server';
import { workoutState } from '../../test/workoutFixtures';

vi.mock('./feedback', async (original) => ({ ...await original<typeof import('./feedback')>(), celebrate: vi.fn() }));

const liftTrazioni = async () => {
  const user = userEvent.setup();
  const grip = await screen.findByRole('button', { name: 'Trascina Trazioni' });
  grip.focus();
  await user.keyboard(' {ArrowUp}{Enter}');
};
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

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
    await liftTrazioni();
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
    await liftTrazioni();
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
    await liftTrazioni();
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Salta esercizio' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Interrompi' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Trascina Trazioni' }));
    expect(requests).toBe(1);
    resolve();
    await screen.findByRole('heading', { name: 'Allenamento interrotto' });
    expect(screen.queryByRole('button', { name: /Trascina/ })).not.toBeInTheDocument();
  });
  it('moves completed exercises to the bottom immediately and after refresh, without handles', async () => {
    let state = workoutState();
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.post('*/api/me/workouts/:id/sets/:setId/complete', () => {
        state = { ...state, executionVersion: 1, currentExerciseId: 'e-2', currentSetId: 's-3',
          exercises: [{ ...state.exercises[0]!, status: 'COMPLETED', setsCompleted: 2,
            sets: state.exercises[0]!.sets.map((set) => ({ ...set, completedAt: state.serverTime })) },
            { ...state.exercises[1]!, status: 'IN_PROGRESS' }] };
        return HttpResponse.json(state);
      }),
    );
    const view = renderApp('/app/workout/w-1');
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Fine serie' }));
    await screen.findByRole('region', { name: 'Trazioni' });
    const rows = within(screen.getByRole('list')).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('Trazioni');
    expect(rows[1]).toHaveTextContent('Panca');
    expect(rows[1]).toHaveTextContent('Completato');
    expect(screen.queryByRole('button', { name: 'Trascina Panca' })).not.toBeInTheDocument();
    view.unmount();
    renderApp('/app/workout/w-1');
    await screen.findByRole('region', { name: 'Trazioni' });
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[1]).toHaveTextContent('Panca');
  });

  it.each(['mouse', 'touch'])('previews %s dragging and saves only once on release; terminal rows stay locked', async (pointerType) => {
    const state = workoutState();
    state.exercises.push({ ...state.exercises[0]!, id: 'done', position: 3, status: 'COMPLETED', exerciseName: 'Squat', setsCompleted: 2 });
    let requests = 0;
    let body: unknown;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(state)),
      http.post('*/api/me/workouts/:id/exercises/reorder', async ({ request }) => {
        requests++; body = await request.json();
        return HttpResponse.json({ ...state, executionVersion: 1 });
      }),
    );
    // JSDOM lacks pointer capture and hit testing; emulate these browser primitives.
    vi.stubGlobal('PointerEvent', class extends MouseEvent {
      pointerId = 1;
      isPrimary = true;
      pointerType: string;
      constructor(type: string, init: PointerEventInit) {
        super(type, init);
        this.pointerType = init.pointerType ?? 'mouse';
      }
    });
    const capture = vi.fn();
    renderApp('/app/workout/w-1');
    const grip = await screen.findByRole('button', { name: 'Trascina Trazioni' });
    grip.setPointerCapture = capture;
    const rows = within(screen.getByRole('list')).getAllByRole('listitem');
    Object.defineProperty(document, 'elementFromPoint', { configurable: true, value: vi.fn(() => rows[0]) });
    fireEvent.pointerDown(grip, { button: 0, pointerType, clientX: 10, clientY: 200 });
    expect(capture).toHaveBeenCalledOnce();
    fireEvent.pointerMove(grip, { pointerType, clientX: 10, clientY: 160 });
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[0]).toHaveTextContent('Trazioni');
    expect(requests).toBe(0);
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeDisabled();
    Object.defineProperty(document, 'elementFromPoint', { configurable: true, value: vi.fn(() => rows[2]) });
    fireEvent.pointerMove(grip, { pointerType, clientX: 10, clientY: 300 });
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[2]).toHaveTextContent('Squat');
    expect(screen.queryByRole('button', { name: 'Trascina Squat' })).not.toBeInTheDocument();
    fireEvent.pointerCancel(grip, { pointerType });
    expect(requests).toBe(0);
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[0]).toHaveTextContent('Panca');
    Object.defineProperty(document, 'elementFromPoint', { configurable: true, value: vi.fn(() => rows[0]) });
    fireEvent.pointerDown(grip, { button: 0, pointerType, clientX: 10, clientY: 200 });
    fireEvent.pointerMove(grip, { pointerType, clientX: 10, clientY: 160 });
    fireEvent.pointerUp(grip, { pointerType });
    fireEvent.pointerUp(grip, { pointerType });
    await waitFor(() => expect(requests).toBe(1));
    expect(body).toEqual({ exerciseIds: ['e-2', 'e-1'], expectedVersion: 0 });
    vi.unstubAllGlobals();
  });

  it('cancels a keyboard preview without saving or changing the current exercise', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
    );
    renderApp('/app/workout/w-1');
    const grip = await screen.findByRole('button', { name: 'Trascina Trazioni' });
    grip.focus();
    await userEvent.setup().keyboard(' {ArrowUp}{Escape}');
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[0]).toHaveTextContent('Panca');
    expect(screen.getByRole('region', { name: 'Panca' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Fine serie' })).not.toBeDisabled();
  });

  it('discards a picked-up order when newer server state arrives', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
    );
    const { client } = renderApp('/app/workout/w-1');
    const grip = await screen.findByRole('button', { name: 'Trascina Trazioni' });
    grip.focus();
    await userEvent.setup().keyboard(' {ArrowUp}');
    expect(screen.getByRole('button', { name: 'Fine serie' })).toBeDisabled();
    client.setQueryData(['me', 'workout', 'w-1'], { ...workoutState({ executionVersion: 1 }), receivedAt: Date.now() });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Fine serie' })).not.toBeDisabled());
    expect(within(screen.getByRole('list')).getAllByRole('listitem')[0]).toHaveTextContent('Panca');
  });

});
