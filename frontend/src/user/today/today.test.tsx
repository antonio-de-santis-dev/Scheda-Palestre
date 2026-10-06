import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { server } from '../../test/server';
import { normalUser, renderApp } from '../../test/render';
import { planStructure } from '../../test/planFixtures';
import { workoutState } from '../../test/workoutFixtures';
import type { Today } from '../workout/api';
import { addDays } from '../../shared/utils/format';

const session = planStructure().sessions[0]!;

function today(overrides: Partial<Today> = {}): Today {
  return {
    date: '2026-10-05',
    status: 'TRAINING_DAY',
    assignmentId: 'as-1',
    planName: 'Scheda principianti',
    weekdays: [1, 3, 5],
    session,
    workout: null,
    pendingWorkout: null,
    nextTraining: null,
    canStart: true,
    activePlanCount: 1,
    plansWithoutDays: [],
    recommendedDurationEnded: [],
    ...overrides,
  };
}

describe('today page', () => {
  it('previews the planned session and starts the workout', async () => {
    let started: unknown = null;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () => HttpResponse.json(today())),
      http.post('*/api/me/workouts', async ({ request }) => {
        started = await request.json();
        return HttpResponse.json(workoutState(), { status: 201 });
      }),
      http.get('*/api/me/workouts/:id', () => HttpResponse.json(workoutState())),
    );
    const { router } = renderApp('/app');
    expect(await screen.findByRole('heading', { name: 'Giorno 1', level: 2 })).toBeInTheDocument();
    expect(screen.getByText(/3 × MAX/)).toBeInTheDocument();
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Inizia allenamento' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/app/workout/w-1'));
    expect(started).toHaveProperty('date');
  });

  it('shows a rest day with the next training', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(today({ status: 'REST_DAY', session: null, canStart: false, nextTraining: { date: '2026-10-07', sessionTitle: 'Giorno 2', planName: 'Scheda principianti' } })),
      ),
    );
    renderApp('/app/today');
    expect(await screen.findByRole('heading', { name: 'Giorno di riposo' })).toBeInTheDocument();
    expect(screen.getByText('Giorno 2')).toBeInTheDocument();
  });

  it('shows the end of the recommended duration without blocking the workout', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(
          today({ recommendedDurationEnded: [{ assignmentId: 'as-1', planName: 'Scheda principianti', expiresOn: '2026-10-04', ended: true }] }),
        ),
      ),
    );
    renderApp('/app/today');
    const notice = await screen.findByText('Durata consigliata terminata: “Scheda principianti”');
    expect(notice.closest('[role="status"]')).toHaveTextContent(
      'La durata consigliata è terminata il 4 ottobre 2026. Contatta la palestra per riceverne una nuova.',
    );
    expect(screen.getByRole('button', { name: 'Inizia allenamento' })).toBeEnabled();
  });

  it('asks to choose the days when none is set', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(
          today({
            status: 'NO_SCHEDULE',
            session: null,
            canStart: false,
            plansWithoutDays: [{ assignmentId: 'as-1', planName: 'Scheda principianti' }],
          }),
        ),
      ),
    );
    renderApp('/app/today');
    expect(await screen.findByRole('link', { name: 'Scegli i giorni' })).toHaveAttribute('href', '/app/schedule?assignment=as-1');
  });

  it('guides the user to the days of a new plan while showing the training of another plan', async () => {
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(today({ activePlanCount: 2, plansWithoutDays: [{ assignmentId: 'as-2', planName: 'Cardio' }] })),
      ),
    );
    renderApp('/app/today');
    expect(await screen.findByRole('heading', { name: 'Giorno 1', level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Scegli i giorni di Cardio' })).toHaveAttribute('href', '/app/schedule?assignment=as-2');
  });

  it('offers to resume or interrupt a workout left open on another day', async () => {
    let interrupted = false;
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/today', () =>
        HttpResponse.json(
          today({
            canStart: false,
            pendingWorkout: interrupted
              ? null
              : {
                  id: 'w-old',
                  scheduledDate: '2026-10-04',
                  status: 'IN_PROGRESS',
                  planName: 'Scheda principianti',
                  sessionTitle: 'Giorno 2',
                  startedAt: '2026-10-04T18:00:00Z',
                  finishedAt: null, durationSeconds: null,
                  volume: { recordedKgReps: null, completedSets: 0, recordedSets: 0, missingWeightSets: 0, missingRepsSets: 0 },
                  totalExercises: 3,
                  completedExercises: 1,
                  skippedExercises: 0,
                },
          }),
        ),
      ),
      http.post('*/api/me/workouts/:id/interrupt', () => {
        interrupted = true;
        return HttpResponse.json(workoutState({ workoutId: 'w-old', status: 'INTERRUPTED', nextAction: 'FINISHED' }));
      }),
    );
    renderApp('/app/today');
    expect(await screen.findByText('Allenamento non concluso')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Riprendi' })).toHaveAttribute('href', '/app/workout/w-old');
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: 'Interrompi' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Interrompi' }));
    await waitFor(() => expect(screen.queryByText('Allenamento non concluso')).not.toBeInTheDocument());
  });
});

describe('calendar page', () => {
  it('selects completed, rest and planned days, and requests complete weeks when changing month', async () => {
    const ranges: { from: string; to: string }[] = [];
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/calendar', ({ request }) => {
        const url = new URL(request.url);
        const from = url.searchParams.get('from')!;
        ranges.push({ from, to: url.searchParams.get('to')! });
        return HttpResponse.json([
          {
            date: from,
            type: 'TRAINING',
            sessionTitle: 'Giorno 1',
            workout: {
              id: 'w-1',
              scheduledDate: from,
              status: 'COMPLETED',
              planName: 'P',
              sessionTitle: 'Giorno 1',
              startedAt: '',
              finishedAt: '',
              totalExercises: 2,
              completedExercises: 2,
              skippedExercises: 0,
            },
          },
          { date: addDays(from, 1), type: 'REST', sessionTitle: null, workout: null },
          { date: addDays(from, 2), type: 'TRAINING', sessionTitle: 'Giorno 2', workout: null },
        ]);
      }),
    );
    renderApp('/app/calendar');
    const list = await screen.findByRole('list', { name: 'Giorni' });
    const user = userEvent.setup();
    await user.click(within(list).getByRole('button', { name: /completato/ }));
    expect(screen.getByText('Completato')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^Apri allenamento del/ })).toHaveAttribute('href', '/app/history/w-1');
    await user.click(within(list).getAllByRole('button', { name: /riposo/ })[0]!);
    expect(screen.getByText(/Giorno di riposo/)).toBeInTheDocument();
    await user.click(within(list).getByRole('button', { name: /Giorno 2/ }));
    expect(screen.getByRole('heading', { name: 'Giorno 2' })).toBeInTheDocument();
    expect(within(list).getAllByRole('button').length).toBeLessThanOrEqual(42);
    await user.click(screen.getByRole('button', { name: 'Mese successivo' }));
    await waitFor(() => expect(ranges).toHaveLength(2));
    expect(ranges[0]!.from).not.toBe(ranges[1]!.from);
    for (const range of ranges) {
      expect(new Date(range.from + 'T12:00:00').getDay()).toBe(1);
      expect(new Date(range.to + 'T12:00:00').getDay()).toBe(0);
    }
  });
});

describe('calendar session identity', () => {
  it('shows the selected session even when another session has the same title', async () => {
    const date = new Date();
    const iso = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    const base = planStructure();
    server.use(
      http.get('*/api/auth/me', () => HttpResponse.json(normalUser)),
      http.get('*/api/me/calendar', () => HttpResponse.json([
        { date: iso, type: 'TRAINING', sessionTitle: 'Duplicato', sessionId: 's-1', assignmentId: 'as-1', planName: 'P', workout: null },
      ])),
      http.get('*/api/me/assignments/as-1/plan', () => HttpResponse.json({ ...base, sessions: base.sessions.map((s) => ({ ...s, title: 'Duplicato' })) })),
    );
    renderApp('/app/calendar');
    expect(await screen.findByText('Panca piana')).toBeInTheDocument();
  });
});
